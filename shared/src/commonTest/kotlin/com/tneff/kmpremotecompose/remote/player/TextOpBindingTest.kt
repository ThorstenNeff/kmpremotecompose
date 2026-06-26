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

import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeDocument
import com.tneff.kmpremotecompose.remote.core.operations.TextData
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawText
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawTextAnchored
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawTextOnPath
import com.tneff.kmpremotecompose.remote.core.operations.draw.PaintData
import com.tneff.kmpremotecompose.remote.player.core.ComputedTextLayout
import com.tneff.kmpremotecompose.remote.player.core.PaintContext
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-35 text op→paint binding — the render-evidence: the player walk now **dispatches** the text ops
 * (they were `: Operation`, so the walk skipped them and text never rendered). Headless via a recording
 * [PaintContext] (no Skiko); the real CMP rendering is covered in `ComposeTextRendererIosTest`.
 */
class TextOpBindingTest {

    /** Records text-primitive calls and serves fixed text bounds (width 20) for the anchor math. */
    private class RecordingTextContext(context: RemoteContext) : PaintContext(context) {
        val log = mutableListOf<String>()
        override fun getTextBounds(textId: Int, start: Int, end: Int, flags: Int, bounds: FloatArray) {
            bounds[0] = 0f; bounds[1] = -8f; bounds[2] = 20f; bounds[3] = 4f
        }
        override fun drawTextRun(textId: Int, start: Int, end: Int, contextStart: Int, contextEnd: Int, x: Float, y: Float, rtl: Boolean) {
            log += "run(id=$textId,start=$start,end=$end,ctx=[$contextStart,$contextEnd],x=$x,y=$y,rtl=$rtl)"
        }
        override fun drawTextOnPath(textId: Int, pathId: Int, hOffset: Float, vOffset: Float) {
            log += "onPath(id=$textId,path=$pathId,h=$hOffset,v=$vOffset)"
        }
        // unused primitives: no-op
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

    @Test
    fun dataText_populatesContext_andDrawText_dispatchesRun() {
        val context = RemoteContext()
        val paint = RecordingTextContext(context)
        val doc = RemoteComposeDocument(listOf(
            TextData(5, "Hi"),                       // DATA_TEXT → context (the missing prerequisite)
            DrawText(5, 0, -1, 0, 1, 10f, 20f, rtl = false),
        ))

        RemoteComposePlayer(context).paint(doc, paint)

        assertEquals("Hi", context.getText(5), "DATA_TEXT must populate the context during the walk")
        assertEquals(listOf("run(id=5,start=0,end=-1,ctx=[0,1],x=10.0,y=20.0,rtl=false)"), paint.log)
    }

    @Test
    fun drawTextOnPath_dispatches_withPrimitiveArgOrder() {
        val context = RemoteContext()
        val paint = RecordingTextContext(context)
        // op wire order is (vOffset, hOffset); the primitive takes (hOffset, vOffset).
        val doc = RemoteComposeDocument(listOf(DrawTextOnPath(textId = 7, pathId = 9, vOffset = 2f, hOffset = 3f)))

        RemoteComposePlayer(context).paint(doc, paint)

        assertEquals(listOf("onPath(id=7,path=9,h=3.0,v=2.0)"), paint.log)
    }

    @Test
    fun drawTextAnchored_centersHorizontally_viaMeasure() {
        val context = RemoteContext()
        val paint = RecordingTextContext(context)
        // panX=0 (centered), panY=NaN (keep y). bounds width=20 → hOffset=-10 → x=100-10=90, y=50.
        val doc = RemoteComposeDocument(listOf(
            DrawTextAnchored(textId = 3, x = 100f, y = 50f, panX = 0f, panY = Float.NaN, flags = 0),
        ))

        RemoteComposePlayer(context).paint(doc, paint)

        assertEquals(listOf("run(id=3,start=0,end=-1,ctx=[0,1],x=90.0,y=50.0,rtl=false)"), paint.log)
    }
}
