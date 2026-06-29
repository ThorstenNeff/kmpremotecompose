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
import com.tneff.kmpremotecompose.remote.core.operations.DataDynamicListFloat
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.layout.LayoutCompute
import com.tneff.kmpremotecompose.remote.core.operations.UpdateDynamicFloatList
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * REM-139 Slice 2 — LayoutCompute: a layout modifier that computes a component's measure (type=0) or
 * position (type=1) from a seeded `[x,y,w,h,parentW,parentH]` float list its child Updates overwrite.
 * Pre-S2 the compute docs rendered the box at its default measure (list never read/applied).
 */
class Rem139Slice2Test {
    private data class Rect(val l: Float, val t: Float, val r: Float, val b: Float)
    private class Rec(c: RemoteContext) : NoOpPaintContext(c) {
        val rects = ArrayList<Rect>()
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float) { rects += Rect(left, top, right, bottom) }
        override fun drawRoundRect(left: Float, top: Float, right: Float, bottom: Float, radiusX: Float, radiusY: Float) { rects += Rect(left, top, right, bottom) }
    }
    private fun play(doc: String): Pair<RemoteContext, Rec> {
        Builtins.register()
        val ctx = RemoteContext(); val rec = Rec(ctx)
        RemoteComposePlayer(ctx).paint(DocumentReader.inflate(RcCorpus.readFixture("corpus/$doc.rc")), rec)
        return ctx to rec
    }

    @Test
    fun measureType_computesComponentHeight() {
        // c_modifier_compute_measure: Box WIDTH=100 (exact), HEIGHT computed via the bounds list (slot 3).
        // The box background draws at the computed bounds → a 100-wide, computed-tall rect (NOT fill-500).
        val (_, rec) = play("c_modifier_compute_measure")
        val box = rec.rects.firstOrNull { (it.r - it.l) in 99f..101f }
        assertNotNull(box, "the WIDTH=100 box background must draw; rects=${rec.rects}")
        val h = box.b - box.t
        assertTrue(h > 0f && h < 500f, "height must be COMPUTED (not 0 / not fill-500), got $h")
    }

    @Test
    fun positionType_computesComponentXY() {
        // c_modifier_compute_position: the box's x/y are computed (centered ~200,200 in the 500 doc).
        val (_, rec) = play("c_modifier_compute_position")
        val box = rec.rects.firstOrNull { (it.r - it.l) in 1f..400f && it.l > 0f }
        assertNotNull(box, "the positioned box must draw at a computed non-zero origin; rects=${rec.rects}")
        assertTrue(box.l > 0f && box.t > 0f, "x/y must be COMPUTED to a non-zero origin, got (${box.l},${box.t})")
    }

    @Test
    fun reconciliation_measureComputedListSurvivesPhaseA() {
        // PO-mandated: measure() seeds+computes the LayoutCompute child list BEFORE Phase-A; DynamicFloatList's
        // allocate-if-absent apply must NOT re-zero it → the computed value is still in the store after paint.
        val (ctx, _) = play("c_modifier_compute_measure")
        val list = ctx.getFloatArray(2097194)
        assertNotNull(list, "bounds list must exist post-paint")
        assertEquals(6, list.size)
        assertTrue(list[3] > 0f, "computed height (slot 3) must survive the Phase-A walk (not re-zeroed), got ${list[3]}")
    }

    @Test
    fun byteFormat_unchanged() {
        // §2: LayoutCompute + DynamicFloatList + Update round-trip byte-identical (apply additive, wire untouched).
        fun rt(op: Operation, reader: com.tneff.kmpremotecompose.remote.core.operations.OperationReader) {
            val bytes = WireBuffer().also { op.write(it) }.toByteArray()
            val buf = WireBuffer.fromBytes(bytes); assertEquals(op.opcode, buf.readByte())
            val decoded = ArrayList<Operation>(); reader.read(buf, decoded)
            assertEquals(op, decoded[0])
            assertContentEquals(bytes, WireBuffer().also { decoded[0].write(it) }.toByteArray())
        }
        rt(LayoutCompute(0, 2097194, false), LayoutCompute)
        rt(DataDynamicListFloat(2097194, 6f), DataDynamicListFloat)
        rt(UpdateDynamicFloatList(2097194, 3f, Float.fromBits(0x7FC00001)), UpdateDynamicFloatList)
    }
}
