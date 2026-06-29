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

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** REM-143 §5b #1/#2 — the deterministic multi-frame snapshot schedule. */
class ParticleFrameScheduleTest {

    @Test fun schedule_dtIs30fps_andDecayTail() {
        // duration 2.0s @ dt=1/30 → 60 frames + 4 decay tail = N=64; times = 65 values (k=0..64).
        val s = ParticleFrameSchedule.of(startAtRaw = 0f, durationRaw = 2.0f)
        assertEquals(1f / 30f, s.dt)
        assertEquals(64, s.frameCount, "ceil(2.0/(1/30)) + 4 = 60 + 4")
        assertEquals(65, s.times.size, "N+1 paints incl. seed frame k=0")
        assertEquals(0f, s.times.first(), "frame 0 = seed @ startAt")
    }

    @Test fun schedule_timesAreStartAtPlusKDt_deterministic() {
        val s = ParticleFrameSchedule.of(startAtRaw = 0.5f, durationRaw = 1.0f)
        s.times.forEachIndexed { k, t -> assertTrue(abs(t - (0.5f + k * (1f / 30f))) < 1e-5f, "t[$k]") }
        assertEquals(0.5f, s.seedTime)
        assertEquals(1.0f, s.midTime, "startAt + duration/2")
        assertEquals(1.5f, s.endTime, "startAt + duration")
    }

    @Test fun schedule_nonFiniteOrZeroDuration_fallsBackToDefaultWindow() {
        for (bad in listOf(Float.NaN, 0f, -1f)) {
            val s = ParticleFrameSchedule.of(startAtRaw = 0f, durationRaw = bad)
            assertEquals(ParticleFrameSchedule.DEFAULT_DURATION_SECONDS, s.duration, "duration=$bad → default")
        }
        // non-finite startAt → 0.
        assertEquals(0f, ParticleFrameSchedule.of(Float.NaN, 1f).startAt)
    }
}
