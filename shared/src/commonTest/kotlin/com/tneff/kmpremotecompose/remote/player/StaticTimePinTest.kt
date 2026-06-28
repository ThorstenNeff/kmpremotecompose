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

import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeDocument
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-57 — static-mode time pin: time-driven docs (clocks, countdown, flow_control) must render a
 * **deterministic** frozen frame for goldens. In static mode the player seeds the wall-clock time vars
 * from t=0 regardless of the `frameTimeSeconds` the host passes; live mode advances from it.
 */
class StaticTimePinTest {

    private fun timeInSecAfterPaint(animation: Boolean, frameTime: Float, staticPin: Float = 0f): Float {
        val ctx = RemoteContext().apply { animationEnabled = animation }
        RemoteComposePlayer(ctx).paint(
            RemoteComposeDocument(emptyList<Operation>()), NoOpPaintContext(ctx),
            frameTimeSeconds = frameTime, staticTimeSeconds = staticPin,
        )
        return ctx.getFloat(RemoteContext.ID_TIME_IN_SEC)
    }

    @Test
    fun staticMode_pinsTimeToZero_ignoringFrameTime() {
        assertEquals(
            0f, timeInSecAfterPaint(animation = false, frameTime = 5f),
            "static (animation off) → time pinned to 0 even when a non-zero frameTime is passed → freezable golden",
        )
    }

    @Test
    fun liveMode_advancesTimeFromFrameTime() {
        assertEquals(
            5f, timeInSecAfterPaint(animation = true, frameTime = 5f),
            "live (animation on) → time advances from frameTimeSeconds (clocks tick)",
        )
    }

    @Test
    fun staticMode_defaultStaticPin_isZero_unchangedFromBefore() {
        // REM-62: the new staticTimeSeconds param defaults to 0f → byte-for-byte the pre-REM-62 static
        // path (the existing callers/tests that pass no pin must seed exactly t=0).
        assertEquals(
            0f, timeInSecAfterPaint(animation = false, frameTime = 5f, staticPin = 0f),
            "static + pin=0 → t=0 (identical to the original static render)",
        )
    }

    @Test
    fun staticMode_withPin_seedsFromPin() {
        // REM-62: a fixed non-zero static pin seeds the wall-clock time vars deterministically (spread
        // analog-clock hands). 20s → TIME_IN_SEC=20 (sec-within-hour), regardless of frameTimeSeconds.
        assertEquals(
            20f, timeInSecAfterPaint(animation = false, frameTime = 0f, staticPin = 20f),
            "static + pin=20 → TIME_IN_SEC seeded from the pin (deterministic non-zero frame)",
        )
    }

    @Test
    fun liveMode_ignoresStaticPin() {
        // REM-62: the live loop is untouched — the pin is read ONLY in the static branch. Live still
        // advances from frameTimeSeconds even when a static pin is set.
        assertEquals(
            5f, timeInSecAfterPaint(animation = true, frameTime = 5f, staticPin = 99f),
            "live → frameTimeSeconds wins, static pin ignored (live loop untouched)",
        )
    }
}
