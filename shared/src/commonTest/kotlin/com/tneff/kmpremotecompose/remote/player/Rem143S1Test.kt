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
import com.tneff.kmpremotecompose.remote.core.operations.draw.ParticlesCreate
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-143 S1 — the particle seed-frame: ParticlesCreate seeds the per-particle state and the paint-walk
 * runs the ParticlesLoop body **once per particle** (matrix-positioned). Pre-S1 the body never drew
 * (PARTICLE_LOOP was Operation-only) → blank; now N particles render at distinct positions.
 *
 * The body draw-count == particleCount is **deterministic** (independent of the RAND-seeded positions);
 * the distinct-position spread proves the per-particle var-load reaches the body's matrix transform. Exact
 * positions need the RNG seed-pin (docs carry no RAND_SEED) → that is test-3's data-oracle gate, not here.
 */
class Rem143S1Test {
    private class Rec(c: RemoteContext) : NoOpPaintContext(c) {
        var tx = 0f; var ty = 0f
        private val st = ArrayDeque<Pair<Float, Float>>()
        var bitmaps = 0; var texts = 0
        val positions = ArrayList<Pair<Float, Float>>()
        override fun matrixSave() { st.addLast(tx to ty) }
        override fun matrixRestore() { st.removeLastOrNull()?.let { tx = it.first; ty = it.second } }
        override fun translate(translateX: Float, translateY: Float) { tx += translateX; ty += translateY }
        override fun matrixTranslate(translateX: Float, translateY: Float) { tx += translateX; ty += translateY }
        override fun drawBitmap(id: Int, left: Float, top: Float, right: Float, bottom: Float) { bitmaps++; positions += (left + tx) to (top + ty) }
        override fun drawTextRun(textId: Int, start: Int, end: Int, contextStart: Int, contextEnd: Int, x: Float, y: Float, rtl: Boolean) { texts++; positions += (x + tx) to (y + ty) }
        override fun getTextBounds(textId: Int, start: Int, end: Int, flags: Int, bounds: FloatArray) { val t = context.getText(textId) ?: ""; bounds[0] = 0f; bounds[1] = -11f; bounds[2] = t.length * 9f; bounds[3] = 3f }
    }

    private fun play(doc: String): Rec {
        Builtins.register()
        val ctx = RemoteContext(); val rec = Rec(ctx)
        RemoteComposePlayer(ctx).paint(DocumentReader.inflate(RcCorpus.readFixture("corpus/$doc.rc")), rec)
        return rec
    }

    @Test
    fun confetti_drawsBitmapPerParticle_atDistinctPositions() {
        val rec = play("impulse_demo_confetti_demo")
        assertEquals(100, rec.bitmaps, "100-particle confetti must draw one bitmap sprite per particle (was 0 pre-S1)")
        // Matrix-positioned per particle → positions spread (RAND seed → not exact, but distinct).
        val distinct = rec.positions.map { (kotlin.math.round(it.first) to kotlin.math.round(it.second)) }.toSet().size
        assertTrue(distinct > 50, "particles must spread to distinct positions (matrix per-particle), got $distinct")
    }

    @Test
    fun hearts_drawsGlyphPerParticle() {
        val rec = play("impulse_demo_hearts_demo")
        assertTrue(rec.texts >= 50, "50-particle hearts must draw a glyph per particle, got ${rec.texts} (was 0 pre-S1)")
    }

    @Test
    fun byteFormat_unchanged_forParticlesCreate() {
        // §2: seed-apply is additive; ParticlesCreate round-trips byte-identical.
        val op = ParticlesCreate(54, 3, intArrayOf(48, 49), arrayOf(floatArrayOf(1f, 2f), floatArrayOf(3f)))
        val bytes = WireBuffer().also { op.write(it) }.toByteArray()
        val buf = WireBuffer.fromBytes(bytes); assertEquals(op.opcode, buf.readByte())
        val decoded = ArrayList<Operation>(); ParticlesCreate.read(buf, decoded)
        assertEquals(op, decoded[0], "decoded equals original")
        assertContentEquals(bytes, WireBuffer().also { decoded[0].write(it) }.toByteArray(), "re-encode byte-identical")
    }
}
