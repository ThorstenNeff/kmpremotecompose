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
package com.tneff.kmpremotecompose.remote.player.compose

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.createFontFamilyResolver
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-45 `DRAW_TEXT_ON_PATH` on the **real CMP/Skiko** backend — proves the PathMeasure glyph-placement
 * (the successor to the documented `drawTextOnPath(){}` no-op) actually runs end-to-end: it resolves the
 * text + path, places/rotates each glyph along the curve, draws on Skiko without throwing, and bumps the
 * honest draw counter exactly once for a primitive that emitted glyphs. The *visual* on-path correctness
 * (glyphs following the curve, hOffset/vOffset placement) is the test-3 render-sweep oracle's job; this is
 * the necessary-not-sufficient run-path gate.
 */
class Rem45TextOnPathIosTest {

    private fun canvas() = Canvas(ImageBitmap(256, 64))

    private fun ctxWith(text: String, path: Path): RemoteContext =
        RemoteContext().also {
            it.setDensity(2f)
            it.putText(TEXT_ID, text)
            it.putPath(PATH_ID, path)
        }

    private fun paintContext(ctx: RemoteContext) =
        ComposePaintContext(ctx, canvas(), fontFamilyResolver = createFontFamilyResolver())

    @Test
    fun textOnPath_drawsGlyphsAlongPath_andCountsOneDraw() {
        val line = Path().apply { moveTo(0f, 20f); lineTo(220f, 20f) }
        val ctx = ctxWith("ABC", line)
        paintContext(ctx).drawTextOnPath(TEXT_ID, PATH_ID, hOffset = 0f, vOffset = 0f)
        assertEquals(1, ctx.drawCount, "a text-on-path that emitted glyphs counts exactly one honest draw")
    }

    @Test
    fun textOnPath_followsCurvedPath_withoutThrowing() {
        // A quadratic curve exercises the per-glyph tangent rotation (getTangent != constant).
        val curve = Path().apply { moveTo(0f, 50f); quadraticTo(110f, -40f, 220f, 50f) }
        val ctx = ctxWith("Curve", curve)
        paintContext(ctx).drawTextOnPath(TEXT_ID, PATH_ID, hOffset = 4f, vOffset = -6f)
        assertTrue(ctx.drawCount >= 1, "glyphs on a curved path must draw")
    }

    @Test
    fun textOnPath_emptyPath_isCleanNoOp() {
        val ctx = ctxWith("ABC", Path()) // empty path → zero length → nothing to place
        paintContext(ctx).drawTextOnPath(TEXT_ID, PATH_ID, hOffset = 0f, vOffset = 0f)
        assertEquals(0, ctx.drawCount, "an empty path draws nothing (clean no-op, no throw)")
    }

    @Test
    fun textOnPath_hOffsetPastEnd_drawsNothing() {
        val line = Path().apply { moveTo(0f, 20f); lineTo(40f, 20f) } // short path
        val ctx = ctxWith("ABC", line)
        // Start far past the path end → every glyph centre is out of [0,len] → skipped (Android clips).
        paintContext(ctx).drawTextOnPath(TEXT_ID, PATH_ID, hOffset = 9999f, vOffset = 0f)
        assertEquals(0, ctx.drawCount, "text starting past the path end is clipped → no draw")
    }

    private companion object {
        const val TEXT_ID = 10
        const val PATH_ID = 20
    }
}
