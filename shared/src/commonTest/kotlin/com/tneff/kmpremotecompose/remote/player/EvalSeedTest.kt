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
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-36 E-Seed: the player seeds the system variables (window/density) into the store **before** the
 * variable phase, so `FLOAT_WINDOW_WIDTH`/`HEIGHT`/`DENSITY` references resolve to real sizes instead
 * of the `0f` default that collapses `drawOval(0,0,FLOAT_WINDOW_WIDTH,…)` to a blank degenerate shape.
 */
class EvalSeedTest {

    @Test
    fun seedSystemVariables_loadsWindowAndDensity() {
        val ctx = RemoteContext()
        ctx.setDensity(2.5f)
        ctx.seedSystemVariables(600f, 400f)
        assertEquals(600f, ctx.getFloat(RemoteContext.ID_WINDOW_WIDTH))
        assertEquals(400f, ctx.getFloat(RemoteContext.ID_WINDOW_HEIGHT))
        assertEquals(2.5f, ctx.getFloat(RemoteContext.ID_DENSITY))
    }

    @Test
    fun player_seedsBeforePhaseA_soWindowVarResolves() {
        val ctx = RemoteContext()
        // FLOAT_WINDOW_WIDTH = asNan(ID_WINDOW_WIDTH); an expression that just references it.
        val doc = RemoteComposeDocument(listOf<Operation>(
            FloatExpression(id = 50, value = floatArrayOf(WireTypes.asNan(RemoteContext.ID_WINDOW_WIDTH))),
        ))

        RemoteComposePlayer(ctx).paint(doc, NoOpPaintContext(ctx), windowWidth = 600f, windowHeight = 400f)

        // The window var was seeded before Phase A, so the expression resolved to the real width.
        assertEquals(600f, ctx.getFloat(50), "FLOAT_WINDOW_WIDTH must resolve to the seeded viewport width")
    }

    @Test
    fun seedSystemVariables_seedsStaticTimeVars() {
        val ctx = RemoteContext()
        // Static MVP frame (t=0) ⇒ a valid 12:00:00 clock.
        ctx.seedSystemVariables(600f, 400f, timeSeconds = 0f)
        assertEquals(0f, ctx.getFloat(RemoteContext.ID_CONTINUOUS_SEC))
        assertEquals(0f, ctx.getFloat(RemoteContext.ID_TIME_IN_SEC))
        assertEquals(0f, ctx.getFloat(RemoteContext.ID_TIME_IN_MIN))
        assertEquals(0f, ctx.getFloat(RemoteContext.ID_TIME_IN_HR))
        assertEquals(0f, ctx.getFloat(RemoteContext.ID_OFFSET_TO_UTC))

        // A later frame time (1h 2m 5s) → upstream RemoteClock components:
        // sec-within-hour = 2*60+5 = 125; min-within-day = 1*60+2 = 62; hour = 1.
        ctx.seedSystemVariables(600f, 400f, timeSeconds = 3725f)
        assertEquals(125f, ctx.getFloat(RemoteContext.ID_CONTINUOUS_SEC))
        assertEquals(125f, ctx.getFloat(RemoteContext.ID_TIME_IN_SEC))
        assertEquals(62f, ctx.getFloat(RemoteContext.ID_TIME_IN_MIN))
        assertEquals(1f, ctx.getFloat(RemoteContext.ID_TIME_IN_HR))
    }

    @Test
    fun player_seedsContinuousSecFromFrameTime() {
        val ctx = RemoteContext()
        val doc = RemoteComposeDocument(listOf<Operation>(
            FloatExpression(id = 60, value = floatArrayOf(WireTypes.asNan(RemoteContext.ID_CONTINUOUS_SEC))),
        ))
        RemoteComposePlayer(ctx).paint(doc, NoOpPaintContext(ctx), frameTimeSeconds = 12f, windowWidth = 100f, windowHeight = 100f)
        assertEquals(12f, ctx.getFloat(60), "CONTINUOUS_SEC resolves to the injected frame time")
    }

    @Test
    fun player_defaultsWindowToDocumentDims_whenNoViewportGiven() {
        // No header ⇒ document dims are 0; seeding still runs (degenerate only without dims, as upstream).
        val ctx = RemoteContext()
        RemoteComposePlayer(ctx).paint(RemoteComposeDocument(emptyList()), NoOpPaintContext(ctx))
        assertEquals(0f, ctx.getFloat(RemoteContext.ID_WINDOW_WIDTH), "no header + no viewport ⇒ 0 (upstream parity)")
    }
}
