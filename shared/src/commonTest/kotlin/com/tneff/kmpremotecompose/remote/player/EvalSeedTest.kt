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
import com.tneff.kmpremotecompose.remote.core.operations.Header
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.layout.RootContentBehavior
import com.tneff.kmpremotecompose.remote.player.core.ContentScaling
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
    fun player_seedsWindowFromDocDims_soWindowVarResolves() {
        val ctx = RemoteContext()
        // Window vars are seeded from DOC dims (header), not the surface (REM-36 RootContentBehavior).
        val doc = RemoteComposeDocument(listOf<Operation>(
            Header.fromProperties(mapOf(Header.DOC_WIDTH to 600, Header.DOC_HEIGHT to 400)),
            FloatExpression(id = 50, value = floatArrayOf(WireTypes.asNan(RemoteContext.ID_WINDOW_WIDTH))),
        ))

        RemoteComposePlayer(ctx).paint(doc, NoOpPaintContext(ctx), surfaceWidth = 1200f, surfaceHeight = 800f)

        // Window var resolved to the DOC width (600), not the surface (1200) — doc-space authoring.
        assertEquals(600f, ctx.getFloat(50), "FLOAT_WINDOW_WIDTH resolves to the doc dim, not the surface")
    }

    /** A [NoOpPaintContext] that records the doc→surface transform the player applies. */
    private class RecordingTransformContext(context: RemoteContext) : NoOpPaintContext(context) {
        val log = mutableListOf<String>()
        override fun scale(scaleX: Float, scaleY: Float) { log += "scale($scaleX,$scaleY)" }
        override fun translate(translateX: Float, translateY: Float) { log += "translate($translateX,$translateY)" }
    }

    @Test
    fun player_appliesRootContentBehaviorScale_docToSurface() {
        val ctx = RemoteContext()
        val doc = RemoteComposeDocument(listOf<Operation>(
            Header.fromProperties(mapOf(Header.DOC_WIDTH to 600, Header.DOC_HEIGHT to 400)),
            RootContentBehavior(
                scroll = 0,
                alignment = ContentScaling.ALIGNMENT_HORIZONTAL_CENTER or ContentScaling.ALIGNMENT_VERTICAL_CENTER,
                sizing = ContentScaling.SIZING_SCALE,
                mode = ContentScaling.SCALE_FIT,
            ),
        ))
        val rec = RecordingTransformContext(ctx)

        // 600x400 doc into a 1200x800 surface, SCALE_FIT → uniform 2x, centered (content fills → t=0).
        RemoteComposePlayer(ctx).paint(doc, rec, surfaceWidth = 1200f, surfaceHeight = 800f)

        assertEquals(listOf("translate(0.0,0.0)", "scale(2.0,2.0)"), rec.log)
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
        RemoteComposePlayer(ctx).paint(doc, NoOpPaintContext(ctx), frameTimeSeconds = 12f)
        assertEquals(12f, ctx.getFloat(60), "CONTINUOUS_SEC resolves to the injected frame time")
    }

    @Test
    fun contentScaling_modesAndAlignment_matchUpstream() {
        // 600x400 doc into 1200x600 surface. sx=2, sy=1.5.
        fun scale(mode: Int) = ContentScaling.computeScale(1200f, 600f, 600f, 400f, ContentScaling.SIZING_SCALE, mode)
        assertEquals(1.5f to 1.5f, scale(ContentScaling.SCALE_FIT), "FIT = min(sx,sy)")
        assertEquals(2f to 2f, scale(ContentScaling.SCALE_CROP), "CROP = max(sx,sy)")
        assertEquals(2f to 2f, scale(ContentScaling.SCALE_FILL_WIDTH), "FILL_WIDTH = sx")
        assertEquals(1.5f to 1.5f, scale(ContentScaling.SCALE_FILL_HEIGHT), "FILL_HEIGHT = sy")
        assertEquals(2f to 1.5f, scale(ContentScaling.SCALE_FILL_BOUNDS), "FILL_BOUNDS = (sx,sy) non-uniform")
        assertEquals(1f to 1f, scale(ContentScaling.SCALE_INSIDE), "INSIDE clamps to <=1")
        // not SIZING_SCALE ⇒ identity.
        assertEquals(1f to 1f, ContentScaling.computeScale(1200f, 600f, 600f, 400f, ContentScaling.SIZING_LAYOUT, 4))

        // FIT scale 1.5 → content 900x600; END/BOTTOM aligns to the far edge.
        assertEquals(
            300f to 0f, // tx = 1200-900 = 300; ty = 600-600 = 0
            ContentScaling.computeTranslate(1200f, 600f, 1.5f, 1.5f, 600f, 400f, ContentScaling.ALIGNMENT_END or ContentScaling.ALIGNMENT_TOP),
        )
    }

    @Test
    fun player_defaultsWindowToDocumentDims_whenNoViewportGiven() {
        // No header ⇒ document dims are 0; seeding still runs (degenerate only without dims, as upstream).
        val ctx = RemoteContext()
        RemoteComposePlayer(ctx).paint(RemoteComposeDocument(emptyList()), NoOpPaintContext(ctx))
        assertEquals(0f, ctx.getFloat(RemoteContext.ID_WINDOW_WIDTH), "no header + no viewport ⇒ 0 (upstream parity)")
    }
}
