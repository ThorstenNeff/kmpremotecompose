/*
 * Copyright 2026 The KmpRemoteCompose Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tneff.kmpremotecompose.remote.player

import com.tneff.kmpremotecompose.conformance.RcCorpus
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.Builtins
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawBitmap
import com.tneff.kmpremotecompose.remote.player.core.PaintContext
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-132 — `DrawBitmap` (op 44) render-apply. Upstream is `PaintOperation implements VariableSupport`:
 * [DrawBitmap.updateVariables] resolves NaN dst-rect refs (Phase-A), [DrawBitmap.paint] blits the whole
 * bitmap into the resolved rect via the 5-arg `drawBitmap`. These tests cover the assist gate-bar:
 * (1) byte format untouched, (2) NaN-resolve correctness incl. the `!isOperationVariable` guard +
 * only-floats-resolved, (3) the end-to-end sprite-at-rect data-oracle.
 *
 * **Scope (dispatch≠visual):** the oracle asserts the *sprite is drawn at its resolved dst-rect* — NOT
 * that `impulse_demo_confetti_demo` matches the live animated confetti. The particle field lives in the
 * Impulse/Particles subsystem (still `Operation`-only = separately deferred), out of REM-132 scope.
 */
class Rem132DrawBitmapRenderTest {

    /** Records every 5-arg `drawBitmap` blit (the only primitive REM-132 emits). */
    private class RecordingBitmapContext(context: RemoteContext) : NoOpPaintContext(context) {
        data class Blit(val id: Int, val l: Float, val t: Float, val r: Float, val b: Float)
        val blits = ArrayList<Blit>()
        override fun drawBitmap(id: Int, left: Float, top: Float, right: Float, bottom: Float) {
            blits += Blit(id, left, top, right, bottom)
        }
    }

    @Test
    fun paint_blitsWholeBitmapIntoLiteralRect() {
        val ctx = RemoteContext()
        val rec = RecordingBitmapContext(ctx)
        val op = DrawBitmap(id = 7, left = 1f, top = 2f, right = 51f, bottom = 52f, descriptionId = 9)
        op.updateVariables(ctx) // literals → resolved == raw
        op.paint(ctx, rec)
        assertEquals(listOf(RecordingBitmapContext.Blit(7, 1f, 2f, 51f, 52f)), rec.blits)
    }

    @Test
    fun updateVariables_resolvesNanDataRefsForDstRect() {
        val ctx = RemoteContext()
        // Data-variable NaN refs for the dst rect, seeded in the store.
        val lRef = WireTypes.asNan(100); val tRef = WireTypes.asNan(101)
        val rRef = WireTypes.asNan(102); val bRef = WireTypes.asNan(103)
        ctx.loadFloat(100, 10f); ctx.loadFloat(101, 20f); ctx.loadFloat(102, 110f); ctx.loadFloat(103, 120f)
        val rec = RecordingBitmapContext(ctx)
        val op = DrawBitmap(5, lRef, tRef, rRef, bRef, 0)
        op.updateVariables(ctx)
        op.paint(ctx, rec)
        assertEquals(listOf(RecordingBitmapContext.Blit(5, 10f, 20f, 110f, 120f)), rec.blits)
    }

    @Test
    fun updateVariables_doesNotResolveOperatorNan() {
        // An RPN-operator NaN (region 3) is NOT a variable ref → must pass through verbatim (the
        // coord-resolution-class trap: resolving it would index the store with an operator id). Guarded
        // by resolveCoord's `!isOperationVariable`. We assert the raw bits survive to the blit.
        val ctx = RemoteContext()
        val opNan = WireTypes.asNan(0x310000 + 1) // ADD operator id — region 3
        assertTrue(WireTypes.isOperationVariable(opNan), "precondition: operator-region NaN")
        val rec = RecordingBitmapContext(ctx)
        val op = DrawBitmap(3, opNan, 0f, 50f, 50f, 0)
        op.updateVariables(ctx)
        op.paint(ctx, rec)
        assertEquals(1, rec.blits.size)
        assertEquals(opNan.toRawBits(), rec.blits[0].l.toRawBits(), "operator NaN must pass through unresolved")
    }

    @Test
    fun byteFormat_unchanged_roundTripsRawBits() {
        // §2 by-construction: render-apply is additive; write/read keep the raw rect bits (incl. a NaN ref).
        val op = DrawBitmap(54, WireTypes.asNan(200), 0f, 50f, 50f, 55)
        val bytes = WireBuffer().also { op.write(it) }.toByteArray()
        val buffer = WireBuffer.fromBytes(bytes)
        assertEquals(op.opcode, buffer.readByte(), "opcode byte")
        val decoded = ArrayList<Operation>()
        DrawBitmap.read(buffer, decoded)
        assertEquals(1, decoded.size)
        val d = decoded[0] as DrawBitmap
        assertEquals(op.left.toRawBits(), d.left.toRawBits(), "NaN-ref rect bits must survive read")
        assertEquals(op, d, "decoded equals original (incl. raw bits)")
        assertContentEquals(bytes, WireBuffer().also { d.write(it) }.toByteArray(), "re-encode byte-identical")
    }

    @Test
    fun confettiDoc_dispatchesSpriteAtResolvedRect() {
        // End-to-end data-oracle (dispatch≠visual): the one DrawBitmap in the corpus draws its 50x50
        // sprite at its resolved dst-rect [0,0,50,50]. Pre-REM-132 this op had no paint → BLANK. We
        // assert sprite-AT-RECT, NOT the live animated confetti (Particles subsystem, deferred).
        Builtins.register()
        val ctx = RemoteContext()
        val rec = RecordingBitmapContext(ctx)
        val doc = DocumentReader.inflate(RcCorpus.readFixture("corpus/impulse_demo_confetti_demo.rc"))
        RemoteComposePlayer(ctx).paint(doc, rec)
        val bmp = rec.blits.filter { it.id == 54 }
        assertEquals(1, bmp.size, "the DrawBitmap#54 sprite must be dispatched exactly once")
        assertEquals(RecordingBitmapContext.Blit(54, 0f, 0f, 50f, 50f), bmp[0], "sprite at its resolved dst-rect")
    }
}
