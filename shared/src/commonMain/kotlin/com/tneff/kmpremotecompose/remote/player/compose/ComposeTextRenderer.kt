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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.tneff.kmpremotecompose.remote.core.operations.BitmapFontData
import com.tneff.kmpremotecompose.remote.player.core.ComputedTextLayout
import kotlin.math.roundToInt

/**
 * The CMP text engine (REM-32, L2-S3 **basis text**) — fills the text half of the player adapter that
 * S1 left as no-ops. One implementation for Android **and** iOS via Compose-Multiplatform text
 * ([TextMeasurer]/[TextLayoutResult], iOS = Skia/Skiko). Replaces upstream's Android-native
 * `nativeCanvas().drawText` / `StaticLayout` path (which is the Android-specific rest the spec flagged).
 *
 * Coordinate convention (faithful to upstream): `drawTextRun` positions text at the **baseline** at
 * `(x, y)`; CMP paints from the layout top-left, so we offset by [TextLayoutResult.firstBaseline].
 *
 * **Paint-state seam (S2↔S3):** the run's color/size/letterSpacing come from the shared
 * [PlayerPaintState] (dev-2's S2 decode), read via [currentStyle]; [textStyle] is the fallback default.
 * Parameter parity is classified in [TEXT_PARAMETER_PARITY] — complex parity (GAP-1) is deferred to L2-D1.
 *
 * The [fontFamilyResolver] is **injected**, never constructed here: on Android `createFontFamilyResolver()`
 * needs a resource loader / `Context`, so the composition supplies `LocalFontFamilyResolver.current`. This
 * keeps `commonMain` platform-clean and `:shared:compileAndroidMain` green (the inverse of the iOS lesson).
 */
class ComposeTextRenderer(
    private val density: Float,
    fontFamilyResolver: FontFamily.Resolver,
) {

    private val measurer: TextMeasurer = TextMeasurer(
        defaultFontFamilyResolver = fontFamilyResolver,
        defaultDensity = Density(density),
        defaultLayoutDirection = LayoutDirection.Ltr,
    )

    /** Fallback text style when no [paintState] is bound (color/fontSize). */
    var textStyle: TextStyle = TextStyle(color = Color.Black, fontSize = 16.sp)

    /**
     * The shared paint-state (S2↔S3 seam, proposal A). When bound, each run's color + size come from
     * it ([deriveTextStyle]); null ⇒ fall back to [textStyle].
     */
    var paintState: PlayerPaintState? = null

    /** The effective style for this draw: derived from [paintState] if bound, else [textStyle]. */
    private fun currentStyle(): TextStyle =
        paintState?.let {
            deriveTextStyle(it.paint.color, it.textSizePx, it.fontStyle, it.fontWeight, density, textStyle)
        } ?: textStyle

    /** Substring [start,end) of [text]; end == -1 (or past the end) means "to the end". */
    private fun slice(text: String, start: Int, end: Int): String {
        val s = start.coerceIn(0, text.length)
        val e = if (end == -1 || end > text.length) text.length else end.coerceIn(s, text.length)
        return text.substring(s, e)
    }

    /** Draw a single text run at the baseline `(x, y)` (upstream `drawTextRun`). */
    fun drawTextRun(canvas: Canvas, text: String, start: Int, end: Int, x: Float, y: Float, rtl: Boolean) {
        val run = slice(text, start, end)
        if (run.isEmpty()) return
        val result = measurer.measure(
            text = run,
            style = currentStyle(),
            layoutDirection = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        )
        // CMP paints from the top-left; shift so the run sits on the baseline at (x, y).
        canvas.save()
        canvas.translate(x, y - result.firstBaseline)
        TextPainter.paint(canvas, result)
        canvas.restore()
    }

    /**
     * Fill [bounds] = (left, top, right, bottom) for the run, relative to a `drawTextRun` at
     * `(0,0)` baseline (upstream `getTextBounds`). **GAP-2 (approximated, flagged):** derived from CMP
     * [TextLayoutResult] metrics, which can sub-pixel diverge from Android `Paint.getTextBounds`.
     */
    fun getTextBounds(text: String, start: Int, end: Int, flags: Int, bounds: FloatArray) {
        if (bounds.size < 4) return
        val run = slice(text, start, end)
        if (run.isEmpty()) {
            bounds[0] = 0f; bounds[1] = 0f; bounds[2] = 0f; bounds[3] = 0f
            return
        }
        val result = measurer.measure(run, style = currentStyle())
        val baseline = result.firstBaseline
        bounds[0] = 0f
        bounds[1] = -baseline
        bounds[2] = result.size.width.toFloat()
        bounds[3] = result.size.height.toFloat() - baseline
    }

    /** Build a multi-line layout (upstream `layoutComplexText`); null text id ⇒ null. */
    fun layoutComplexText(
        text: String?,
        start: Int,
        end: Int,
        alignment: Int,
        overflow: Int,
        maxLines: Int,
        maxWidth: Float,
        maxHeight: Float,
        letterSpacing: Float,
        lineHeightAdd: Float,
        lineHeightMultiplier: Float,
        underline: Boolean,
        strikethrough: Boolean,
    ): ComputedTextLayout? {
        if (text == null) return null
        val run = slice(text, start, end)
        val style = currentStyle().copy(
            textAlign = alignmentToTextAlign(alignment),
            letterSpacing = if (letterSpacing != 0f) letterSpacing.sp else textStyle.letterSpacing,
            lineHeight = if (lineHeightMultiplier > 0f) lineHeightMultiplier.em else textStyle.lineHeight,
            textDecoration = decoration(underline, strikethrough),
        )
        val constraints = Constraints(
            maxWidth = if (maxWidth > 0f) maxWidth.roundToInt() else Constraints.Infinity,
            maxHeight = if (maxHeight > 0f) maxHeight.roundToInt() else Constraints.Infinity,
        )
        val result: TextLayoutResult = measurer.measure(
            text = run,
            style = style,
            overflow = overflowToTextOverflow(overflow),
            maxLines = if (maxLines > 0) maxLines else Int.MAX_VALUE,
            constraints = constraints,
        )
        return ComputedTextLayout(result)
    }

    /** Paint a pre-computed layout at the canvas origin (upstream `drawComplexText`). */
    fun drawComplexText(canvas: Canvas, layout: ComputedTextLayout?) {
        if (layout == null) return
        TextPainter.paint(canvas, layout.layout)
    }

    /**
     * Draw [text] with a bitmap font ([BitmapFontData]) starting at baseline `(x, y)`: each char maps
     * to a [BitmapFontData.Glyph] whose bitmap (resolved by id via [bitmapById]) is blitted, advancing
     * the pen by the glyph's margins + width plus any kerning adjustment for the pair.
     *
     * Greedy longest-match handles multi-char glyphs/ligatures. `bitmapId == -1` is a space (advance
     * only, no blit). **Exact advance/baseline parity is Maestro-verified (L2-S4), flagged:** the data
     * op carries the metrics; the precise pen model is approximated to the standard margin+kerning rule.
     */
    fun drawBitmapFontText(
        canvas: Canvas,
        font: BitmapFontData,
        text: String,
        x: Float,
        y: Float,
        glyphSpacing: Float = 0f,
        bitmapById: (Int) -> ImageBitmap?,
    ) {
        if (text.isEmpty() || font.glyphs.isEmpty()) return
        val byLongest = font.glyphs.sortedByDescending { it.chars.length }
        val kerning = font.kerning.associate { it.key to it.adjustment }
        // Tint the glyph with the current text color (REM-42b). The glyph bitmaps are alpha/light masks;
        // drawn raw they render white-on-white (~250 on a white canvas → invisible). Upstream blits the
        // glyph in the paint color — so apply a tint ColorFilter from the shared paint state (the same
        // color base text uses via currentStyle), defaulting to black when no state is bound.
        val tint = paintState?.paint?.color ?: Color.Black
        val paint = Paint().apply { colorFilter = ColorFilter.tint(tint) }
        var penX = x
        var i = 0
        var prevChars: String? = null
        while (i < text.length) {
            val glyph = byLongest.firstOrNull { it.chars.isNotEmpty() && text.startsWith(it.chars, i) }
            if (glyph == null) { i += 1; continue }
            prevChars?.let { penX += (kerning[it + glyph.chars] ?: 0).toFloat() }
            penX += glyph.marginLeft
            if (glyph.bitmapId != -1) {
                bitmapById(glyph.bitmapId)?.let { bmp ->
                    // Top-left = (penX, y + marginTop); the glyph grows DOWNWARD from y (upstream
                    // `DrawBitmapFontText.paint`: top = mOutY + marginTop). NOT `y - bitmapHeight` —
                    // that pushed glyphs above y → off-screen/negative → invisible digits (REM-42).
                    canvas.drawImage(bmp, Offset(penX, y + glyph.marginTop), paint)
                }
            }
            penX += glyph.bitmapWidth + glyph.marginRight + glyphSpacing
            prevChars = glyph.chars
            i += glyph.chars.length
        }
    }

    private fun alignmentToTextAlign(alignment: Int): TextAlign = when (alignment) {
        TEXT_ALIGN_LEFT -> TextAlign.Left
        TEXT_ALIGN_RIGHT -> TextAlign.Right
        TEXT_ALIGN_CENTER -> TextAlign.Center
        TEXT_ALIGN_JUSTIFY -> TextAlign.Justify // see justificationMode parity (D1)
        TEXT_ALIGN_END -> TextAlign.End
        else -> TextAlign.Start // TEXT_ALIGN_START + default
    }

    // CMP-common has only END ellipsis; START/MIDDLE ellipsis are L2-D1 (approximated → END here).
    private fun overflowToTextOverflow(overflow: Int): TextOverflow = when (overflow) {
        OVERFLOW_ELLIPSIS, OVERFLOW_START_ELLIPSIS, OVERFLOW_MIDDLE_ELLIPSIS -> TextOverflow.Ellipsis
        OVERFLOW_VISIBLE -> TextOverflow.Visible
        else -> TextOverflow.Clip // OVERFLOW_CLIP + default
    }

    private fun decoration(underline: Boolean, strikethrough: Boolean): TextDecoration? = when {
        underline && strikethrough -> TextDecoration.Underline + TextDecoration.LineThrough
        underline -> TextDecoration.Underline
        strikethrough -> TextDecoration.LineThrough
        else -> null
    }

    companion object {
        /**
         * Read-side of the paint-state seam (proposal A): derive a [TextStyle] from the shared state's
         * text [color] + [textSizePx] (px → sp via [density]) + **[fontStyle]/[fontWeight]** (the
         * TextStyle-refinement seam). Pure (no font backend) → headless-testable. letterSpacing/
         * decoration come from the op params, not the bundle, so they stay on [base]. `textSizePx <= 0`
         * keeps the base size. `fontStyle` 0 = normal, 1 = italic; `fontWeight` CSS 100–900 (0 ⇒ base).
         */
        fun deriveTextStyle(
            color: Color,
            textSizePx: Float,
            fontStyle: Int,
            fontWeight: Int,
            density: Float,
            base: TextStyle,
        ): TextStyle {
            val fontSize = if (textSizePx > 0f) with(Density(density)) { textSizePx.toSp() } else base.fontSize
            return base.copy(
                color = color,
                fontSize = fontSize,
                fontStyle = if (fontStyle == 1) FontStyle.Italic else FontStyle.Normal,
                fontWeight = if (fontWeight > 0) FontWeight(fontWeight) else base.fontWeight,
            )
        }

        // Upstream TextLayout alignment constants.
        const val TEXT_ALIGN_LEFT = 1
        const val TEXT_ALIGN_RIGHT = 2
        const val TEXT_ALIGN_CENTER = 3
        const val TEXT_ALIGN_JUSTIFY = 4
        const val TEXT_ALIGN_START = 5
        const val TEXT_ALIGN_END = 6

        // Upstream TextLayout overflow constants.
        const val OVERFLOW_CLIP = 1
        const val OVERFLOW_VISIBLE = 2
        const val OVERFLOW_ELLIPSIS = 3
        const val OVERFLOW_START_ELLIPSIS = 4
        const val OVERFLOW_MIDDLE_ELLIPSIS = 5
    }
}
