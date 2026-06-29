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
package com.tneff.kmpremotecompose.remote.player.particles

import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeDocument
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.RenderRngPins
import com.tneff.kmpremotecompose.remote.player.core.RpnFloatEvaluator
import com.tneff.kmpremotecompose.remote.player.core.seedHostPalette
import kotlin.math.abs

/**
 * REM-143 §5b — the multi-frame draw-capture driver (dev-1 builds, test-3 runs). It paints the SAME inflated
 * doc once per scheduled frame (`frameTimeSeconds = startAt + k·dt`, [ParticleFrameSchedule]) so the sim's
 * op-field particle state evolves across frames (decode-once → paint-N, the verified precondition), and
 * records the per-frame body-draw positions through a [RecordingParticlePaintContext] (black-box output,
 * §5b #5). It then compares those against the independent [ParticleReconstruction] (§5b #3).
 *
 * Determinism: the RNG is pinned to [RenderRngPins.PARTICLE_SEED] ONCE before frame 0 (it then advances
 * continuously across frames, mirroring the sim's continuous consumption); each frame's context is freshly
 * seeded with the BASELINE host palette + density 1.0 (the capture pins), with `animationEnabled = true` so
 * the impulse timeline drives the evolution. The reconstruction reseeds the SAME pin before ITS run, so the
 * two share an identical RAND sequence without sharing a live RNG.
 */
object ParticleGateHarness {

    /**
     * Paint [doc] across [schedule] and return the captured per-particle draw centres per frame
     * (`frames[k]` = the draws of frame k). Reseeds the capture RNG once before frame 0.
     */
    fun captureFrames(doc: RemoteComposeDocument, schedule: ParticleFrameSchedule.Schedule): List<List<RecordingParticlePaintContext.Draw>> {
        RpnFloatEvaluator.seedRngForCapture(RenderRngPins.PARTICLE_SEED)
        val captured = ArrayList<List<RecordingParticlePaintContext.Draw>>(schedule.frameCount + 1)
        for (t in schedule.times) {
            val ctx = RemoteContext().also {
                it.setDensity(1f)
                it.animationEnabled = true
                it.seedHostPalette() // BASELINE pin
            }
            val rec = RecordingParticlePaintContext(ctx)
            RemoteComposePlayer(ctx).paint(doc, rec, frameTimeSeconds = t)
            captured.add(rec.draws.toList())
        }
        return captured
    }

    /** A per-frame mismatch between captured draws and the reconstruction's expected positions. */
    data class Mismatch(val frame: Int, val detail: String)

    /**
     * Compare [captured] draw centres against [expected] reconstruction positions, frame by frame, within
     * [tolerance] (particle order is preserved — both sides are particle-major). Returns the list of
     * mismatches; empty ⇒ the sim's accumulating evolution matches the independent oracle (the gate passes).
     */
    fun compare(
        captured: List<List<RecordingParticlePaintContext.Draw>>,
        expected: List<List<Pair<Float, Float>>>,
        tolerance: Float = 0.5f,
    ): List<Mismatch> {
        val mismatches = mutableListOf<Mismatch>()
        val frames = minOf(captured.size, expected.size)
        if (captured.size != expected.size) {
            mismatches += Mismatch(-1, "frame count: captured=${captured.size} expected=${expected.size}")
        }
        for (k in 0 until frames) {
            val cap = captured[k]
            val exp = expected[k]
            if (cap.size != exp.size) {
                mismatches += Mismatch(k, "particle count: captured=${cap.size} expected=${exp.size}")
                continue
            }
            for (p in cap.indices) {
                val dx = abs(cap[p].cx - exp[p].first)
                val dy = abs(cap[p].cy - exp[p].second)
                if (dx > tolerance || dy > tolerance) {
                    mismatches += Mismatch(k, "particle $p: captured=(${cap[p].cx},${cap[p].cy}) expected=(${exp[p].first},${exp[p].second})")
                }
            }
        }
        return mismatches
    }
}
