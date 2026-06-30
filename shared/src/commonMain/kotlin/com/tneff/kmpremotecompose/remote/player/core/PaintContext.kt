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
package com.tneff.kmpremotecompose.remote.player.core

import com.tneff.kmpremotecompose.remote.core.operations.draw.PaintData

/**
 * The platform-agnostic drawing surface a [PaintOperation] renders into (REM-30, L2-S1 — **the
 * contract**). This is the KMP port of upstream `PaintContext`: an abstract base whose ~40 drawing
 * primitives are implemented once per platform via Compose-Multiplatform graphics
 * ([com.tneff.kmpremotecompose.remote.player.compose.ComposePaintContext], Android **and** iOS).
 *
 * Split (PROJECT_CONTEXT §7, confirmed with PO):
 *  - **Concrete convenience methods** (save/restore, density, flags, version queries) live here and
 *    delegate to the [RemoteContext]. Subclasses inherit them unchanged.
 *  - **Abstract primitives** are the contract dev-2 (geometry, L2-S2) and dev-1 (text, L2-S3) fill.
 *    Field order / parameter shapes mirror upstream verbatim so the adapter maps 1:1.
 *
 * Coordinates are in **logical pixels**; density scaling is the player's job ([getDensity]).
 */
abstract class PaintContext(context: RemoteContext) {

    private var needsRepaintFlag: Boolean = false
    private var measureVersion: Int = 0

    /** The runtime state backing this paint pass (upstream `getContext()`). */
    var context: RemoteContext = context
        protected set

    // --- repaint / convenience (concrete; upstream-faithful) ------------------------------------

    fun doesNeedsRepaint(): Boolean = needsRepaintFlag

    fun clearNeedsRepaint() { needsRepaintFlag = false }

    /** Mark that the document wants another frame. */
    fun needsRepaint() { needsRepaintFlag = true }

    /** convenience: save the current matrix (upstream delegates to [matrixSave]). */
    fun save() = matrixSave()

    /** convenience: restore the current matrix (upstream delegates to [matrixRestore]). */
    fun restore() = matrixRestore()

    /** convenience layer save; upstream is a matrix save placeholder (real layer is GraphicsLayer). */
    fun saveLayer(x: Float, y: Float, width: Float, height: Float) = matrixSave()

    /** Ask the player to repaint in [seconds] (feeds [RemoteContext.wakeIn]). */
    fun wakeIn(seconds: Float) = context.wakeIn(seconds)

    fun isDebug(): Boolean = context.isBasicDebug()

    fun isAnimationEnabled(): Boolean = context.isAnimationEnabled()

    fun isVisualDebug(): Boolean = context.isVisualDebug()

    fun supportsVersion(major: Int, minor: Int, patch: Int): Boolean =
        context.supportsVersion(major, minor, patch)

    fun setMeasureVersion(measureVersion: Int) { this.measureVersion = measureVersion }

    fun getMeasureVersion(): Int = measureVersion

    fun useFeature(feature: Short): Boolean = context.useFeature(feature)

    /** Current player density (PROJECT_CONTEXT §5: evaluate-on-player, never hardcoded). */
    fun getDensity(): Float = context.density

    fun getDensityBehavior(): Int = context.densityBehavior

    // --- shapes (abstract; L2-S2 geometry) ------------------------------------------------------

    abstract fun drawRect(left: Float, top: Float, right: Float, bottom: Float)

    abstract fun drawCircle(centerX: Float, centerY: Float, radius: Float)

    abstract fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float)

    abstract fun drawOval(left: Float, top: Float, right: Float, bottom: Float)

    abstract fun drawArc(
        left: Float, top: Float, right: Float, bottom: Float, startAngle: Float, sweepAngle: Float,
    )

    abstract fun drawSector(
        left: Float, top: Float, right: Float, bottom: Float, startAngle: Float, sweepAngle: Float,
    )

    abstract fun drawRoundRect(
        left: Float, top: Float, right: Float, bottom: Float, radiusX: Float, radiusY: Float,
    )

    // --- bitmaps (abstract; L2-S2 geometry) -----------------------------------------------------

    /** Draw bitmap [id] into the dst rect (left,top,right,bottom). */
    abstract fun drawBitmap(id: Int, left: Float, top: Float, right: Float, bottom: Float)

    /** Draw a src-rect of bitmap [imageId] into a dst-rect, with content-description id [cdId]. */
    abstract fun drawBitmap(
        imageId: Int,
        srcLeft: Int, srcTop: Int, srcRight: Int, srcBottom: Int,
        dstLeft: Int, dstTop: Int, dstRight: Int, dstBottom: Int,
        cdId: Int,
    )

    // --- path (abstract; L2-S2 geometry — Skiko Path.op / PathMeasure) --------------------------

    abstract fun drawPath(id: Int, start: Float, end: Float)

    /** Boolean-combine path1/path2 into [out]. operation: 0=diff 1=intersect 2=revDiff 3=union 4=xor. */
    abstract fun combinePath(out: Int, path1: Int, path2: Int, operation: Byte)

    abstract fun tweenPath(out: Int, path1: Int, path2: Int, tween: Float)

    abstract fun drawTweenPath(path1Id: Int, path2Id: Int, tween: Float, start: Float, end: Float)

    // --- text (abstract; L2-S3 text — CMP TextMeasurer/Paragraph) -------------------------------

    abstract fun drawTextRun(
        textId: Int,
        start: Int, end: Int,
        contextStart: Int, contextEnd: Int,
        x: Float, y: Float,
        rtl: Boolean,
    )

    /**
     * REM-156 — draw a single-line text run at baseline `(x, y)` constrained to [maxWidth]: if the run is
     * wider than [maxWidth] it is truncated and ellipsized (`…`) instead of clipping edgelessly past the
     * surface. The **overflow policy** for `DRAW_TEXT_ANCHOR` (audit-P1: long anchored text ran off the
     * doc edge with no wrap/ellipsis/indicator). `[maxWidth] <= 0` ⇒ nothing fits → no draw.
     *
     * Default (this base / headless fakes / geometry-only contexts) = plain [drawTextRun] ignoring
     * [maxWidth], so no caller's behavior changes unless the rendering adapter overrides it. The real
     * ellipsizing implementation lives in the CMP adapter, where the resolved string + text engine are
     * available (it needs to measure arbitrary `prefix + …` strings, not just text-resource substrings).
     *
     * REM-160: [leadingEllipsis] truncates from the **front** (`… + suffix`, keeping the right edge) for
     * left-/centered-overflow (and RTL leading), vs the default trailing `prefix + …` (keeping the left
     * edge). The base default ignores it.
     */
    open fun drawTextRunClipped(
        textId: Int,
        start: Int, end: Int,
        x: Float, y: Float,
        rtl: Boolean,
        maxWidth: Float,
        leadingEllipsis: Boolean = false,
    ) {
        drawTextRun(textId, start, end, 0, 1, x, y, rtl)
    }

    abstract fun drawTextOnPath(textId: Int, pathId: Int, hOffset: Float, vOffset: Float)

    /** Draw the [start,end) run of text [textId] with bitmap font [bitmapFontId] at baseline (x,y). */
    abstract fun drawBitmapFontText(
        textId: Int,
        bitmapFontId: Int,
        start: Int, end: Int,
        x: Float, y: Float,
        glyphSpacing: Float,
    )

    /** Fill [bounds] (left,top,right,bottom) for the run; relative to a drawTextRun at x=0,y=0. */
    abstract fun getTextBounds(textId: Int, start: Int, end: Int, flags: Int, bounds: FloatArray)

    abstract fun layoutComplexText(
        textId: Int,
        start: Int, end: Int,
        alignment: Int,
        overflow: Int,
        maxLines: Int,
        maxWidth: Float, maxHeight: Float,
        letterSpacing: Float,
        lineHeightAdd: Float, lineHeightMultiplier: Float,
        lineBreakStrategy: Int,
        hyphenationFrequency: Int,
        justificationMode: Int,
        useUnderline: Boolean,
        strikethrough: Boolean,
        flags: Int,
    ): ComputedTextLayout?

    abstract fun drawComplexText(computedTextLayout: ComputedTextLayout?)

    /** The decoded string for [id], or null. */
    abstract fun getText(id: Int): String?

    // --- matrix (abstract; L2-S2 geometry) ------------------------------------------------------

    abstract fun matrixSave()

    abstract fun matrixRestore()

    abstract fun matrixScale(scaleX: Float, scaleY: Float, centerX: Float, centerY: Float)

    abstract fun matrixTranslate(translateX: Float, translateY: Float)

    abstract fun matrixSkew(skewX: Float, skewY: Float)

    abstract fun matrixRotate(rotate: Float, pivotX: Float, pivotY: Float)

    abstract fun matrixFromPath(pathId: Int, fraction: Float, vOffset: Float, flags: Int)

    /** Raw scale of subsequent commands (distinct from the centered [matrixScale]). */
    abstract fun scale(scaleX: Float, scaleY: Float)

    /** Raw translate of subsequent commands. */
    abstract fun translate(translateX: Float, translateY: Float)

    // --- clip (abstract; L2-S2 geometry) --------------------------------------------------------

    abstract fun clipRect(left: Float, top: Float, right: Float, bottom: Float)

    /** Clip to path [pathId] with the given region op (intersect/difference). */
    abstract fun clipPath(pathId: Int, regionOp: Int)

    abstract fun roundedClipRect(
        width: Float, height: Float,
        topStart: Float, topEnd: Float, bottomStart: Float, bottomEnd: Float,
    )

    // --- paint (abstract; L2-S2 geometry — PaintBundle→Paint/Brush mapping is dev-2's) ----------

    abstract fun savePaint()

    abstract fun restorePaint()

    /** Apply a bundle of paint mutations ([PaintData] = upstream `PaintBundle`). */
    abstract fun applyPaint(paint: PaintData)

    /**
     * Apply a component's text font style/weight to the shared paint state (REM-37 TextStyle) — read by
     * the text renderer. `fontStyle`: 0 = normal, 1 = italic; `fontWeight`: CSS 100–900 (0 = default).
     * Default no-op so non-text contexts (and the headless test fake) need no override.
     */
    open fun applyTextStyle(fontStyle: Int, fontWeight: Int) {}

    // --- graphics layer (abstract; GAP-4 — deferred L2-D2) --------------------------------------

    abstract fun startGraphicsLayer(w: Int, h: Int)

    /** Attribute map (alpha/scale/rotation/blur/…); keyed by upstream layer-attribute id. */
    abstract fun setGraphicsLayer(attributes: Map<Int, Any?>)

    abstract fun endGraphicsLayer()

    // --- misc (abstract; L2-S2 geometry) --------------------------------------------------------

    /** Reset the paint to defaults at the start of a pass. */
    abstract fun reset()

    /** Redirect drawing to bitmap [bitmapId] (0 = back to the main canvas). */
    abstract fun drawToBitmap(bitmapId: Int, mode: Int, color: Int)

    companion object {
        // Measure flags (upstream PaintContext constants), shared by getTextBounds/layoutComplexText.
        const val TEXT_MEASURE_MONOSPACE_WIDTH = 0x01
        const val TEXT_MEASURE_FONT_HEIGHT = 0x02
        const val TEXT_MEASURE_SPACES = 0x04
        const val TEXT_COMPLEX = 0x08
        const val TEXT_MEASURE_AUTOSIZE = 0x10
    }
}
