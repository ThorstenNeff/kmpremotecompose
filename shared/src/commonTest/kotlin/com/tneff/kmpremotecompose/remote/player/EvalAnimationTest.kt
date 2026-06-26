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
import com.tneff.kmpremotecompose.remote.core.operations.FloatExpression
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.player.core.FrameClock
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-36 E-D1 animation loop (player-side): the player stays **time-injected** (deterministic goldens),
 * advancing time per pass re-evaluates the time vars, and a time-driven doc requests a continuous
 * repaint (the wakeIn contract the host loop honors). Headless — the live `withFrameNanos` loop is the
 * app's (dev-2) job.
 */
class EvalAnimationTest {

    /** A clock-driven expression: `value = CONTINUOUS_SEC` (the live time var). */
    private fun timeDoc(id: Int) = RemoteComposeDocument(listOf<Operation>(
        FloatExpression(id = id, value = floatArrayOf(WireTypes.asNan(RemoteContext.ID_CONTINUOUS_SEC))),
    ))

    @Test
    fun advancingFrameTime_reEvaluatesTimeVars() {
        val ctx = RemoteContext()
        val player = RemoteComposePlayer(ctx)
        val doc = timeDoc(80)

        val wake0 = player.paint(doc, NoOpPaintContext(ctx), frameTimeSeconds = 0f)
        assertEquals(0f, ctx.getFloat(80), "t=0 → CONTINUOUS_SEC 0 (the fixed golden frame)")
        assertEquals(RemoteComposePlayer.CONTINUOUS, wake0, "time-driven ⇒ continuous repaint requested")

        player.paint(doc, NoOpPaintContext(ctx), frameTimeSeconds = 1.5f)
        assertEquals(1.5f, ctx.getFloat(80), "advancing the frame time re-evaluates the time var → 1.5")
    }

    @Test
    fun staticDoc_requestsNoRepaint() {
        val ctx = RemoteContext()
        val doc = RemoteComposeDocument(listOf<Operation>(FloatExpression(id = 81, value = floatArrayOf(42f))))
        val wake = RemoteComposePlayer(ctx).paint(doc, NoOpPaintContext(ctx))
        assertEquals(RemoteComposePlayer.STATIC, wake, "no time reference ⇒ static (-1), render once")
    }

    @Test
    fun animationDisabled_pinsStaticFrame() {
        val ctx = RemoteContext().apply { animationEnabled = false }
        val wake = RemoteComposePlayer(ctx).paint(timeDoc(82), NoOpPaintContext(ctx))
        assertEquals(RemoteComposePlayer.STATIC, wake, "animationEnabled=false ⇒ static even for a clock doc")
    }

    @Test
    fun isTimeDriven_detectsClockReference() {
        assertTrue(RemoteComposePlayer.isTimeDriven(timeDoc(83)))
        assertTrue(!RemoteComposePlayer.isTimeDriven(
            RemoteComposeDocument(listOf<Operation>(FloatExpression(id = 84, value = floatArrayOf(1f, 2f)))),
        ))
    }

    @Test
    fun frameClock_elapsesNonNegative() {
        assertTrue(FrameClock().elapsed() >= 0f)
    }
}
