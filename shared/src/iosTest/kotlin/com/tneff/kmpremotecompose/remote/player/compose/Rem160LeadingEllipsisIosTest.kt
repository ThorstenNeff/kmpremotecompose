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
 * REM-160 leading-ellipsis render path on the **real CMP/Skiko** backend — proves
 * [ComposePaintContext.drawTextRunClipped] with `leadingEllipsis = true` (the left/centered/RTL overflow
 * case) runs end-to-end: a too-wide run draws (front-truncated `… + suffix`) without throwing, and the
 * degenerate widths are clean no-ops. Visual placement is the test-3 sweep.
 */
class Rem160LeadingEllipsisIosTest {

    private fun ctx(text: String) = RemoteContext().also { it.setDensity(2f); it.putText(7, text) }
    private fun paint(ctx: RemoteContext) =
        ComposePaintContext(ctx, Canvas(ImageBitmap(256, 64)), fontFamilyResolver = createFontFamilyResolver())

    @Test
    fun leadingEllipsis_longText_draws() {
        val c = ctx("A very long left-overflowing anchored label")
        paint(c).drawTextRunClipped(7, 0, -1, x = 0f, y = 20f, rtl = false, maxWidth = 60f, leadingEllipsis = true)
        assertEquals(1, c.drawCount, "front-truncated overflow text still draws once")
    }

    @Test
    fun leadingEllipsis_nonPositiveMaxWidth_noOp() {
        val c = ctx("anything")
        paint(c).drawTextRunClipped(7, 0, -1, x = 0f, y = 20f, rtl = false, maxWidth = 0f, leadingEllipsis = true)
        assertEquals(0, c.drawCount, "no room → nothing drawn, no throw")
    }

    @Test
    fun leadingEllipsis_fittingText_drawsUnchanged() {
        val c = ctx("Hi")
        paint(c).drawTextRunClipped(7, 0, -1, x = 0f, y = 20f, rtl = false, maxWidth = 5000f, leadingEllipsis = true)
        assertEquals(1, c.drawCount, "fitting text draws once via the unchanged path")
    }
}
