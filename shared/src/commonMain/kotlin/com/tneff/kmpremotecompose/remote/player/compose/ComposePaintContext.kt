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
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.text.font.FontFamily
import com.tneff.kmpremotecompose.remote.core.operations.BitmapFontData
import com.tneff.kmpremotecompose.remote.core.operations.draw.PaintData
import com.tneff.kmpremotecompose.remote.player.core.ComputedTextLayout
import com.tneff.kmpremotecompose.remote.player.core.PaintContext
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.math.PI
import kotlin.math.atan2

/**
 * The single Compose-Multiplatform paint adapter (L2-S1 scaffold) — **Android and iOS from one
 * adapter**, because CMP renders both via the same `androidx.compose.ui.graphics` API (iOS = Skiko/Skia,
 * TECHSPEC §2/§3). Its iOS compile is the S1 gate: it proves the contract maps onto CMP graphics on
 * Kotlin/Native.
 *
 * Two halves, plugged in via the PO's delegate seam (no grafting):
 *  - **Geometry** (shapes/bitmap/matrix/clip/paint/path/graphics-layer/misc) is forwarded to a
 *    [GeometryDelegate] — dev-2's L2-S2 component in its own files. Null until set ⇒ those primitives
 *    are no-ops, so the walk runs against the scaffold today and lights up as dev-2 lands S2.
 *  - **Text** (drawTextRun/drawComplexText/getTextBounds/layoutComplexText/…) is dev-1's L2-S3 work,
 *    implemented directly on this adapter against [canvas]; S1 leaves it as documented no-ops.
 *
 * [getText] resolves from the [RemoteContext] id store today (no platform work needed).
 */
class ComposePaintContext(
    context: RemoteContext,
    /**
     * The CMP canvas the **text** half (L2-S3, dev-1) draws into. Late-bound: null in the S1 scaffold
     * (text is a no-op until S3 wires it). The geometry half does not read this — dev-2's
     * [GeometryDelegate] carries its own canvas reference.
     */
    val canvas: Canvas? = null,
    /** dev-2's geometry implementation (L2-S2). When null, geometry primitives are no-ops. */
    var geometry: GeometryDelegate? = null,
    /**
     * The CMP font resolver for the **text** half — supply `LocalFontFamilyResolver.current` from the
     * composition. Injected (not constructed) because Android's `createFontFamilyResolver()` needs a
     * resource loader / `Context`. Null ⇒ text draws are no-ops (geometry-only / scaffold usage).
     */
    val fontFamilyResolver: FontFamily.Resolver? = null,
    /**
     * REM-110: bundled symbol-fallback [FontFamily] (♥/❤/⚡/⬩/▲/↑/↓) for the text half — forwarded to
     * [ComposeTextRenderer] so web text resolves these glyphs the default font lacks. Null ⇒ no fallback.
     */
    val symbolFallbackFamily: FontFamily? = null,
    /**
     * REM-158: the cross-target name/enum → [FontFamily] resolver for the `TYPEFACE` op, forwarded to
     * [ComposeTextRenderer]. Built from `composeResources/font` in the composition (like
     * [symbolFallbackFamily]). Null ⇒ no named-font resolution (text uses the renderer default).
     */
    val namedFontResolver: NamedFontResolver? = null,
) : PaintContext(context) {

    /**
     * The single shared paint state (REM-32, Flag-2): construct the [geometry] delegate with this same
     * instance (`GeometryPaintDelegate(context, canvas, paintState)`) so that `PAINT_VALUES` and
     * `savePaint`/`restorePaint` (forwarded to geometry) and the text renderer below all see one state.
     * The text half (L2-S3, dev-1) reads [PlayerPaintState.textSizePx] / [PlayerPaintState.typefaceId]
     * / [PlayerPaintState.paint] from here.
     *
     * Lazy: a `Paint` needs the graphics backend (Skiko), absent in plain jvm unit tests — deferring
     * creation until first render keeps the canvas-free seam tests (e.g. PlayerFoundationTest) working.
     */
    val paintState: PlayerPaintState by lazy { PlayerPaintState() }

    // --- geometry: forwarded to dev-2's delegate (L2-S2) ----------------------------------------

    override fun drawRect(left: Float, top: Float, right: Float, bottom: Float) {
        geometry?.drawRect(left, top, right, bottom)
    }

    override fun drawCircle(centerX: Float, centerY: Float, radius: Float) {
        geometry?.drawCircle(centerX, centerY, radius)
    }

    override fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float) {
        geometry?.drawLine(x1, y1, x2, y2)
    }

    override fun drawOval(left: Float, top: Float, right: Float, bottom: Float) {
        geometry?.drawOval(left, top, right, bottom)
    }

    override fun drawArc(
        left: Float, top: Float, right: Float, bottom: Float, startAngle: Float, sweepAngle: Float,
    ) {
        geometry?.drawArc(left, top, right, bottom, startAngle, sweepAngle)
    }

    override fun drawSector(
        left: Float, top: Float, right: Float, bottom: Float, startAngle: Float, sweepAngle: Float,
    ) {
        geometry?.drawSector(left, top, right, bottom, startAngle, sweepAngle)
    }

    override fun drawRoundRect(
        left: Float, top: Float, right: Float, bottom: Float, radiusX: Float, radiusY: Float,
    ) {
        geometry?.drawRoundRect(left, top, right, bottom, radiusX, radiusY)
    }

    override fun drawBitmap(id: Int, left: Float, top: Float, right: Float, bottom: Float) {
        geometry?.drawBitmap(id, left, top, right, bottom)
    }

    override fun drawBitmap(
        imageId: Int,
        srcLeft: Int, srcTop: Int, srcRight: Int, srcBottom: Int,
        dstLeft: Int, dstTop: Int, dstRight: Int, dstBottom: Int,
        cdId: Int,
    ) {
        geometry?.drawBitmap(
            imageId, srcLeft, srcTop, srcRight, srcBottom, dstLeft, dstTop, dstRight, dstBottom, cdId,
        )
    }

    override fun drawPath(id: Int, start: Float, end: Float) {
        geometry?.drawPath(id, start, end)
    }

    override fun combinePath(out: Int, path1: Int, path2: Int, operation: Byte) {
        geometry?.combinePath(out, path1, path2, operation)
    }

    override fun tweenPath(out: Int, path1: Int, path2: Int, tween: Float) {
        geometry?.tweenPath(out, path1, path2, tween)
    }

    override fun drawTweenPath(path1Id: Int, path2Id: Int, tween: Float, start: Float, end: Float) {
        geometry?.drawTweenPath(path1Id, path2Id, tween, start, end)
    }

    override fun matrixSave() { geometry?.matrixSave() }

    override fun matrixRestore() { geometry?.matrixRestore() }

    override fun matrixScale(scaleX: Float, scaleY: Float, centerX: Float, centerY: Float) {
        geometry?.matrixScale(scaleX, scaleY, centerX, centerY)
    }

    override fun matrixTranslate(translateX: Float, translateY: Float) {
        geometry?.matrixTranslate(translateX, translateY)
    }

    override fun matrixSkew(skewX: Float, skewY: Float) { geometry?.matrixSkew(skewX, skewY) }

    override fun matrixRotate(rotate: Float, pivotX: Float, pivotY: Float) {
        geometry?.matrixRotate(rotate, pivotX, pivotY)
    }

    override fun matrixFromPath(pathId: Int, fraction: Float, vOffset: Float, flags: Int) {
        geometry?.matrixFromPath(pathId, fraction, vOffset, flags)
    }

    override fun scale(scaleX: Float, scaleY: Float) { geometry?.scale(scaleX, scaleY) }

    override fun translate(translateX: Float, translateY: Float) {
        geometry?.translate(translateX, translateY)
    }

    override fun clipRect(left: Float, top: Float, right: Float, bottom: Float) {
        geometry?.clipRect(left, top, right, bottom)
    }

    override fun clipPath(pathId: Int, regionOp: Int) { geometry?.clipPath(pathId, regionOp) }

    override fun roundedClipRect(
        width: Float, height: Float,
        topStart: Float, topEnd: Float, bottomStart: Float, bottomEnd: Float,
    ) {
        geometry?.roundedClipRect(width, height, topStart, topEnd, bottomStart, bottomEnd)
    }

    override fun savePaint() { geometry?.savePaint() }

    override fun restorePaint() { geometry?.restorePaint() }

    override fun applyPaint(paint: PaintData) { geometry?.applyPaint(paint) }

    /**
     * **REM-157 Phase-A font-state preroll** — apply only the font-affecting tags
     * (TEXT_SIZE / TYPEFACE / FONT_STYLE / FONT_WEIGHT side effects via `applyTo`) of the bundle
     * onto the shared [paintState], so a subsequent Phase-A `TEXT_MEASURE.apply()` (which routes
     * through `getTextBounds` → `currentStyle()` → reads `paintState.textSizePx`) measures at the
     * font state the corresponding text will render with in Phase-B. Non-font fields stay untouched
     * here and are re-applied in full by Phase-B's [applyPaint]. Routes via [PaintBundleApplier]
     * with the real [paintState] receiving only the font-field mutations.
     */
    override fun applyFontStateForMeasure(values: IntArray) {
        PaintBundleApplier.applyFontStateOnly(context, paintState, values)
    }

    override fun startGraphicsLayer(w: Int, h: Int) { geometry?.startGraphicsLayer(w, h) }

    override fun setGraphicsLayer(attributes: Map<Int, Any?>) { geometry?.setGraphicsLayer(attributes) }

    override fun endGraphicsLayer() { geometry?.endGraphicsLayer() }

    override fun reset() { geometry?.reset() }

    override fun drawToBitmap(bitmapId: Int, mode: Int, color: Int) {
        geometry?.drawToBitmap(bitmapId, mode, color)
    }

    // --- text: dev-1 L2-S3 basis (CMP TextMeasurer/Paragraph via [textRenderer], → [canvas]) -----

    /**
     * The CMP text engine, built lazily from the pass density + injected [fontFamilyResolver], bound to
     * the shared [paintState] (dev-2's single instance above) — so a `PAINT_VALUES` bundle that sets
     * `TEXT_SIZE` and the geometry color reach the text renderer. **Null when no [fontFamilyResolver]
     * was supplied** (geometry-only / scaffold usage) ⇒ text draws are no-ops. The text half also needs
     * the [canvas].
     */
    val textRenderer: ComposeTextRenderer? by lazy {
        fontFamilyResolver?.let {
            ComposeTextRenderer(context.density, it, symbolFallbackFamily, namedFontResolver)
                .also { r -> r.paintState = paintState }
        }
    }

    override fun drawTextRun(
        textId: Int,
        start: Int, end: Int,
        contextStart: Int, contextEnd: Int,
        x: Float, y: Float,
        rtl: Boolean,
    ) {
        val c = canvas ?: return
        val r = textRenderer ?: return
        val text = context.getText(textId) ?: return
        // REM-120: text/glyph draws are real pixel-emitting primitives → bump the honest-render counter
        // (geometry does this in GeometryPaintDelegate.emit). Without it a text-only doc (e.g. the ❤ in
        // spline_demo, DrawTextAnchored→drawTextRun) draws but the gate falsely reports "rendered empty".
        // Guard on the renderer's drew-result so an empty slice (start==end) counts nothing (assist nit).
        if (r.drawTextRun(c, text, start, end, x, y, rtl)) context.incrementDrawCount()
    }

    /**
     * REM-45 — draw text along a path (`DRAW_TEXT_ON_PATH`). Upstream defers to Android's native
     * `Canvas.drawTextOnPath(text, path, hOffset, vOffset, paint)`; CMP has no such primitive, so we
     * replicate it per glyph via [PathMeasure] (the same Skiko path API the geometry adapter already uses
     * for `matrixFromPath` / stamped path effects):
     *
     * - [hOffset] is the start distance **along** the path; each glyph advances the pen by its measured
     *   width, and is placed with its horizontal **centre** on the path at `pen + w/2` (Android advances
     *   glyph-by-glyph along the curve). The glyph is rotated to the path **tangent** at that point.
     * - [vOffset] is the perpendicular offset from the path (the glyph baseline in the rotated frame).
     * - A glyph whose centre falls outside `[0, pathLength]` is skipped (text running past the path end is
     *   not drawn — Android clips it), so an `hOffset` past the end yields nothing rather than wrapping.
     *
     * NaN-id offsets are already resolved upstream (`DrawTextOnPath.updateVariables` → `mOut*`), so the
     * floats arriving here are concrete. **Parity is approximate (flagged, GAP-class):** exact per-glyph
     * hinting/spacing vs Android's native shaper can sub-pixel diverge; the render-sweep oracle gates the
     * visual result. Surrogate pairs are kept intact (advanced as one cluster). No `.rc`/wire touch.
     */
    override fun drawTextOnPath(textId: Int, pathId: Int, hOffset: Float, vOffset: Float) {
        val c = canvas ?: return
        val r = textRenderer ?: return
        val text = context.getText(textId) ?: return
        if (text.isEmpty()) return
        val path = PathGeometry.buildPath(context, pathId, 0f, 1f, null)
        if (path.isEmpty) return
        val measure = PathMeasure().apply { setPath(path, false) }
        val len = measure.length
        if (len <= 0f) return

        val bounds = FloatArray(4)
        var pen = hOffset
        var drew = false
        var i = 0
        while (i < text.length) {
            // Keep a surrogate pair together as one glyph cluster (BMP chars advance by one).
            val clusterLen =
                if (text[i].isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) 2 else 1
            val glyph = text.substring(i, i + clusterLen)
            r.getTextBounds(glyph, 0, -1, 0, bounds)
            val w = bounds[2]
            val mid = pen + w / 2f
            if (mid in 0f..len) {
                val pos = measure.getPosition(mid)
                val tangent = measure.getTangent(mid)
                val angleDegrees = (atan2(tangent.y, tangent.x) * 180.0 / PI).toFloat()
                c.save()
                c.translate(pos.x, pos.y)
                c.rotate(angleDegrees)
                // Centre the glyph on the path point (x = -w/2) and offset perpendicular by vOffset.
                if (r.drawTextRun(c, glyph, 0, -1, -w / 2f, vOffset, rtl = false)) drew = true
                c.restore()
            }
            pen += w
            i += clusterLen
        }
        // REM-120: one text-on-path primitive that emitted at least one glyph = one honest draw (mirrors
        // drawTextRun/geometry emit). A path/text that produced nothing counts zero.
        if (drew) context.incrementDrawCount()
    }

    /**
     * REM-156 — single-line draw at baseline (x,y) clamped to [maxWidth]: ellipsize (`…`) instead of
     * letting long anchored text run off the surface edge. Measures arbitrary `prefix + …` strings via
     * the existing public [ComposeTextRenderer] surface — **no change to that shared text seam**. When the
     * full run already fits [maxWidth] the draw is identical to plain [drawTextRun] (golden-safe: only
     * genuinely overflowing text changes). The largest fitting prefix is found by binary search and kept
     * surrogate-pair-safe; the ellipsis is appended (LTR; RTL leading-ellipsis is a flagged follow-up).
     */
    override fun drawTextRunClipped(
        textId: Int,
        start: Int, end: Int,
        x: Float, y: Float,
        rtl: Boolean,
        maxWidth: Float,
        leadingEllipsis: Boolean,
    ) {
        val c = canvas ?: return
        val r = textRenderer ?: return
        val text = context.getText(textId) ?: return
        if (text.isEmpty() || maxWidth <= 0f) return
        val bounds = FloatArray(4)
        fun width(s: String): Float { r.getTextBounds(s, 0, -1, 0, bounds); return bounds[2] - bounds[0] }

        // Fits as-is → identical to the plain path (no golden shift for non-overflowing text).
        if (width(text) <= maxWidth) {
            if (r.drawTextRun(c, text, 0, -1, x, y, rtl)) context.incrementDrawCount()
            return
        }
        val ellipsis = "…"
        if (width(ellipsis) > maxWidth) return // not even "…" fits → draw nothing (clipped away)

        val truncated = if (leadingEllipsis) {
            // REM-160: keep the END of the string (left/centered/RTL overflow) — `… + suffix(s)`. Find the
            // smallest cut s such that the result fits maxWidth (width shrinks as s grows; binary search).
            var lo = 0
            var hi = text.length
            while (lo < hi) {
                val mid = (lo + hi) / 2
                if (width(ellipsis + text.substring(surrogateSafeFwd(text, mid))) <= maxWidth) hi = mid else lo = mid + 1
            }
            ellipsis + text.substring(surrogateSafeFwd(text, lo))
        } else {
            // REM-156: keep the START (right overflow) — `prefix(n) + …`. Largest prefix that fits.
            var lo = 0
            var hi = text.length
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (width(text.substring(0, surrogateSafe(text, mid)) + ellipsis) <= maxWidth) lo = mid else hi = mid - 1
            }
            text.substring(0, surrogateSafe(text, lo)) + ellipsis
        }
        if (r.drawTextRun(c, truncated, 0, -1, x, y, rtl)) context.incrementDrawCount()
    }

    /** Don't cut a surrogate pair: if index [n] would split one, step back to before the high surrogate. */
    private fun surrogateSafe(text: String, n: Int): Int =
        if (n in 1..text.length && text[n - 1].isHighSurrogate()) n - 1 else n

    /** Forward variant: if cut [n] lands on a low surrogate, step forward past it (don't split a pair). */
    private fun surrogateSafeFwd(text: String, n: Int): Int =
        if (n in 0 until text.length && text[n].isLowSurrogate()) n + 1 else n

    override fun drawBitmapFontText(
        textId: Int,
        bitmapFontId: Int,
        start: Int, end: Int,
        x: Float, y: Float,
        glyphSpacing: Float,
    ) {
        val c = canvas ?: return
        val r = textRenderer ?: return
        val text = context.getText(textId) ?: return
        val font = context.getFromId(bitmapFontId) as? BitmapFontData ?: return
        val from = start.coerceIn(0, text.length)
        val to = if (end < 0 || end > text.length) text.length else end.coerceIn(from, text.length)
        // REM-120: glyph (bitmap-font) text counts too — guarded on the drew-result (empty substring = no count).
        if (r.drawBitmapFontText(c, font, text.substring(from, to), x, y, glyphSpacing) { context.getBitmap(it) }) {
            context.incrementDrawCount()
        }
    }

    override fun getTextBounds(textId: Int, start: Int, end: Int, flags: Int, bounds: FloatArray) {
        val r = textRenderer ?: return
        val text = context.getText(textId) ?: return
        r.getTextBounds(text, start, end, flags, bounds)
    }

    /** REM-37: stash the component's font style/weight in the shared paint state (read by the renderer). */
    override fun applyTextStyle(fontStyle: Int, fontWeight: Int) {
        paintState.fontStyle = fontStyle
        paintState.fontWeight = fontWeight
    }

    override fun layoutComplexText(
        textId: Int,
        start: Int, end: Int,
        alignment: Int,
        overflow: Int,
        maxLines: Int,
        maxWidth: Float, maxHeight: Float,
        letterSpacing: Float,
        lineHeightAdd: Float, lineHeightMultiplier: Float,
        lineBreakStrategy: Int, // approximated → LineBreak preset (D1); see TEXT_PARAMETER_PARITY
        hyphenationFrequency: Int, // unsupported → D1
        justificationMode: Int, // unsupported → D1
        useUnderline: Boolean,
        strikethrough: Boolean,
        flags: Int,
    ): ComputedTextLayout? = textRenderer?.layoutComplexText(
        context.getText(textId), start, end, alignment, overflow, maxLines, maxWidth, maxHeight,
        letterSpacing, lineHeightAdd, lineHeightMultiplier, useUnderline, strikethrough,
    )

    override fun drawComplexText(computedTextLayout: ComputedTextLayout?) {
        val c = canvas ?: return
        val r = textRenderer ?: return
        if (computedTextLayout == null) return // nothing laid out → no draw, no count
        r.drawComplexText(c, computedTextLayout)
        context.incrementDrawCount() // REM-120: multi-line/complex text counts too
    }

    override fun getText(id: Int): String? = context.getText(id)
}
