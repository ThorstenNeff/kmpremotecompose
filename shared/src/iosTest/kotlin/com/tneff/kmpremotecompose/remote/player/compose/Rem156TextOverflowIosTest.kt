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
import androidx.compose.ui.text.font.createFontFamilyResolver
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-156 ellipsize render path on the **real CMP/Skiko** backend — proves
 * [ComposePaintContext.drawTextRunClipped] (the overflow policy for `DRAW_TEXT_ANCHOR`) runs end-to-end:
 * a too-wide run draws (ellipsized) without throwing, a fitting run draws unchanged, and the degenerate
 * widths (≤0 / empty text) are clean no-ops. The visual ellipsis correctness is the test-3 render sweep;
 * this is the necessary-not-sufficient run-path + branch gate.
 */
class Rem156TextOverflowIosTest {

    private fun canvas() = Canvas(ImageBitmap(256, 64))

    private fun ctxWith(text: String) = RemoteContext().also { it.setDensity(2f); it.putText(TEXT_ID, text) }

    private fun paintContext(ctx: RemoteContext) =
        ComposePaintContext(ctx, canvas(), fontFamilyResolver = createFontFamilyResolver())

    @Test
    fun longText_overSmallMaxWidth_drawsEllipsized() {
        val ctx = ctxWith("This is a very long anchored label that overflows the surface")
        paintContext(ctx).drawTextRunClipped(TEXT_ID, 0, -1, x = 0f, y = 20f, rtl = false, maxWidth = 60f)
        assertEquals(1, ctx.drawCount, "overflowing text must still draw (ellipsized) → one honest draw")
    }

    @Test
    fun fittingText_drawsOnce() {
        val ctx = ctxWith("Hi")
        paintContext(ctx).drawTextRunClipped(TEXT_ID, 0, -1, x = 0f, y = 20f, rtl = false, maxWidth = 5000f)
        assertEquals(1, ctx.drawCount, "fitting text draws once via the unchanged path")
    }

    @Test
    fun nonPositiveMaxWidth_isCleanNoOp() {
        val ctx = ctxWith("anything")
        paintContext(ctx).drawTextRunClipped(TEXT_ID, 0, -1, x = 0f, y = 20f, rtl = false, maxWidth = 0f)
        assertEquals(0, ctx.drawCount, "no room (maxWidth ≤ 0) draws nothing, no throw")
    }

    @Test
    fun emptyText_isCleanNoOp() {
        val ctx = ctxWith("")
        paintContext(ctx).drawTextRunClipped(TEXT_ID, 0, -1, x = 0f, y = 20f, rtl = false, maxWidth = 100f)
        assertEquals(0, ctx.drawCount, "empty text draws nothing")
    }

    private companion object {
        const val TEXT_ID = 7
    }
}
