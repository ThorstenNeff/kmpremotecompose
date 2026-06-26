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
import com.tneff.kmpremotecompose.remote.core.operations.draw.PaintData
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext

/**
 * The geometry half of the CMP paint adapter (L2-S2, **owned by dev-2**) — the PO's delegate seam
 * (2026-06-26): rather than grafting dev-2's overrides into [ComposePaintContext] (collision under
 * parallel work), dev-2 implements this interface as a standalone component in its own files and the
 * adapter delegates the non-text primitives to it. Conflict-free, both halves independently testable
 * — same group-separation principle as Layer 1.
 *
 * Signatures mirror the geometry/paint/matrix/clip/bitmap/path subset of
 * [com.tneff.kmpremotecompose.remote.player.core.PaintContext] verbatim, so the adapter forwards 1:1.
 * The CMP internals behind these — `FloatsToPath` (FloatArray→`Path`), the [PaintData]→`Paint`/`Brush`
 * mapping, the path-command constants (MOVE/LINE/QUADRATIC/CONIC/CUBIC/CLOSE/DONE), `Path.op`/
 * `PathEffect`/`PathMeasure` (Skiko) — all live in dev-2's implementation, **not here** (PO scope-split).
 *
 * The implementation is constructed with the target [Canvas] and the [RemoteContext] (to resolve
 * id-referenced paths/bitmaps via [RemoteContext.getPath]/[RemoteContext.getBitmap]/etc.).
 */
interface GeometryDelegate {

    // shapes
    fun drawRect(left: Float, top: Float, right: Float, bottom: Float)
    fun drawCircle(centerX: Float, centerY: Float, radius: Float)
    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float)
    fun drawOval(left: Float, top: Float, right: Float, bottom: Float)
    fun drawArc(left: Float, top: Float, right: Float, bottom: Float, startAngle: Float, sweepAngle: Float)
    fun drawSector(left: Float, top: Float, right: Float, bottom: Float, startAngle: Float, sweepAngle: Float)
    fun drawRoundRect(left: Float, top: Float, right: Float, bottom: Float, radiusX: Float, radiusY: Float)

    // bitmaps
    fun drawBitmap(id: Int, left: Float, top: Float, right: Float, bottom: Float)
    fun drawBitmap(
        imageId: Int,
        srcLeft: Int, srcTop: Int, srcRight: Int, srcBottom: Int,
        dstLeft: Int, dstTop: Int, dstRight: Int, dstBottom: Int,
        cdId: Int,
    )

    // path
    fun drawPath(id: Int, start: Float, end: Float)
    fun combinePath(out: Int, path1: Int, path2: Int, operation: Byte)
    fun tweenPath(out: Int, path1: Int, path2: Int, tween: Float)
    fun drawTweenPath(path1Id: Int, path2Id: Int, tween: Float, start: Float, end: Float)

    // matrix
    fun matrixSave()
    fun matrixRestore()
    fun matrixScale(scaleX: Float, scaleY: Float, centerX: Float, centerY: Float)
    fun matrixTranslate(translateX: Float, translateY: Float)
    fun matrixSkew(skewX: Float, skewY: Float)
    fun matrixRotate(rotate: Float, pivotX: Float, pivotY: Float)
    fun matrixFromPath(pathId: Int, fraction: Float, vOffset: Float, flags: Int)
    fun scale(scaleX: Float, scaleY: Float)
    fun translate(translateX: Float, translateY: Float)

    // clip
    fun clipRect(left: Float, top: Float, right: Float, bottom: Float)
    fun clipPath(pathId: Int, regionOp: Int)
    fun roundedClipRect(
        width: Float, height: Float,
        topStart: Float, topEnd: Float, bottomStart: Float, bottomEnd: Float,
    )

    // paint
    fun savePaint()
    fun restorePaint()
    fun applyPaint(paint: PaintData)

    // graphics layer (GAP-4, deferred L2-D2 — dev-2)
    fun startGraphicsLayer(w: Int, h: Int)
    fun setGraphicsLayer(attributes: Map<Int, Any?>)
    fun endGraphicsLayer()

    // misc
    fun reset()
    fun drawToBitmap(bitmapId: Int, mode: Int, color: Int)
}
