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

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.createFontFamilyResolver
import com.tneff.kmpremotecompose.conformance.RcCorpus
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.Builtins
import com.tneff.kmpremotecompose.remote.player.compose.ComposePaintContext
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-120 / REM-118 — the `drawCount` honest-gate integrity fix. Text/glyph draws are real pixel-emitting
 * primitives and must bump [RemoteContext.drawCount] (the BLANK/RENDERS auto-classifier every sweep relies
 * on), exactly as the geometry half does. Runs in `iosTest` because the text path needs the real CMP/Skiko
 * backend (font resolver + [Canvas]).
 *
 * The headline case is `spline_demo_spline_demo1.rc`, whose **only** draw op is `DrawTextAnchored` (the ❤):
 * before this fix the player reported `drawCount == 0` → the gate falsely flagged "rendered empty" (test-2
 * via the ❤ glyph; test-3 on desktop reported the same doc as the "deferred-spline" blank). One root cause.
 */
class Rem120DrawCountIosTest {

    private fun canvas() = Canvas(ImageBitmap(120, 120))

    private fun paintContext(ctx: RemoteContext) =
        ComposePaintContext(ctx, canvas(), fontFamilyResolver = createFontFamilyResolver())

    private fun doc(name: String) = run {
        Builtins.register()
        DocumentReader.inflate(RcCorpus.readFixture("corpus/$name"))
    }

    @Test fun splineDemo_textOnlyDoc_reportsNonZeroDrawCount() {
        // The actual bug: spline_demo's sole draw is the anchored ❤ text. Render it through the real
        // player + paint context and assert the honest-render counter is no longer stuck at 0.
        val ctx = RemoteContext()
        val d = doc("spline_demo_spline_demo1.rc")
        RemoteComposePlayer(ctx).paint(d, paintContext(ctx), frameTimeSeconds = 0f)
        assertTrue(ctx.drawCount > 0, "text-only doc must count its text draw (was ${ctx.drawCount})")
    }

    @Test fun drawTextRun_incrementsDrawCount() {
        val ctx = RemoteContext()
        val pc = paintContext(ctx)
        ctx.putText(7, "hi")
        assertEquals(0, ctx.drawCount)
        pc.drawTextRun(7, 0, -1, 0, -1, 4f, 20f, rtl = false)
        assertEquals(1, ctx.drawCount, "a text run must bump the honest-render counter")
    }

    @Test fun drawComplexText_countsOnlyWhenLaidOut() {
        val ctx = RemoteContext()
        val pc = paintContext(ctx)
        ctx.putText(7, "hello world that wraps onto lines")
        pc.drawComplexText(null) // nothing laid out → no draw
        assertEquals(0, ctx.drawCount, "a null layout draws nothing → no count")
        val layout = pc.layoutComplexText(
            7, 0, -1, 1, 1, 0, 80f, 0f, 0f, 0f, 0f, 0, 0, 0, false, false, 0,
        )
        pc.drawComplexText(layout)
        assertEquals(1, ctx.drawCount, "a drawn complex layout counts")
    }

    @Test fun unresolvedTextId_doesNotCount() {
        val ctx = RemoteContext()
        val pc = paintContext(ctx)
        pc.drawTextRun(99, 0, -1, 0, -1, 0f, 0f, rtl = false) // no text registered → no-op
        assertEquals(0, ctx.drawCount, "an unresolved text id draws nothing → no count")
    }

    @Test fun emptySlice_doesNotCount() {
        // assist nit: a [start==end] slice paints nothing — the renderer returns false → no increment,
        // consistent with the drawComplexText null-guard. (An over-count here would re-break the honest gate.)
        val ctx = RemoteContext()
        val pc = paintContext(ctx)
        ctx.putText(7, "hello")
        pc.drawTextRun(7, 3, 3, 3, 3, 0f, 0f, rtl = false) // empty slice
        assertEquals(0, ctx.drawCount, "an empty slice draws nothing → no count")
    }
}
