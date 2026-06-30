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
import kotlin.test.assertEquals

/**
 * REM-34 — RcRouter deterministic unknown-doc handling (test-2 fix): a deep-link's `rc` value maps to a
 * single deterministic outcome so `rc-doc` is a reliable sweep anchor (no silent default-fallback, no
 * echo of an unrendered name). Valid → selected; blank/invalid → [RcRouter.UNKNOWN_DOC] (→ rc-error);
 * null (no param) → unchanged (default-launch path).
 */
class RcRouterTest {

    @Test
    fun select_isDeterministic() {
        RcRouter.select("c_box")
        assertEquals("c_box", RcRouter.docName, "valid name selected")

        RcRouter.select("definitely_not_bundled_xyz")
        assertEquals("definitely_not_bundled_xyz", RcRouter.docName, "valid-but-unknown name kept (→ rc-error at load)")

        RcRouter.select("evil.rc")
        assertEquals(RcRouter.UNKNOWN_DOC, RcRouter.docName, "invalid charset → deterministic sentinel, not silent fallback")

        RcRouter.select("../secret")
        assertEquals(RcRouter.UNKNOWN_DOC, RcRouter.docName, "path-traversal → sentinel (no escape)")

        RcRouter.select("   ")
        assertEquals(RcRouter.UNKNOWN_DOC, RcRouter.docName, "blank → sentinel")

        val before = RcRouter.docName
        RcRouter.select(null)
        assertEquals(before, RcRouter.docName, "null (no rc param) → unchanged (default-launch preserved)")
    }

    @Test
    fun setStaticTime_failsSafeToZero() {
        // REM-62: only a valid non-negative number changes the static frame; everything else → 0f (the
        // pre-REM-62 t=0 path), so a malformed `&t` can never produce a non-deterministic capture.
        RcRouter.setStaticTime("20")
        assertEquals(20f, RcRouter.staticTimeSeconds, "valid number parsed")

        RcRouter.setStaticTime("2.5")
        assertEquals(2.5f, RcRouter.staticTimeSeconds, "valid fractional parsed")

        RcRouter.setStaticTime("0")
        assertEquals(0f, RcRouter.staticTimeSeconds, "&t=0 → 0f (original t=0 path)")

        RcRouter.setStaticTime(null)
        assertEquals(0f, RcRouter.staticTimeSeconds, "absent → 0f (original t=0 path)")

        RcRouter.setStaticTime("20"); RcRouter.setStaticTime("  ")
        assertEquals(0f, RcRouter.staticTimeSeconds, "blank → 0f")

        RcRouter.setStaticTime("20"); RcRouter.setStaticTime("abc")
        assertEquals(0f, RcRouter.staticTimeSeconds, "non-numeric → 0f")

        RcRouter.setStaticTime("20"); RcRouter.setStaticTime("-5")
        assertEquals(0f, RcRouter.staticTimeSeconds, "negative → 0f (no nonsensical seed)")

        RcRouter.setStaticTime("20"); RcRouter.setStaticTime("NaN")
        assertEquals(0f, RcRouter.staticTimeSeconds, "non-finite → 0f")

        RcRouter.setStaticTime(null) // reset shared state for other tests
    }

    @Test
    fun resetForLaunch_clearsTransientLiveAndPin() {
        // REM-62 hardening: a fresh launch must start static (t=0, not live), even if a prior capture
        // left the process-singleton in a live/pinned state.
        RcRouter.live = true
        RcRouter.setStaticTime("7")
        RcRouter.setEpochSeconds("1751529600")
        RcRouter.resetForLaunch()
        assertEquals(false, RcRouter.live, "resetForLaunch → live=false (no leaked animation)")
        assertEquals(0f, RcRouter.staticTimeSeconds, "resetForLaunch → static pin back to t=0")
        assertEquals(0L, RcRouter.epochSeconds, "REM-178: resetForLaunch → epoch back to 0L (epochFor lookup applies)")
    }

    @Test
    fun setEpochSeconds_failsSafeToZero() {
        // REM-178: only a valid non-negative Long changes the epoch override; everything else → 0L
        // (no override → RenderTimePins.epochFor lookup applies), so a malformed `&epoch` can never
        // produce a non-deterministic capture.
        RcRouter.setEpochSeconds("1751529600")
        assertEquals(1751529600L, RcRouter.epochSeconds, "valid Unix-epoch parsed")

        RcRouter.setEpochSeconds("0")
        assertEquals(0L, RcRouter.epochSeconds, "&epoch=0 → 0L (legacy Jan-1970 path)")

        RcRouter.setEpochSeconds(null)
        assertEquals(0L, RcRouter.epochSeconds, "absent → 0L")

        RcRouter.setEpochSeconds("1751529600"); RcRouter.setEpochSeconds("  ")
        assertEquals(0L, RcRouter.epochSeconds, "blank → 0L")

        RcRouter.setEpochSeconds("1751529600"); RcRouter.setEpochSeconds("not-a-number")
        assertEquals(0L, RcRouter.epochSeconds, "non-numeric → 0L")

        RcRouter.setEpochSeconds("1751529600"); RcRouter.setEpochSeconds("-1")
        assertEquals(0L, RcRouter.epochSeconds, "negative → 0L (no nonsensical seed)")

        RcRouter.setEpochSeconds("3.14")
        assertEquals(0L, RcRouter.epochSeconds, "non-Long (float) → 0L")

        RcRouter.setEpochSeconds(null) // reset shared state for other tests
    }
}
