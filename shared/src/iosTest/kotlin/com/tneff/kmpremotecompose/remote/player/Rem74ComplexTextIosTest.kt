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
import com.tneff.kmpremotecompose.remote.core.operations.layout.CoreText
import com.tneff.kmpremotecompose.remote.player.compose.ComposeTextRenderer
import com.tneff.kmpremotecompose.remote.player.core.ComputedTextLayout
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * REM-74 (FC-D1) **end-to-end on the real CMP/Skia (`Paragraph`) backend**: drives [CoreText.paint] and
 * proves that, when component text is wider than its measured box, the wrap routing produces a real
 * **multi-line** Skia layout (not a clipped single line); and that text that fits keeps the exact
 * single-line `drawTextRun` path (Bein-2). The text half is delegated to a real [ComposeTextRenderer],
 * so the single-line measure that drives the wrap decision AND the wrapped layout are both real Skiko —
 * not a fake width. Runs in `iosTest` because the CMP text backend needs the graphics backend the bare
 * `jvm()` target lacks. Headless routing/Bein-2 logic lives in `Rem74WrapDecisionTest` (commonTest).
 */
class Rem74ComplexTextIosTest {

    private fun canvas() = Canvas(ImageBitmap(256, 256))

    /**
     * A [PaintContext] whose text primitives are backed by a **real** [ComposeTextRenderer] (Skiko),
     * built on the open [NoOpPaintContext] (paint/matrix ops are no-ops — irrelevant to the layout).
     * Captures the [ComputedTextLayout] the complex path produced and counts which path ran. The
     * textId→string resolution + the param-drop to the renderer mirror `ComposePaintContext` exactly.
     */
    private class RealTextPaintContext(
        context: RemoteContext,
        private val canvas: Canvas,
        private val renderer: ComposeTextRenderer,
    ) : NoOpPaintContext(context) {
        var captured: ComputedTextLayout? = null
        var complexCalls = 0
        var drawComplexCalls = 0
        var textRunCalls = 0

        override fun getTextBounds(textId: Int, start: Int, end: Int, flags: Int, bounds: FloatArray) {
            renderer.getTextBounds(context.getText(textId) ?: "", start, end, flags, bounds)
        }

        override fun layoutComplexText(
            textId: Int, start: Int, end: Int, alignment: Int, overflow: Int, maxLines: Int,
            maxWidth: Float, maxHeight: Float, letterSpacing: Float, lineHeightAdd: Float,
            lineHeightMultiplier: Float, lineBreakStrategy: Int, hyphenationFrequency: Int,
            justificationMode: Int, useUnderline: Boolean, strikethrough: Boolean, flags: Int,
        ): ComputedTextLayout? {
            complexCalls++
            return renderer.layoutComplexText(
                context.getText(textId), start, end, alignment, overflow, maxLines, maxWidth, maxHeight,
                letterSpacing, lineHeightAdd, lineHeightMultiplier, useUnderline, strikethrough,
            ).also { captured = it }
        }

        override fun drawComplexText(computedTextLayout: ComputedTextLayout?) {
            drawComplexCalls++
            renderer.drawComplexText(canvas, computedTextLayout)
        }

        override fun drawTextRun(
            textId: Int, start: Int, end: Int, contextStart: Int, contextEnd: Int,
            x: Float, y: Float, rtl: Boolean,
        ) {
            textRunCalls++
            renderer.drawTextRun(canvas, context.getText(textId) ?: "", start, end, x, y, rtl)
        }
    }

    /** Big-endian 4-byte int param value (wire order), for synthesising a TextStyle param. */
    private fun intParam(id: Int, value: Int): CoreText.Param =
        CoreText.Param(id, byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte()))

    private fun runCoreText(text: String, boxW: Float, params: List<CoreText.Param> = emptyList()): RealTextPaintContext {
        val ctx = RemoteContext()
        val textId = 7
        ctx.putText(textId, text)
        val op = CoreText(textId, params)
        op.setTextDraw(0f, 12f)
        op.setTextBox(0f, 0f, boxW, 200f)
        val paint = RealTextPaintContext(ctx, canvas(), ComposeTextRenderer(density = 2f, createFontFamilyResolver()))
        op.paint(ctx, paint)
        return paint
    }

    @Test
    fun wideText_producesRealMultiLineSkiaLayout() {
        // Bein-1 (the headline width-wrap branch): no maxLines param ⇒ unlimited wrap. Drives the FULL
        // paint→layout→draw pipeline on the real Skiko backend and proves a genuine multi-line layout.
        val p = runCoreText("the quick brown fox jumps over the lazy dog again and again", boxW = 80f)
        assertEquals(1, p.complexCalls, "wide text ⇒ complex path")
        assertEquals(1, p.drawComplexCalls, "and it actually draws the complex layout (not a no-op)")
        assertEquals(0, p.textRunCalls, "must not also single-line draw")
        val layout = assertNotNull(p.captured, "complex layout must be produced")
        assertTrue(layout.lineCount > 1, "real Skia Paragraph must wrap to >1 line (was ${layout.lineCount})")
        assertTrue(layout.width <= 80f + 0.5f, "wrapped width within box (was ${layout.width})")
    }

    @Test
    fun cappedWrap_respectsMaxLines_andStillMultiLine() {
        // Second real-wrap fixture: a long paragraph capped at maxLines=2 (P_MAX_LINES=11). The wrap path
        // must still produce >1 line but honour the cap (≤2) — proving maxLines flows into the real layout.
        val text = "the quick brown fox jumps over the lazy dog again and again and again and again"
        val p = runCoreText(text, boxW = 80f, params = listOf(intParam(11, 2)))
        assertEquals(1, p.complexCalls, "capped wide text ⇒ complex path (maxLines>1)")
        assertEquals(1, p.drawComplexCalls)
        val layout = assertNotNull(p.captured)
        assertTrue(layout.lineCount in 2..2, "maxLines=2 ⇒ exactly the cap on this overflowing text (was ${layout.lineCount})")
    }

    @Test
    fun fittingText_keepsSingleLineDrawTextRun() {
        val p = runCoreText("Hi", boxW = 400f)
        assertEquals(0, p.complexCalls, "text that fits ⇒ no wrap")
        assertEquals(1, p.textRunCalls, "fits ⇒ exact single-line drawTextRun (Bein-2)")
    }
}
