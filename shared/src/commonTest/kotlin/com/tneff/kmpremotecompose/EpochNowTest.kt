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
package com.tneff.kmpremotecompose

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * REM-178-S2 — sanity check on the per-platform [epochNow] actual: the returned value must lie in a
 * **plausible wall-clock window** (post-2026-01-01 UTC, pre-2100-01-01 UTC). The bar is loose on
 * purpose — we are not asserting the precise instant (NTP drift / sim clock skew can move it ±sec)
 * but rather catching the actual-wiring class of bugs:
 *  - wrong unit (ms returned where seconds expected → ≈ 1000× too big),
 *  - wrong epoch (e.g. Apple's 2001 reference epoch instead of Unix 1970 → ≈ 30y off),
 *  - constant-zero / uninitialised return (the bug the static path would silently hide),
 *  - wraparound / sign loss (Int truncation on a Long-required value).
 *
 * Runs against the jvmTest actual ([System.currentTimeMillis] / 1000) — the iOS/wasm actuals share
 * the same plausibility contract; their per-target sanity is covered by the build itself (they
 * compile + return Long).
 */
class EpochNowTest {

    @Test
    fun epochNow_isInPlausibleWindow() {
        val now = epochNow()
        // 2026-01-01 00:00:00 UTC = 1767225600 (after which any wall-clock reading must land,
        // given today's date is 2026-06-30 per project context). The pre-2100 cap catches the
        // unit-mistake class (e.g. millis-as-seconds would yield ≈ 1.75e12, > the 2100 cap).
        val lower = 1767225600L // 2026-01-01 UTC
        val upper = 4102444800L // 2100-01-01 UTC
        assertTrue(now in lower..upper, "epochNow() out of plausible window: got $now, expected [$lower..$upper]")
    }
}
