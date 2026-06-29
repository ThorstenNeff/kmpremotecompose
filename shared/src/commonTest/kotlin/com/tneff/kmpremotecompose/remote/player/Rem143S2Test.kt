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
import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeDocument
import com.tneff.kmpremotecompose.remote.core.operations.Builtins
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-143 S2 self-check (necessary-not-sufficient — the FORMAL gate is dev-1's independent multi-frame
 * data-oracle + draw-capture + test-3 Voll-173, per §5b). Validates the evolution MECHANISM end-to-end:
 * the impulse seeds per-frame Δt (ID_ANIMATION_DELTA_TIME) from its op-field, so decode-once → paint-N on
 * the **same inflated doc** evolves the particle positions frame-to-frame (live); the static seed-frame and
 * the live first-frame (Δt=0) do NOT evolve (S1 golden preserved). Black-box draw-capture, no state readback.
 *
 * Critically — fresh ctx + fresh player per frame (mirrors RemoteComposeApp/the harness): Δt must come from
 * the persistent OP-field, not a per-frame-reset context/player field (dev-1's catch).
 */
class Rem143S2Test {
    private class Rec(c: RemoteContext) : NoOpPaintContext(c) {
        var tx = 0f; var ty = 0f
        private val st = ArrayDeque<Pair<Float, Float>>()
        var first: Pair<Float, Float>? = null
        override fun matrixSave() { st.addLast(tx to ty) }
        override fun matrixRestore() { st.removeLastOrNull()?.let { tx = it.first; ty = it.second } }
        override fun translate(translateX: Float, translateY: Float) { tx += translateX; ty += translateY }
        override fun matrixTranslate(translateX: Float, translateY: Float) { tx += translateX; ty += translateY }
        override fun drawBitmap(id: Int, left: Float, top: Float, right: Float, bottom: Float) {
            if (first == null) first = (left + tx) to (top + ty) // first particle each frame
        }
    }

    /** Paint each frame with a FRESH ctx + player (live-app/harness reality) — the SAME inflated doc carries
     *  the op-field state across frames. Returns the first particle's drawn position per frame. */
    private fun firstParticlePerFrame(doc: RemoteComposeDocument, frames: List<Float>, animate: Boolean): List<Pair<Float, Float>> {
        Builtins.register()
        return frames.map { t ->
            val ctx = RemoteContext(); ctx.animationEnabled = animate
            val rec = Rec(ctx)
            RemoteComposePlayer(ctx).paint(doc, rec, frameTimeSeconds = t, staticTimeSeconds = t)
            rec.first!!
        }
    }

    @Test
    fun live_particlesEvolveAcrossFrames_viaImpulseDeltaTime() {
        val doc = DocumentReader.inflate(RcCorpus.readFixture("corpus/impulse_demo_confetti_demo.rc"))
        val pos = firstParticlePerFrame(doc, listOf(0f, 0.1f, 0.2f, 0.3f), animate = true)
        val distinct = pos.map { (kotlin.math.round(it.first) to kotlin.math.round(it.second)) }.toSet().size
        assertTrue(distinct >= 2, "live: particle must evolve frame-to-frame via Δt (gravity/velocity), got $pos")
        // Frame 0 = seed (Δt=0, lastFrameTime NaN), frame 1 = first real Δt → must differ.
        assertTrue(pos[0] != pos[1], "frame 0 (seed, Δt=0) must differ from frame 1 (evolved), got ${pos[0]} vs ${pos[1]}")
    }

    @Test
    fun static_seedFrameStableAcrossPaints() {
        // animationEnabled=false → Δt forced 0 + evolution gated off → identical seed frame across paints (S1).
        val doc = DocumentReader.inflate(RcCorpus.readFixture("corpus/impulse_demo_confetti_demo.rc"))
        val pos = firstParticlePerFrame(doc, listOf(0f, 0.1f, 0.2f), animate = false)
        assertEquals(1, pos.toSet().size, "static: seed frame must be stable across paints (no evolution), got $pos")
    }

    /** REM-143 S2b — maze PARTICLE_COMPARE (per-particle conditional branch): the ball evolves live, and the
     *  compare children draw conditionally (fire only in the process frames, never the seed). */
    private class MazeRec(c: RemoteContext) : NoOpPaintContext(c) {
        var tx = 0f; var ty = 0f
        private val st = ArrayDeque<Pair<Float, Float>>()
        var circles = 0; var firstCircle: Pair<Float, Float>? = null
        override fun matrixSave() { st.addLast(tx to ty) }
        override fun matrixRestore() { st.removeLastOrNull()?.let { tx = it.first; ty = it.second } }
        override fun translate(translateX: Float, translateY: Float) { tx += translateX; ty += translateY }
        override fun matrixTranslate(translateX: Float, translateY: Float) { tx += translateX; ty += translateY }
        override fun drawCircle(centerX: Float, centerY: Float, radius: Float) {
            circles++; if (firstCircle == null) firstCircle = (centerX + tx) to (centerY + ty)
        }
        override fun getTextBounds(textId: Int, start: Int, end: Int, flags: Int, bounds: FloatArray) { bounds[0] = 0f; bounds[1] = -11f; bounds[2] = 9f; bounds[3] = 3f }
    }

    @Test
    fun maze_ballEvolvesLive_andCompareIsProcessOnly() {
        Builtins.register()
        val doc = DocumentReader.inflate(RcCorpus.readFixture("corpus/maze.rc"))
        val ballPos = ArrayList<Pair<Float, Float>>()
        for (t in listOf(0f, 0.1f, 0.2f, 0.3f)) { // decode-once → paint-N (fresh ctx+player, op-field carries)
            val ctx = RemoteContext(); ctx.animationEnabled = true
            val rec = MazeRec(ctx)
            RemoteComposePlayer(ctx).paint(doc, rec, frameTimeSeconds = t, staticTimeSeconds = t)
            ballPos += rec.firstCircle!!
        }
        val distinct = ballPos.map { (kotlin.math.round(it.first) to kotlin.math.round(it.second)) }.toSet().size
        assertTrue(distinct >= 2, "maze ball must evolve live (Δt-driven), got $ballPos")

        // The compare equations1 mutate state only on process frames (Δt>0); the static seed must be unchanged
        // across paints (no premature compare firing) — guards the S1 seed-convergence the oracle checks.
        val staticDoc = DocumentReader.inflate(RcCorpus.readFixture("corpus/maze.rc"))
        val staticCircles = (0..1).map {
            val ctx = RemoteContext(); ctx.animationEnabled = false
            val rec = MazeRec(ctx)
            RemoteComposePlayer(ctx).paint(staticDoc, rec, frameTimeSeconds = 0f, staticTimeSeconds = 0f)
            rec.circles
        }
        assertEquals(staticCircles[0], staticCircles[1], "static maze must be stable across paints (compare process-only)")
    }
}
