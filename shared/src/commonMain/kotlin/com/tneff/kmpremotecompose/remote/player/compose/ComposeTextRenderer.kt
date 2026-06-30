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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
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
    /**
     * REM-110/REM-106: a bundled fallback [FontFamily] covering the symbol glyphs the CMP **default** font
     * lacks on web (♥/❤/⚡/⬩/▲/↑/↓ — Misc-Symbols/Dingbats/Geometric/Arrows). Injected (built from a
     * `composeResources/font` resource in the composition, like [fontFamilyResolver]). Null ⇒ no
     * fallback (Android/iOS/Desktop + headless tests). Applied **per segment** via [styledRun] +
     * [needsFallbackCodepoint] (REM-106 generalized REM-110's per-run swap to mixed-run-safe segments).
     */
    private val symbolFallbackFamily: FontFamily? = null,
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

    /**
     * The effective **base** style for this draw: derived from [paintState] if bound, else [textStyle].
     * REM-106: the per-glyph fallback family is no longer applied here (the old REM-110 whole-run swap) —
     * it is applied per **segment** in [styledRun], so a mixed run keeps Latin on the base font and only
     * swaps the symbol spans. This style carries everything *except* that per-span family.
     */
    private fun currentStyle(): TextStyle =
        paintState?.let {
            deriveTextStyle(it.paint.color, it.textSizePx, it.fontStyle, it.fontWeight, density, textStyle)
        } ?: textStyle

    /**
     * REM-106 (generalizes REM-110) — render [run] as an [AnnotatedString] that routes every **maximal
     * segment** of fallback-needed codepoints ([needsFallbackCodepoint]) to the bundled
     * [symbolFallbackFamily], leaving every other segment on the base font. The segment-aware successor to
     * REM-110's whole-run family swap: a mixed run like `"A♥B"` now renders `A`/`B` on the base font and
     * only `♥` on the fallback, instead of forcing the entire run (Latin included) onto the symbol font.
     *
     * **Golden-safe by construction:** when [symbolFallbackFamily] is null (Android/iOS/Desktop — the
     * system font already covers these glyphs) OR the run carries no fallback codepoint, this returns a
     * plain `AnnotatedString(run)` — byte-identical to the pre-REM-106 measure input, so no target's
     * golden shifts. For the wasm-triggering corpus runs (all pure-symbol) the single span equals the old
     * whole-run swap → identical output there too. Only genuinely **mixed** content changes behavior.
     */
    private fun styledRun(run: String): AnnotatedString {
        val fb = symbolFallbackFamily
        if (fb == null || run.none { needsFallbackCodepoint(it.code) }) return AnnotatedString(run)
        return buildAnnotatedString {
            append(run)
            var i = 0
            while (i < run.length) {
                if (needsFallbackCodepoint(run[i].code)) {
                    val start = i
                    while (i < run.length && needsFallbackCodepoint(run[i].code)) i++
                    addStyle(SpanStyle(fontFamily = fb), start, i)
                } else {
                    i++
                }
            }
        }
    }

    /** Substring [start,end) of [text]; end == -1 (or past the end) means "to the end". */
    private fun slice(text: String, start: Int, end: Int): String {
        val s = start.coerceIn(0, text.length)
        val e = if (end == -1 || end > text.length) text.length else end.coerceIn(s, text.length)
        return text.substring(s, e)
    }

    /** Draw a single text run at the baseline `(x, y)` (upstream `drawTextRun`). */
    /** Draws the run; returns true iff something was actually painted (false for an empty slice). */
    fun drawTextRun(canvas: Canvas, text: String, start: Int, end: Int, x: Float, y: Float, rtl: Boolean): Boolean {
        val run = slice(text, start, end)
        if (run.isEmpty()) return false
        val result = measurer.measure(
            text = styledRun(run),
            style = currentStyle(),
            layoutDirection = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        )
        // CMP paints from the top-left; shift so the run sits on the baseline at (x, y).
        canvas.save()
        canvas.translate(x, y - result.firstBaseline)
        TextPainter.paint(canvas, result)
        canvas.restore()
        return true
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
        val result = measurer.measure(styledRun(run), style = currentStyle())
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
            text = styledRun(run),
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
    ): Boolean {
        if (text.isEmpty() || font.glyphs.isEmpty()) return false
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
        return true
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

        /**
         * REM-110: symbol codepoints that appear in genuine `DATA_TEXT` strings across the 173-doc
         * corpus and that the CMP **default** web font lacks (→ tofu on wasm; Android/iOS fall back via
         * the system font). Grounded by decoding every `DATA_TEXT` op in the corpus:
         * ♥ U+2665 (heart_rate_timeline) · ❤ U+2764 (impulse_demo_hearts_demo, spline_demo) ·
         * ⚡ U+26A1 (battery_radial_gauge) · ⬩ U+2B29 (hydration_wave) · ▲ U+25B2 (stock_sparkline) ·
         * ↑ U+2191 / ↓ U+2193 (pressure_gauge, stock). The Latin-1 glyphs (° ² · U+00B0/B2/B7) and the
         * bullet (• U+2022) are left to the default font (it has them); the bundled fallback still
         * carries • as a safety net but does not trigger on it (its only run "BTC • 13:3" is mixed).
         * Every run that DOES trigger is pure-symbol, so the per-run family swap never touches Latin.
         */
        val SYMBOL_FALLBACK_CODEPOINTS: Set<Int> =
            setOf(0x2191, 0x2193, 0x25B2, 0x2665, 0x26A1, 0x2764, 0x2B29)

        /**
         * REM-106 — the **generalized** fallback trigger: any codepoint in the Unicode symbol blocks the
         * bundled `rc_symbol_fallback` family covers (Arrows · Geometric Shapes · Misc Symbols · Dingbats ·
         * Misc Symbols & Arrows). A strict superset of the REM-110 corpus census
         * [SYMBOL_FALLBACK_CODEPOINTS] (kept as a pinned drift-guard), so **arbitrary** foreign symbol
         * content — not only the 7 corpus glyphs — routes to the fallback instead of rendering tofu/blank on
         * web. Deliberately EXCLUDES Latin-1 (° ² ·), General Punctuation (• U+2022) and ASCII (all
         * default-font-covered) so a Latin-bearing segment is never pulled onto the symbol font. BMP-only by
         * design: every range is below U+D800, so surrogate pairs never match → astral-plane emoji stay on
         * the base font (broadening the bundled fallback to more scripts/planes — CJK, Cyrillic, emoji — is
         * a tracked FC follow-up, not this base increment).
         */
        fun needsFallbackCodepoint(cp: Int): Boolean =
            cp in 0x2190..0x21FF ||  // Arrows (↑ U+2191, ↓ U+2193)
                cp in 0x25A0..0x25FF ||  // Geometric Shapes (▲ U+25B2)
                cp in 0x2600..0x26FF ||  // Miscellaneous Symbols (♥ U+2665, ⚡ U+26A1)
                cp in 0x2700..0x27BF ||  // Dingbats (❤ U+2764)
                cp in 0x2B00..0x2BFF     // Misc Symbols and Arrows (⬩ U+2B29)

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
