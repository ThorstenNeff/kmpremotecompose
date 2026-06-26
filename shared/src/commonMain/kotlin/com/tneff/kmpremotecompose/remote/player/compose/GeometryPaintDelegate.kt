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

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.tneff.kmpremotecompose.remote.core.operations.draw.PaintData
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.math.PI
import kotlin.math.atan2

/**
 * REM-31 (L2-S2 Geometrie-Adapter) — the geometry/paint half of the CMP paint adapter. Implements
 * dev-1's [GeometryDelegate] (PO's delegate seam, 2026-06-26): [ComposePaintContext] holds the
 * `PaintContext` surface + text (S3) and forwards the non-text primitives to this component
 * (`composePaintContext.geometry = GeometryPaintDelegate(context, canvas)`). Zero file overlap with S1/S3.
 *
 * Re-implemented from upstream `ComposePaintContext` against `androidx.compose.ui.graphics` — one impl
 * for Android **and** iOS (CMP-iOS = Skiko). No `java.*`; PROJECT_CONTEXT §5 honored.
 *
 * @param context the document render state ([RemoteContext]) — resolves id-referenced paths/bitmaps;
 *   built paths cache into its `pathCache` (distinct from raw path-data), so [PathGeometry.buildPath]
 *   never clobbers the float data.
 * @param canvas the target canvas; `var` because the adapter may redirect it (drawToBitmap / graphics
 *   layer — deferred, see below).
 */
internal class GeometryPaintDelegate(
    private val context: RemoteContext,
    var canvas: Canvas,
) : GeometryDelegate {

    /** Current paint; `var` so [reset] can swap a fresh instance (upstream behavior). */
    var paint: Paint = Paint()

    private val paintStack = ArrayDeque<Paint>()
    private val matrixStack = ArrayDeque<Matrix>().apply { addLast(Matrix()) }
    private val currentMatrix: Matrix get() = matrixStack.last()

    /** Paint-bundle tags encountered but outside S2 scope (text/shader/texture/path-effect/fill+stroke). */
    val deferredPaintTags: MutableSet<String> = mutableSetOf()

    // ---- non-matrix transforms (upstream scale/translate) ----

    override fun scale(scaleX: Float, scaleY: Float) {
        canvas.scale(scaleX, scaleY)
    }

    override fun translate(translateX: Float, translateY: Float) {
        canvas.translate(translateX, translateY)
        currentMatrix.translate(translateX, translateY)
    }

    // ---- shapes ----

    override fun drawRect(left: Float, top: Float, right: Float, bottom: Float) =
        canvas.drawRect(left, top, right, bottom, paint)

    override fun drawCircle(centerX: Float, centerY: Float, radius: Float) =
        canvas.drawCircle(Offset(centerX, centerY), radius, paint)

    override fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float) =
        canvas.drawLine(Offset(x1, y1), Offset(x2, y2), paint)

    override fun drawOval(left: Float, top: Float, right: Float, bottom: Float) =
        canvas.drawOval(left, top, right, bottom, paint)

    override fun drawRoundRect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radiusX: Float,
        radiusY: Float,
    ) = canvas.drawRoundRect(left, top, right, bottom, radiusX, radiusY, paint)

    override fun drawArc(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        startAngle: Float,
        sweepAngle: Float,
    ) = canvas.drawArc(left, top, right, bottom, startAngle, sweepAngle, useCenter = false, paint)

    override fun drawSector(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        startAngle: Float,
        sweepAngle: Float,
    ) = canvas.drawArc(left, top, right, bottom, startAngle, sweepAngle, useCenter = true, paint)

    // ---- bitmaps ----

    /** Draw the whole bitmap [id] into the destination rect. */
    override fun drawBitmap(id: Int, left: Float, top: Float, right: Float, bottom: Float) {
        val image = context.getBitmap(id) ?: return
        canvas.drawImageRect(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(left.toInt(), top.toInt()),
            dstSize = IntSize((right - left).toInt(), (bottom - top).toInt()),
            paint = paint,
        )
    }

    /** Draw a src sub-rect of bitmap [imageId] into a dst rect (the `cdId` content-desc is ignored here). */
    @Suppress("UNUSED_PARAMETER")
    override fun drawBitmap(
        imageId: Int,
        srcLeft: Int,
        srcTop: Int,
        srcRight: Int,
        srcBottom: Int,
        dstLeft: Int,
        dstTop: Int,
        dstRight: Int,
        dstBottom: Int,
        cdId: Int,
    ) {
        val image = context.getBitmap(imageId) ?: return
        canvas.drawImageRect(
            image = image,
            srcOffset = IntOffset(srcLeft, srcTop),
            srcSize = IntSize(srcRight - srcLeft, srcBottom - srcTop),
            dstOffset = IntOffset(dstLeft, dstTop),
            dstSize = IntSize(dstRight - dstLeft, dstBottom - dstTop),
            paint = paint,
        )
    }

    // ---- path geometry ----

    override fun drawPath(id: Int, start: Float, end: Float) =
        canvas.drawPath(PathGeometry.buildPath(context, id, start, end, deferredPaintTags), paint)

    override fun drawTweenPath(path1Id: Int, path2Id: Int, tween: Float, start: Float, end: Float) =
        canvas.drawPath(PathGeometry.buildTweenPath(context, path1Id, path2Id, tween, start, end), paint)

    /** Interpolate two paths' data and store the result under [out] (no draw). */
    override fun tweenPath(out: Int, path1: Int, path2: Int, tween: Float) {
        val d1 = context.getPathData(path1) ?: return
        val d2 = context.getPathData(path2) ?: return
        context.putPathData(out, PathGeometry.tweenPathData(d1, d2, tween))
    }

    /** Boolean-combine two cached paths and store the result under [out] (no draw). */
    override fun combinePath(out: Int, path1: Int, path2: Int, operation: Byte) {
        val p1 = PathGeometry.buildPath(context, path1, 0f, 1f, deferredPaintTags)
        val p2 = PathGeometry.buildPath(context, path2, 0f, 1f, deferredPaintTags)
        context.putPath(out, PathGeometry.combinePaths(p1, p2, operation))
    }

    // ---- paint ----

    override fun savePaint() {
        paintStack.addLast(paint.copyOf())
    }

    override fun restorePaint() {
        if (paintStack.isNotEmpty()) paint = paintStack.removeLast()
    }

    /** Apply a Layer-1 `PAINT_VALUES` bundle ([PaintData]) onto the current paint. */
    override fun applyPaint(paint: PaintData) {
        PaintBundleApplier.applyTo(this.paint, paint.values, deferred = deferredPaintTags)
    }

    override fun reset() {
        paint = Paint()
    }

    // ---- matrix ----

    override fun matrixSave() {
        canvas.save()
        matrixStack.addLast(Matrix(currentMatrix.values.copyOf()))
    }

    override fun matrixRestore() {
        canvas.restore()
        if (matrixStack.size > 1) matrixStack.removeLast()
    }

    override fun matrixTranslate(translateX: Float, translateY: Float) {
        canvas.translate(translateX, translateY)
        currentMatrix.translate(translateX, translateY)
    }

    override fun matrixScale(scaleX: Float, scaleY: Float, centerX: Float, centerY: Float) {
        if (centerX.isNaN()) {
            canvas.scale(scaleX, scaleY)
        } else {
            // CMP Canvas has no pivoted scale — emulate around (centerX, centerY).
            canvas.translate(centerX, centerY)
            canvas.scale(scaleX, scaleY)
            canvas.translate(-centerX, -centerY)
        }
    }

    override fun matrixRotate(rotate: Float, pivotX: Float, pivotY: Float) {
        if (pivotX.isNaN()) {
            canvas.rotate(rotate)
        } else {
            canvas.translate(pivotX, pivotY)
            canvas.rotate(rotate)
            canvas.translate(-pivotX, -pivotY)
        }
    }

    override fun matrixSkew(skewX: Float, skewY: Float) {
        canvas.skew(skewX, skewY)
    }

    /** Concat a matrix derived from a position (and optional tangent rotation) along path [pathId]. */
    override fun matrixFromPath(pathId: Int, fraction: Float, vOffset: Float, flags: Int) {
        val path = PathGeometry.buildPath(context, pathId, 0f, 1f, deferredPaintTags)
        if (path.isEmpty) return
        val measure = PathMeasure().apply { setPath(path, false) }
        val len = measure.length
        if (len == 0f) return
        val distance = (len * fraction) % len
        val pos = measure.getPosition(distance)
        val matrix = Matrix()
        matrix.translate(pos.x, pos.y)
        // flag bit 2 == Android PathMeasure.TANGENT_MATRIX_FLAG → rotate by the tangent angle.
        if ((flags and 2) != 0) {
            val tangent = measure.getTangent(distance)
            val angleDegrees = (atan2(tangent.y, tangent.x) * 180.0 / PI).toFloat()
            matrix.rotateZ(angleDegrees)
        }
        canvas.concat(matrix)
    }

    // ---- clip ----

    override fun clipRect(left: Float, top: Float, right: Float, bottom: Float) =
        canvas.clipRect(left, top, right, bottom, ClipOp.Intersect)

    /** Clip to a cached path; [regionOp] `1` (upstream `ClipPath.DIFFERENCE`) → difference, else intersect. */
    override fun clipPath(pathId: Int, regionOp: Int) {
        val path = PathGeometry.buildPath(context, pathId, 0f, 1f, deferredPaintTags)
        canvas.clipPath(path, if (regionOp == CLIP_DIFFERENCE) ClipOp.Difference else ClipOp.Intersect)
    }

    override fun roundedClipRect(
        width: Float,
        height: Float,
        topStart: Float,
        topEnd: Float,
        bottomStart: Float,
        bottomEnd: Float,
    ) {
        val roundRect = RoundRect(
            left = 0f,
            top = 0f,
            right = width,
            bottom = height,
            topLeftCornerRadius = CornerRadius(topStart, topStart),
            topRightCornerRadius = CornerRadius(topEnd, topEnd),
            bottomRightCornerRadius = CornerRadius(bottomEnd, bottomEnd),
            bottomLeftCornerRadius = CornerRadius(bottomStart, bottomStart),
        )
        val path = Path().apply { addRoundRect(roundRect) }
        canvas.clipPath(path, ClipOp.Intersect)
    }

    // ---- graphics layer / drawToBitmap: GAP-4, deferred to L2-D2 (dev-2) ----
    // 🚩 No-op stubs satisfying GeometryDelegate; docs using these render without the layer/bitmap-
    // redirect until D2. Recorded in deferredPaintTags so the gap is visible, not silent.

    @Suppress("UNUSED_PARAMETER")
    override fun startGraphicsLayer(w: Int, h: Int) { deferredPaintTags.add("GRAPHICS_LAYER") }

    @Suppress("UNUSED_PARAMETER")
    override fun setGraphicsLayer(attributes: Map<Int, Any?>) { deferredPaintTags.add("GRAPHICS_LAYER") }

    override fun endGraphicsLayer() { /* deferred (L2-D2) */ }

    @Suppress("UNUSED_PARAMETER")
    override fun drawToBitmap(bitmapId: Int, mode: Int, color: Int) { deferredPaintTags.add("DRAW_TO_BITMAP") }

    private companion object {
        const val CLIP_DIFFERENCE = 1
    }
}
