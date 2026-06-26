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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import com.tneff.kmpremotecompose.remote.core.operations.BitmapFontData
import com.tneff.kmpremotecompose.remote.player.compose.ComposePaintContext
import com.tneff.kmpremotecompose.remote.player.compose.ComposeTextRenderer
import com.tneff.kmpremotecompose.remote.player.compose.PlayerPaintState
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * REM-32 basis-text behaviour on the real CMP text backend (Skiko `Paragraph`). Runs in `iosTest`
 * because [ComposeTextRenderer]'s font resolver + [Canvas]/[ImageBitmap] need the graphics backend,
 * which the bare `jvm()` test target lacks (Skiko `LibraryLoadException`). The iOS gate is the
 * load-bearing test the PO called out.
 */
class ComposeTextRendererIosTest {

    private fun canvas() = Canvas(ImageBitmap(64, 64))

    @Test
    fun getTextBounds_isBaselineRelative_andNonEmpty() {
        val r = ComposeTextRenderer(density = 2f)
        val bounds = FloatArray(4)
        r.getTextBounds("Hello", 0, -1, 0, bounds)
        assertTrue(bounds[2] > 0f, "width must be positive")
        assertTrue(bounds[3] > bounds[1], "bottom must be below top")
        assertTrue(bounds[1] < 0f, "top is above the baseline (negative)")
    }

    @Test
    fun drawTextRun_andSubstring_doNotThrow() {
        val r = ComposeTextRenderer(density = 2f)
        val c = canvas()
        r.drawTextRun(c, "Hello world", 0, -1, 4f, 20f, rtl = false)
        r.drawTextRun(c, "Hello world", 0, 5, 4f, 40f, rtl = false) // substring "Hello"
        r.drawTextRun(c, "", 0, -1, 0f, 0f, rtl = false) // empty run: no-op
    }

    @Test
    fun layoutComplexText_honoursMaxWidthAndMaxLines() {
        val r = ComposeTextRenderer(density = 2f)
        val layout = r.layoutComplexText(
            text = "the quick brown fox jumps over the lazy dog",
            start = 0, end = -1,
            alignment = ComposeTextRenderer.TEXT_ALIGN_START,
            overflow = ComposeTextRenderer.OVERFLOW_ELLIPSIS,
            maxLines = 2,
            maxWidth = 80f, maxHeight = 0f,
            letterSpacing = 0f, lineHeightAdd = 0f, lineHeightMultiplier = 0f,
            underline = false, strikethrough = false,
        )
        assertNotNull(layout)
        assertTrue(layout.lineCount in 1..2, "maxLines respected (was ${layout.lineCount})")
        assertTrue(layout.width <= 80f + 0.5f, "width within maxWidth (was ${layout.width})")
        r.drawComplexText(canvas(), layout)

        // null text id ⇒ null layout.
        assertNull(
            r.layoutComplexText(null, 0, -1, 0, 0, 0, 0f, 0f, 0f, 0f, 0f, false, false),
        )
    }

    @Test
    fun bitmapFontText_advancesAndBlitsWithoutThrow() {
        val r = ComposeTextRenderer(density = 2f)
        val glyph = BitmapFontData.Glyph(
            chars = "A", bitmapId = 7,
            marginLeft = 1, marginTop = 0, marginRight = 1, marginBottom = 0,
            bitmapWidth = 8, bitmapHeight = 10,
        )
        val font = BitmapFontData(id = 1, glyphs = listOf(glyph))
        val glyphBitmap = ImageBitmap(8, 10)
        r.drawBitmapFontText(canvas(), font, "AAA", 0f, 20f) { id -> if (id == 7) glyphBitmap else null }
        // unknown chars are skipped, missing bitmap is tolerated (advance only).
        r.drawBitmapFontText(canvas(), font, "AxA", 0f, 20f) { null }
    }

    @Test
    fun renderer_readsBoundPaintState_sizeAffectsMeasure() {
        // Read-side of the S2↔S3 seam (proposal A) on the real backend: the bound PlayerPaintState's
        // textSizePx drives the measured style. (Headless logic is in TextPaintStateReadSideTest.)
        val r = ComposeTextRenderer(density = 2f)
        val small = FloatArray(4)
        r.getTextBounds("Hi", 0, -1, 0, small) // default 16sp
        // dev-2's real PlayerPaintState: no-arg ctor + var props.
        r.paintState = PlayerPaintState().apply { paint = Paint().apply { color = Color.Red }; textSizePx = 80f }
        val big = FloatArray(4)
        r.getTextBounds("Hi", 0, -1, 0, big)
        assertTrue(big[2] > small[2], "larger textSizePx must widen the measured bounds")
    }

    @Test
    fun composePaintContext_textHalf_rendersThroughTheAdapter() {
        val context = RemoteContext()
        context.putText(3, "Hi")
        val adapter = ComposePaintContext(context, canvas())
        adapter.drawTextRun(3, 0, -1, 0, 0, 2f, 20f, rtl = false) // resolves text id 3, draws via renderer
        val bounds = FloatArray(4)
        adapter.getTextBounds(3, 0, -1, 0, bounds)
        assertTrue(bounds[2] > 0f)
    }
}
