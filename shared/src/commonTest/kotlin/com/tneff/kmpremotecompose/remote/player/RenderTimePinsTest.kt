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

import com.tneff.kmpremotecompose.remote.player.core.RenderTimePins
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * REM-137 — the shared per-doc static-time pin map. The seam every sweep/capture consumes so time-sensitive
 * clocks are captured at the REM-69 cross-platform-safe time, not the degenerate t=0.
 */
class RenderTimePinsTest {

    @Test fun pinnedDocsResolveToTheClockSafeTime() {
        assertEquals(36630f, RenderTimePins.CLOCK_SAFE_TIME_SECONDS)
        assertEquals(36630f, RenderTimePins.timeFor("clock"))
        assertEquals(36630f, RenderTimePins.timeFor("digital_clock1"))
    }

    @Test fun acceptsNameWithOrWithoutRcExtension() {
        assertEquals(36630f, RenderTimePins.timeFor("clock.rc"))
        assertTrue(RenderTimePins.isPinned("digital_clock1.rc"))
        assertTrue(RenderTimePins.isPinned("clock"))
    }

    @Test fun unpinnedDocFallsBackToDefaultZero() {
        assertEquals(0f, RenderTimePins.timeFor("color_table"))
        assertEquals(0f, RenderTimePins.timeFor("some_static_doc.rc"))
        assertFalse(RenderTimePins.isPinned("color_table"))
        // explicit default is honored (a sweep can pass its own global --t baseline).
        assertEquals(5f, RenderTimePins.timeFor("color_table", default = 5f))
        // a pinned doc still wins over a passed default.
        assertEquals(36630f, RenderTimePins.timeFor("clock", default = 5f))
    }
}
