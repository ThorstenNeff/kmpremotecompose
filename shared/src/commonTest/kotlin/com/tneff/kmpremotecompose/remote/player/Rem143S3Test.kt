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

import com.tneff.kmpremotecompose.conformance.IgnoreOnWasm
import com.tneff.kmpremotecompose.conformance.RcCorpus
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeDocument
import com.tneff.kmpremotecompose.remote.core.operations.Builtins
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.TouchState
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * REM-143 S3a self-check (necessary-not-sufficient — the FORMAL gate is a Maestro live touch flow per §0
 * target + dev-1's §5b re-trigger data-oracle). Validates the Touch→Impulse MECHANISM end-to-end on
 * confetti (startAt=id29): the player seeds id29 (ID_TOUCH_EVENT_TIME) from the persistent [TouchState]
 * each live frame, so a tap re-enters the impulse window; and on window-elapse the impulse re-arms the
 * ParticlesCreate seed (upstream mInitialPass=true) so a re-trigger re-bursts from the seed — while a tap
 * *during* the active window only moves startAt and never re-seeds (B2-ratified: Tap ≠ Re-Seed).
 *
 * confetti's init uses OP_RAND (non-deterministic in jvmTest — no RAND_SEED), so this asserts RAND-immune
 * observables (re-activation, re-burst ≠ frozen-evolved, trajectory-continuity), NOT exact positions.
 *
 * Fresh ctx + fresh player per frame (live-app reality); the SAME inflated doc + the SAME [TouchState]
 * carry the cross-frame op-field state (impulse lastFrameTime, particle seed, touchEventTime).
 */
@IgnoreOnWasm
class Rem143S3Test {
    private class Rec(c: RemoteContext) : NoOpPaintContext(c) {
        var tx = 0f; var ty = 0f
        private val st = ArrayDeque<Pair<Float, Float>>()
        var count = 0
        var first: Pair<Float, Float>? = null
        override fun matrixSave() { st.addLast(tx to ty) }
        override fun matrixRestore() { st.removeLastOrNull()?.let { tx = it.first; ty = it.second } }
        override fun translate(translateX: Float, translateY: Float) { tx += translateX; ty += translateY }
        override fun matrixTranslate(translateX: Float, translateY: Float) { tx += translateX; ty += translateY }
        override fun drawBitmap(id: Int, left: Float, top: Float, right: Float, bottom: Float) {
            count++; if (first == null) first = (left + tx) to (top + ty)
        }
    }

    /** Paint one live frame on [doc] with the shared [touch]; optionally inject a fresh press first. */
    private fun frame(doc: RemoteComposeDocument, touch: TouchState, t: Float, tapAt: Pair<Float, Float>? = null): Rec {
        if (tapAt != null) touch.down(tapAt.first, tapAt.second)
        val ctx = RemoteContext(); ctx.animationEnabled = true
        val rec = Rec(ctx)
        RemoteComposePlayer(ctx).paint(doc, rec, frameTimeSeconds = t, touchState = touch)
        return rec
    }

    private fun dist(a: Pair<Float, Float>, b: Pair<Float, Float>) =
        abs(a.first - b.first) + abs(a.second - b.second)

    @Test
    fun reTrigger_afterWindowElapse_reActivatesAndReseeds() {
        Builtins.register()
        val doc = DocumentReader.inflate(RcCorpus.readFixture("corpus/impulse_demo_confetti_demo.rc"))
        val touch = TouchState()
        // confetti duration = 20s. id29=0 default ⇒ auto-burst window [0, 20].
        val seed = frame(doc, touch, 0f).first!!         // seed frame (Δt=0, lastFrameTime NaN)
        frame(doc, touch, 0.5f)
        val frozen = frame(doc, touch, 2.0f).first!!     // evolved well away from the seed
        assertTrue(seed != frozen, "particle must evolve pre-elapse (seed=$seed frozen=$frozen)")

        val elapsed = frame(doc, touch, 25.0f)           // 25 > 20 ⇒ window elapsed → impulse inactive + re-arm
        assertTrue(elapsed.count == 0, "elapsed impulse must draw no particles, got ${elapsed.count}")

        // A tap re-enters the window (id29 = 25.1) → Phase A re-seeds (re-armed on elapse) → re-burst.
        val reburst = frame(doc, touch, 25.1f, tapAt = 150f to 150f)
        assertTrue(reburst.count > 0, "tap must re-activate the impulse (re-draw particles), got ${reburst.count}")
        // Without the re-seed, a re-entry (Δt=0) would re-draw the FROZEN positions exactly; the re-seed
        // re-initialises them → the re-burst position must differ from the frozen one. (RAND-immune: equality
        // would require no re-init at all.)
        assertTrue(reburst.first!! != frozen, "re-trigger must re-seed (≠ frozen-evolved), got ${reburst.first} vs $frozen")
    }

    @Test
    fun tapDuringActiveWindow_doesNotReseed_trajectoryContinuous() {
        Builtins.register()
        val doc = DocumentReader.inflate(RcCorpus.readFixture("corpus/impulse_demo_confetti_demo.rc"))
        val touch = TouchState()
        frame(doc, touch, 0f)
        val p1 = frame(doc, touch, 0.1f).first!!
        val p2 = frame(doc, touch, 0.2f).first!!
        val dPre = dist(p1, p2)                           // a normal one-frame evolution step
        // Tap at t=0.3, well INSIDE the active window [0, 20] → must NOT re-seed (only moves startAt).
        val p3 = frame(doc, touch, 0.3f, tapAt = 150f to 150f).first!!
        val dTap = dist(p2, p3)                           // the step across the tap frame
        // A spurious re-seed-on-tap would teleport the particle to a fresh seed → dTap would dwarf dPre.
        // Continuity (no teleport) proves the tap did not reset the simulation (B2: Tap ≠ Re-Seed).
        assertTrue(
            dTap <= 5f * dPre + 1f,
            "tap during active window must NOT re-seed: step across tap ($dTap) should continue the trajectory (~$dPre)",
        )
    }
}
