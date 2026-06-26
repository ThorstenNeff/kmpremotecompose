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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import com.tneff.kmpremotecompose.remote.core.operations.draw.PaintData
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * REM-31 (L2-S2) — Skia-backed tests for the geometry adapter, run on the **iOS gate** (CMP-iOS =
 * Skiko), where real `Path`/`Paint`/`Canvas` are guaranteed. Runs against dev-1's real [RemoteContext]
 * (post-S1-merge reconcile). Full render parity is Maestro-verified later (S4).
 */
class GeometryAdapterIosTest {

    private fun marker(cmd: Int) = WireTypes.asNan(cmd)

    /** A closed right triangle (0,0)->(10,0)->(10,10)->close (perimeter ≈ 34.14). */
    private fun triangle(): FloatArray = floatArrayOf(
        marker(FloatsToPath.MOVE), 0f, 0f,
        marker(FloatsToPath.LINE), 0f, 0f, 10f, 0f,
        marker(FloatsToPath.LINE), 10f, 0f, 10f, 10f,
        marker(FloatsToPath.CLOSE),
    )

    private fun pathLength(path: Path): Float =
        PathMeasure().apply { setPath(path, false) }.length

    @Test
    fun floatsToPath_buildsClosedTriangle() {
        val path = Path()
        FloatsToPath.genPath(path, triangle(), 0f, 1f)
        assertTrue(!path.isEmpty, "path should not be empty")
        val len = pathLength(path)
        assertTrue(len in 33f..35f, "closed-triangle perimeter ~34.14, was $len")
    }

    @Test
    fun buildPath_appliesEvenOddFillTypeWhenWindingIsOne() {
        val context = RemoteContext()
        context.putPathData(1, triangle())
        context.putPathWinding(1, 1) // even-odd
        val path = PathGeometry.buildPath(context, 1, 0f, 1f)
        assertEquals(PathFillType.EvenOdd, path.fillType)
    }

    @Test
    fun floatsToPath_conicProducesGeometry() {
        // MOVE(0,0) + CONIC ctrl(5,10) end(10,0) weight 1 (option-b quad subdivision).
        val cmds = floatArrayOf(
            marker(FloatsToPath.MOVE), 0f, 0f,
            marker(FloatsToPath.CONIC), 0f, 0f, 5f, 10f, 10f, 0f, 1f,
        )
        val path = Path()
        FloatsToPath.genPath(path, cmds, 0f, 1f)
        assertTrue(!path.isEmpty, "conic should produce geometry")
        assertTrue(pathLength(path) > 0f, "conic path length > 0")
    }

    @Test
    fun paintBundle_appliesScalarAttributes() {
        val bundle = PaintData.Builder()
            .color(0xFF112233.toInt())
            .strokeWidth(4f)
            .style(1) // STROKE
            .strokeCap(2) // SQUARE
            .build()
            .values
        val paint = Paint()
        PaintBundleApplier.applyTo(paint, bundle)
        assertEquals(Color(0xFF112233.toInt()), paint.color)
        assertEquals(4f, paint.strokeWidth)
        assertEquals(PaintingStyle.Stroke, paint.style)
        assertEquals(StrokeCap.Square, paint.strokeCap)
    }

    @Test
    fun paintBundle_staysInSyncPastDeferredTag() {
        // TEXTURE(24) + 3 args, then COLOR(4) + color. The COLOR must still land → walk stayed in sync.
        val arr = intArrayOf(24, 7, 0, 0, 4, 0xFF00FF00.toInt())
        val deferred = mutableSetOf<String>()
        val paint = Paint()
        PaintBundleApplier.applyTo(paint, arr, deferred = deferred)
        assertTrue("TEXTURE" in deferred, "deferred tag recorded")
        assertEquals(Color(0xFF00FF00.toInt()), paint.color, "color after deferred tag applied")
    }

    @Test
    fun paintBundle_fillAndStrokeIsApproximatedAsFillAndLoggedVisibly() {
        // style=2 (FILL_AND_STROKE) has no CMP equivalent → Fill + visible in `deferred` (PO decision).
        val bundle = PaintData.Builder().style(2).build().values
        val deferred = mutableSetOf<String>()
        val paint = Paint()
        PaintBundleApplier.applyTo(paint, bundle, deferred = deferred)
        assertEquals(PaintingStyle.Fill, paint.style)
        assertTrue("STYLE_FILL_AND_STROKE" in deferred, "lossy fill+stroke must be logged, not silent")
    }

    @Test
    fun paintBundle_linearGradientSetsShader() {
        val arr = intArrayOf(
            11, // GRADIENT | (LINEAR << 16)
            2, // control: 2 colors, idMask 0
            0xFFFF0000.toInt(), 0xFF0000FF.toInt(),
            0, // stops length 0
            0f.toRawBits(), 0f.toRawBits(), 100f.toRawBits(), 0f.toRawBits(), // start/end
            0, // tileMode CLAMP
        )
        val paint = Paint()
        PaintBundleApplier.applyTo(paint, arr)
        assertNotNull(paint.shader, "linear gradient should set a shader")
    }

    @Test
    fun delegate_geometrySmokeAndPaintStack() {
        val context = RemoteContext()
        context.putPathData(1, triangle())
        context.putPathData(
            2,
            floatArrayOf(
                marker(FloatsToPath.MOVE), 2f, 2f,
                marker(FloatsToPath.LINE), 2f, 2f, 12f, 2f,
                marker(FloatsToPath.LINE), 12f, 2f, 12f, 12f,
                marker(FloatsToPath.CLOSE),
            ),
        )
        val delegate = GeometryPaintDelegate(context, Canvas(ImageBitmap(32, 32)))

        // Shapes / clip / matrix — smoke (no throw).
        delegate.drawRect(0f, 0f, 10f, 10f)
        delegate.drawCircle(5f, 5f, 3f)
        delegate.drawOval(0f, 0f, 8f, 4f)
        delegate.drawArc(0f, 0f, 10f, 10f, 0f, 90f)
        delegate.drawSector(0f, 0f, 10f, 10f, 0f, 90f)
        delegate.drawRoundRect(0f, 0f, 10f, 10f, 2f, 2f)
        delegate.drawLine(0f, 0f, 10f, 10f)
        delegate.drawPath(1, 0f, 1f)
        delegate.clipRect(0f, 0f, 16f, 16f)
        delegate.roundedClipRect(16f, 16f, 2f, 2f, 2f, 2f)

        // Matrix save/restore must be balanced (no throw, stack returns to base).
        delegate.matrixSave()
        delegate.matrixTranslate(2f, 2f)
        delegate.matrixScale(2f, 2f, 4f, 4f)
        delegate.matrixRotate(45f, 4f, 4f)
        delegate.matrixRestore()
        delegate.matrixFromPath(1, 0.5f, 0f, 0)

        // Paint save/restore round-trips the paint state.
        val before = delegate.paint.color
        delegate.savePaint()
        delegate.applyPaint(PaintData.Builder().color(0xFFFF0000.toInt()).build())
        assertEquals(Color(0xFFFF0000.toInt()), delegate.paint.color)
        delegate.restorePaint()
        assertEquals(before, delegate.paint.color, "paint restored after pop")

        // Path producers store results in the context.
        delegate.tweenPath(3, 1, 2, 0.5f)
        assertNotNull(context.getPathData(3), "tweenPath stored data under out id")
        delegate.combinePath(4, 1, 2, 3 /* union */)
        assertNotNull(context.getPath(4), "combinePath stored result path under out id")
    }
}
