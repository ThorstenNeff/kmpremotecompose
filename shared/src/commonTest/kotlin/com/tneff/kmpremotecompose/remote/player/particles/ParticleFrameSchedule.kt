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
import com.tneff.kmpremotecompose.remote.core.operations.layout.ImpulseStart
import kotlin.math.ceil

/**
 * REM-143 §5b #1/#2 — the deterministic multi-frame snapshot schedule for the particle render gate.
 *
 * The live gate paints the same inflated doc N+1 times at `frameTimeSeconds = startAt + k·dt` (k=0..N),
 * NOT wall-clock. This is the schedule half of the harness↔sim contract — pure, deterministic, and
 * independent of both the sim and the reconstruction oracle, so it is the stable anchor both sides agree
 * on. `dt = 1/30 s` fixed; `N = ceil(duration/dt) + DECAY_TAIL_FRAMES`; the decay tail captures particles
 * that keep evolving past `startAt+duration`.
 */
object ParticleFrameSchedule {

    /** Fixed deterministic frame step (§5b #2): 30 fps. */
    const val DT: Float = 1f / 30f

    /** Default impulse window (§5b #1) when ImpulseStart carries no resolvable duration (NaN / ≤0 → 2.0s). */
    const val DEFAULT_DURATION_SECONDS: Float = 2.0f

    /** Extra frames past `startAt+duration` so the decay tail (particles still moving) is captured (§5b #2). */
    const val DECAY_TAIL_FRAMES: Int = 4

    /**
     * Cap on the captured frame count (REM-143 refinement). A few corpus docs (the maze systems) carry a
     * near-infinite impulse duration (persistent particles), so `ceil(duration/dt)` would be hundreds of
     * thousands of frames — impractical to capture/evolve (OOM). The early window + keyframes are where an
     * evolution divergence surfaces, so a bounded window is a sound gate; flagged so it is not mistaken for
     * full-duration coverage of a persistent system.
     */
    const val MAX_FRAMES: Int = 90

    /**
     * The resolved schedule. [frameCount] is N (so there are N+1 paints, k=0..N). [times] is the per-frame
     * `frameTimeSeconds`. Frame 0 is the seed frame.
     */
    data class Schedule(
        val startAt: Float,
        val duration: Float,
        val dt: Float,
        val frameCount: Int,
    ) {
        /** `frameTimeSeconds` for k=0..frameCount (N+1 values; index 0 = seed). */
        val times: List<Float> get() = (0..frameCount).map { startAt + it * dt }

        /** Mandatory keyframes (§5b #1): seed / mid / end. */
        val seedTime: Float get() = startAt
        val midTime: Float get() = startAt + duration / 2f
        val endTime: Float get() = startAt + duration
    }

    /**
     * Build the schedule from a resolved [startAtRaw]/[durationRaw] (seconds). A non-finite or ≤0 duration
     * falls back to [DEFAULT_DURATION_SECONDS]; a non-finite startAt falls back to 0.
     */
    fun of(startAtRaw: Float, durationRaw: Float): Schedule {
        val duration = if (!durationRaw.isFinite() || durationRaw <= 0f) DEFAULT_DURATION_SECONDS else durationRaw
        val startAt = if (startAtRaw.isFinite()) startAtRaw else 0f
        val frameCount = minOf(ceil(duration / DT).toInt() + DECAY_TAIL_FRAMES, MAX_FRAMES)
        return Schedule(startAt = startAt, duration = duration, dt = DT, frameCount = frameCount)
    }

    /**
     * Derive the schedule from the doc's first [ImpulseStart], or null if the doc has none (not an
     * impulse/particle doc). NaN/0 literal startAt/duration fall back to the default window. (A startAt or
     * duration that is a NaN *variable ref* resolves at paint time; for the capture schedule the literal /
     * default is used — refine if a corpus doc proves to need a context-resolved window.)
     */
    fun fromDoc(doc: RemoteComposeDocument): Schedule? {
        val impulse = doc.operations.filterIsInstance<ImpulseStart>().firstOrNull() ?: return null
        return of(startAtRaw = impulse.startAt, durationRaw = impulse.duration)
    }
}
