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

import com.tneff.kmpremotecompose.remote.core.operations.draw.PaintData
import com.tneff.kmpremotecompose.remote.player.core.ComputedTextLayout
import com.tneff.kmpremotecompose.remote.player.core.PaintContext
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext

/** A reusable no-op [PaintContext] for headless player tests that don't assert on draw primitives. */
open class NoOpPaintContext(context: RemoteContext) : PaintContext(context) {
    override fun drawRect(left: Float, top: Float, right: Float, bottom: Float) {}
    override fun drawCircle(centerX: Float, centerY: Float, radius: Float) {}
    override fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float) {}
    override fun drawOval(left: Float, top: Float, right: Float, bottom: Float) {}
    override fun drawArc(left: Float, top: Float, right: Float, bottom: Float, startAngle: Float, sweepAngle: Float) {}
    override fun drawSector(left: Float, top: Float, right: Float, bottom: Float, startAngle: Float, sweepAngle: Float) {}
    override fun drawRoundRect(left: Float, top: Float, right: Float, bottom: Float, radiusX: Float, radiusY: Float) {}
    override fun drawBitmap(id: Int, left: Float, top: Float, right: Float, bottom: Float) {}
    override fun drawBitmap(imageId: Int, srcLeft: Int, srcTop: Int, srcRight: Int, srcBottom: Int, dstLeft: Int, dstTop: Int, dstRight: Int, dstBottom: Int, cdId: Int) {}
    override fun drawPath(id: Int, start: Float, end: Float) {}
    override fun combinePath(out: Int, path1: Int, path2: Int, operation: Byte) {}
    override fun tweenPath(out: Int, path1: Int, path2: Int, tween: Float) {}
    override fun drawTweenPath(path1Id: Int, path2Id: Int, tween: Float, start: Float, end: Float) {}
    override fun drawTextRun(textId: Int, start: Int, end: Int, contextStart: Int, contextEnd: Int, x: Float, y: Float, rtl: Boolean) {}
    override fun drawTextOnPath(textId: Int, pathId: Int, hOffset: Float, vOffset: Float) {}
    override fun getTextBounds(textId: Int, start: Int, end: Int, flags: Int, bounds: FloatArray) {}
    override fun layoutComplexText(textId: Int, start: Int, end: Int, alignment: Int, overflow: Int, maxLines: Int, maxWidth: Float, maxHeight: Float, letterSpacing: Float, lineHeightAdd: Float, lineHeightMultiplier: Float, lineBreakStrategy: Int, hyphenationFrequency: Int, justificationMode: Int, useUnderline: Boolean, strikethrough: Boolean, flags: Int): ComputedTextLayout? = null
    override fun drawComplexText(computedTextLayout: ComputedTextLayout?) {}
    override fun getText(id: Int): String? = context.getText(id)
    override fun matrixSave() {}
    override fun matrixRestore() {}
    override fun matrixScale(scaleX: Float, scaleY: Float, centerX: Float, centerY: Float) {}
    override fun matrixTranslate(translateX: Float, translateY: Float) {}
    override fun matrixSkew(skewX: Float, skewY: Float) {}
    override fun matrixRotate(rotate: Float, pivotX: Float, pivotY: Float) {}
    override fun matrixFromPath(pathId: Int, fraction: Float, vOffset: Float, flags: Int) {}
    override fun scale(scaleX: Float, scaleY: Float) {}
    override fun translate(translateX: Float, translateY: Float) {}
    override fun clipRect(left: Float, top: Float, right: Float, bottom: Float) {}
    override fun clipPath(pathId: Int, regionOp: Int) {}
    override fun roundedClipRect(width: Float, height: Float, topStart: Float, topEnd: Float, bottomStart: Float, bottomEnd: Float) {}
    override fun savePaint() {}
    override fun restorePaint() {}
    override fun applyPaint(paint: PaintData) {}
    override fun startGraphicsLayer(w: Int, h: Int) {}
    override fun setGraphicsLayer(attributes: Map<Int, Any?>) {}
    override fun endGraphicsLayer() {}
    override fun reset() {}
    override fun drawToBitmap(bitmapId: Int, mode: Int, color: Int) {}
        override fun drawBitmapFontText(textId: Int, bitmapFontId: Int, start: Int, end: Int, x: Float, y: Float, glyphSpacing: Float) {}
}
