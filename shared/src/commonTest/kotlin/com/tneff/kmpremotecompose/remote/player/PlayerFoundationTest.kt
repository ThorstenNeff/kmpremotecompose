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
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.draw.PaintData
import com.tneff.kmpremotecompose.remote.player.compose.ComposePaintContext
import com.tneff.kmpremotecompose.remote.player.core.PaintContext
import com.tneff.kmpremotecompose.remote.player.core.PaintOperation
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.platformDensityProvider
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * REM-30 (L2-S1 foundation): the player op-walk dispatch seam, the [RemoteContext] id store, the
 * [PaintContext] convenience surface, the density `expect`/`actual`, and the [ComposePaintContext]
 * geometry-delegate seam. Behavioural cover via fakes; the CMP adapter's iOS *compile* is the gate.
 */
class PlayerFoundationTest {

    /** A no-op [PaintContext] that records which draw primitives the walk dispatched. */
    private class RecordingPaintContext(context: RemoteContext) : PaintContext(context) {
        val log = mutableListOf<String>()
        override fun reset() { log += "reset" }
        override fun drawCircle(centerX: Float, centerY: Float, radius: Float) { log += "circle($centerX,$centerY,$radius)" }
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float) { log += "rect" }
        // remaining primitives: irrelevant to these tests → no-op
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
        override fun layoutComplexText(textId: Int, start: Int, end: Int, alignment: Int, overflow: Int, maxLines: Int, maxWidth: Float, maxHeight: Float, letterSpacing: Float, lineHeightAdd: Float, lineHeightMultiplier: Float, lineBreakStrategy: Int, hyphenationFrequency: Int, justificationMode: Int, useUnderline: Boolean, strikethrough: Boolean, flags: Int) = null
        override fun drawComplexText(computedTextLayout: com.tneff.kmpremotecompose.remote.player.core.ComputedTextLayout?) {}
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
        override fun drawToBitmap(bitmapId: Int, mode: Int, color: Int) {}
    }

    /** A draw op stand-in: it is a [PaintOperation], so the walk must dispatch it. */
    private class FakeCircleOp(val r: Float) : PaintOperation {
        override val opcode: Int get() = 0
        override fun write(buffer: WireBuffer) {}
        override fun dump(): String = "FAKE_CIRCLE r=$r"
        override fun paint(context: RemoteContext, paint: PaintContext) = paint.drawCircle(0f, 0f, r)
    }

    /** A non-drawing op (data/header stand-in): the walk must skip it. */
    private class FakeDataOp : Operation {
        override val opcode: Int get() = 1
        override fun write(buffer: WireBuffer) {}
        override fun dump(): String = "FAKE_DATA"
    }

    @Test
    fun walk_dispatchesPaintOperationsInOrder_skipsNonPaintOps() {
        val context = RemoteContext()
        val paint = RecordingPaintContext(context)
        val doc = RemoteComposeDocument(listOf(FakeCircleOp(3f), FakeDataOp(), FakeCircleOp(7f)))

        val wake = RemoteComposePlayer(context).paint(doc, paint, frameTimeSeconds = 2.5f)

        // reset once at pass start, then only the two PaintOperations, in document order.
        assertEquals(listOf("reset", "circle(0.0,0.0,3.0)", "circle(0.0,0.0,7.0)"), paint.log)
        assertSame(paint, context.paintContext, "player must bind the paint context")
        assertEquals(-1f, wake, "no wake requested ⇒ -1")
        // frame-render ↔ time-source seam: the pass's frame time is the injected value, not hardcoded.
        assertEquals(2.5f, context.frameTimeSeconds)
    }

    @Test
    fun player_defaultFrameTimeIsZero_staticMvpFrame() {
        val context = RemoteContext()
        RemoteComposePlayer(context).paint(
            RemoteComposeDocument(emptyList()), RecordingPaintContext(context),
        )
        assertEquals(0f, context.frameTimeSeconds, "MVP default renders the static t=0 frame")
    }

    @Test
    fun remoteContext_stores_dataWindingAndTypedViews() {
        val context = RemoteContext()
        context.putText(10, "hello")
        context.putPathData(11, floatArrayOf(1f, 2f))
        context.putPathWinding(12, 1)

        assertEquals("hello", context.getText(10))
        assertTrue(context.containsId(10))
        assertEquals("hello", context.getFromId(10))

        // path raw data lives in the general registry (getFromId sees it); the path *cache* is separate.
        assertContentTrue(floatArrayOf(1f, 2f), context.getPathData(11))
        assertNull(context.getPath(11), "no built Path cached yet — separate store from the float[] data")

        // winding has its own store; absent ⇒ 0 (upstream IntIntMap default).
        assertEquals(1, context.getPathWinding(12))
        assertEquals(0, context.getPathWinding(99), "unset winding defaults to 0")

        assertFalse(context.containsId(99))
        assertNull(context.getFromId(99))
    }

    @Test
    fun paintContext_convenienceDelegatesToContext() {
        val context = RemoteContext().apply {
            setDensity(2.5f)
            densityBehavior = 1
            versionMajor = 7; versionMinor = 0; versionPatch = 0
        }
        val paint = RecordingPaintContext(context)

        assertEquals(2.5f, paint.getDensity())
        assertEquals(1, paint.getDensityBehavior())
        assertTrue(paint.supportsVersion(6, 9, 9), "api-7 doc supports a 6.x requirement")
        assertFalse(paint.supportsVersion(7, 1, 0), "api-7.0 doc does not support 7.1")

        paint.needsRepaint()
        assertTrue(paint.doesNeedsRepaint())
        paint.clearNeedsRepaint()
        assertFalse(paint.doesNeedsRepaint())
    }

    @Test
    fun densityProvider_platformValueIsPositive() {
        // Exercises the expect/actual on whatever target runs the test (incl. iOS sim).
        assertTrue(platformDensityProvider().density() > 0f)
    }

    @Test
    fun composeAdapter_geometrySeam_forwardsToDelegateAndNoopsWhenAbsent() {
        // No canvas needed: the geometry seam is canvas-independent (text canvas is L2-S3, late-bound).
        val context = RemoteContext()
        val adapter = ComposePaintContext(context)

        // No delegate yet (S2 not landed): geometry primitives must be safe no-ops.
        adapter.drawCircle(1f, 2f, 3f)
        adapter.reset()

        // Plug in dev-2's delegate seam with a recorder: the adapter forwards 1:1.
        val recorder = RecordingGeometryDelegate()
        adapter.geometry = recorder
        adapter.drawCircle(1f, 2f, 3f)
        adapter.drawRect(0f, 0f, 4f, 4f)
        assertEquals(listOf("circle(1.0,2.0,3.0)", "rect(0.0,0.0,4.0,4.0)"), recorder.log)

        // text/getText half resolves from the context store today.
        context.putText(5, "hi")
        assertEquals("hi", adapter.getText(5))
    }
}

/** Helper: assert a nullable FloatArray equals an expected one (kotlin.test has no array overload). */
private fun assertContentTrue(expected: FloatArray, actual: FloatArray?) {
    assertTrue(actual != null && actual.contentEquals(expected), "expected ${expected.toList()}, got ${actual?.toList()}")
}
