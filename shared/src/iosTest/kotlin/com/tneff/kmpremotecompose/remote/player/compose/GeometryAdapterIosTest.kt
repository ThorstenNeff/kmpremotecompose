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

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
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
        val state = PlayerPaintState()
        PaintBundleApplier.applyTo(RemoteContext(), state,bundle)
        assertEquals(Color(0xFF112233.toInt()), state.paint.color)
        assertEquals(4f, state.paint.strokeWidth)
        assertEquals(PaintingStyle.Stroke, state.paint.style)
        assertEquals(StrokeCap.Square, state.paint.strokeCap)
    }

    // REM-67: COLOR_ID / COLOR_FILTER_ID carry a *color-id*, resolved via context.getColor (upstream
    // fixColor) — NOT a literal ARGB. The literal COLOR / COLOR_FILTER paths must stay byte-for-byte the same.

    @Test
    fun paintBundle_colorId_resolvesViaContext() {
        val context = RemoteContext()
        context.loadColor(61, 0xFF223344.toInt()) // the registered colour for id 61
        // [COLOR_ID=19, id=61] — pre-fix this set Color(61)=0x0000003D (α≈0, invisible).
        val state = PlayerPaintState()
        PaintBundleApplier.applyTo(context, state, intArrayOf(19, 61))
        assertEquals(Color(0xFF223344.toInt()), state.paint.color)
    }

    @Test
    fun paintBundle_color_literalUnchanged() {
        // COLOR=4 stays a literal ARGB regardless of any context registration for that numeric value.
        val context = RemoteContext().also { it.loadColor(0xFF112233.toInt(), 0xFFDEAD00.toInt()) }
        val state = PlayerPaintState()
        PaintBundleApplier.applyTo(context, state, intArrayOf(4, 0xFF112233.toInt()))
        assertEquals(Color(0xFF112233.toInt()), state.paint.color)
    }

    @Test
    fun paintBundle_colorFilterId_resolvesViaContext() {
        val context = RemoteContext()
        context.loadColor(7, 0xFFAABBCC.toInt())
        // [COLOR_FILTER_ID=20 | (BLEND_MODE_SRC_IN=5 << 16), id=7]
        val state = PlayerPaintState()
        PaintBundleApplier.applyTo(context, state, intArrayOf(20 or (5 shl 16), 7))
        assertEquals(ColorFilter.tint(Color(0xFFAABBCC.toInt()), BlendMode.SrcIn), state.paint.colorFilter)
    }

    @Test
    fun paintBundle_colorFilter_literalUnchanged() {
        // COLOR_FILTER=13 stays a literal ARGB tint.
        val context = RemoteContext().also { it.loadColor(0xFFAABBCC.toInt(), 0xFF00FF00.toInt()) }
        val state = PlayerPaintState()
        PaintBundleApplier.applyTo(context, state, intArrayOf(13 or (5 shl 16), 0xFFAABBCC.toInt()))
        assertEquals(ColorFilter.tint(Color(0xFFAABBCC.toInt()), BlendMode.SrcIn), state.paint.colorFilter)
    }

    // REM-67 site 3: applyGradient's control int packs a per-color id-mask in its high 16 bits. A
    // 1-color gradient degrades to a solid fill = the first colour, so paint.color exposes the resolved value.

    @Test
    fun paintBundle_gradientColorId_resolvesViaContext() {
        val context = RemoteContext()
        context.loadColor(9, 0xFF010203.toInt())
        // [GRADIENT=11 LINEAR(0)][control: colorLen=1, idMask bit0=1][color0=id 9][stopsLen=0][sx,sy,ex,ey][tile=0]
        val arr = intArrayOf(
            11,
            1 or (1 shl 16),
            9,
            0,
            0f.toRawBits(), 0f.toRawBits(), 1f.toRawBits(), 1f.toRawBits(),
            0,
        )
        val state = PlayerPaintState()
        PaintBundleApplier.applyTo(context, state, arr)
        assertEquals(Color(0xFF010203.toInt()), state.paint.color) // id 9 resolved, not Color(9)=ARGB garbage
    }

    @Test
    fun paintBundle_gradientColor_literalUnchanged() {
        // Same shape, idMask=0 → color0 is a literal ARGB (not resolved via context).
        val context = RemoteContext().also { it.loadColor(0xFF112233.toInt(), 0xFFDEAD00.toInt()) }
        val arr = intArrayOf(
            11,
            1 or (0 shl 16),
            0xFF112233.toInt(),
            0,
            0f.toRawBits(), 0f.toRawBits(), 1f.toRawBits(), 1f.toRawBits(),
            0,
        )
        val state = PlayerPaintState()
        PaintBundleApplier.applyTo(context, state, arr)
        assertEquals(Color(0xFF112233.toInt()), state.paint.color)
    }

    @Test
    fun paintBundle_textSizeAndTypefaceLandInSharedState() {
        // REM-32: TEXT_SIZE/TYPEFACE now populate the shared state (read by L2-S3 text), not deferred.
        // [TEXT_SIZE=1, 24f][TYPEFACE=16|(style<<16), fontId=7]
        val arr = intArrayOf(1, 24f.toRawBits(), 16 or (0 shl 16), 7)
        val deferred = mutableSetOf<String>()
        val state = PlayerPaintState()
        PaintBundleApplier.applyTo(RemoteContext(), state,arr, deferred = deferred)
        assertEquals(24f, state.textSizePx)
        assertEquals(7, state.typefaceId)
        assertTrue("TEXT_SIZE" !in deferred && "TYPEFACE" !in deferred, "text attrs applied, not deferred")
    }

    @Test
    fun playerPaintState_saveRestoreStacksAllThreeFields() {
        val state = PlayerPaintState()
        state.paint.color = Color(0xFF112233.toInt())
        state.textSizePx = 10f
        state.typefaceId = 3
        state.save()
        state.paint.color = Color(0xFFAABBCC.toInt())
        state.textSizePx = 99f
        state.typefaceId = 9
        state.restore()
        assertEquals(Color(0xFF112233.toInt()), state.paint.color)
        assertEquals(10f, state.textSizePx)
        assertEquals(3, state.typefaceId)
    }

    @Test
    fun paintBundle_staysInSyncPastDeferredTag() {
        // TEXTURE(24) + 3 args, then COLOR(4) + color. The COLOR must still land → walk stayed in sync.
        val arr = intArrayOf(24, 7, 0, 0, 4, 0xFF00FF00.toInt())
        val deferred = mutableSetOf<String>()
        val state = PlayerPaintState()
        PaintBundleApplier.applyTo(RemoteContext(), state,arr, deferred = deferred)
        assertTrue("TEXTURE" in deferred, "deferred tag recorded")
        assertEquals(Color(0xFF00FF00.toInt()), state.paint.color, "color after deferred tag applied")
    }

    @Test
    fun paintBundle_fillAndStrokeIsApproximatedAsFillAndLoggedVisibly() {
        // style=2 (FILL_AND_STROKE) has no CMP equivalent → Fill + visible in `deferred` (PO decision).
        val bundle = PaintData.Builder().style(2).build().values
        val deferred = mutableSetOf<String>()
        val state = PlayerPaintState()
        PaintBundleApplier.applyTo(RemoteContext(), state,bundle, deferred = deferred)
        assertEquals(PaintingStyle.Fill, state.paint.style)
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
        val state = PlayerPaintState()
        PaintBundleApplier.applyTo(RemoteContext(), state,arr)
        assertNotNull(state.paint.shader, "linear gradient should set a shader")
    }

    @Test
    fun paintBundle_cursorStaysInSyncAcrossEveryTagType() {
        // P3 (critical): each tag's advance must match upstream PaintBundle slot counts exactly — a
        // wrong advance desyncs the delta stream and corrupts every later op. We chain one of every
        // tag (1-slot, 0-slot packed, and the variadic FONT_AXIS/TEXTURE/PATH_EFFECT/GRADIENT) followed
        // by a sentinel COLOR; if any advance is off, the sentinel is not read as a COLOR cmd at the
        // right offset → the color won't land. Slot counts cross-checked against ./androidx PaintBundle.
        val sentinel = 0xFF123456.toInt()
        val arr = intArrayOf(
            1, 12f.toRawBits(),                                  // TEXT_SIZE (1)
            16 or (0 shl 16), 0,                                 // TYPEFACE  (1: fontType)
            5, 3f.toRawBits(),                                   // STROKE_WIDTH (1)
            6, 4f.toRawBits(),                                   // STROKE_MITER (1)
            7 or (2 shl 16),                                     // STROKE_CAP (0, packed)
            8 or (1 shl 16),                                     // STYLE (0, packed)
            9, 99,                                               // SHADER (1)
            15 or (2 shl 16),                                    // STROKE_JOIN (0, packed)
            10 or (1 shl 16),                                    // IMAGE_FILTER_QUALITY (0, packed)
            18 or (3 shl 16),                                    // BLEND_MODE (0, packed)
            17 or (1 shl 16),                                    // FILTER_BITMAP (0, packed)
            12, 0.5f.toRawBits(),                                // ALPHA (1)
            13 or (3 shl 16), 0xFF010203.toInt(),               // COLOR_FILTER (1: color)
            21,                                                  // CLEAR_COLOR_FILTER (0)
            22, 0f.toRawBits(),                                  // SHADER_MATRIX (1)
            23 or (1 shl 16), 7, 1f.toRawBits(),                 // FONT_AXIS count=1 (2: tag,val)
            24, 0, 0, 0,                                         // TEXTURE (3)
            25 or (2 shl 16), 0f.toRawBits(), 0f.toRawBits(),    // PATH_EFFECT count=2 (2)
            11 or (0 shl 16), 2, 0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0,
            0f.toRawBits(), 0f.toRawBits(), 10f.toRawBits(), 0f.toRawBits(), 0, // GRADIENT linear (9)
            4, sentinel,                                         // COLOR sentinel (1)
        )
        val deferred = mutableSetOf<String>()
        val state = PlayerPaintState()
        PaintBundleApplier.applyTo(RemoteContext(), state,arr, deferred = deferred)
        assertEquals(Color(sentinel), state.paint.color, "sentinel COLOR after every tag → cursor stayed in sync")
        // TEXT_SIZE/TYPEFACE now land in the shared state (REM-32); the rest stay deferred.
        for (tag in listOf("SHADER", "SHADER_MATRIX", "FONT_AXIS", "TEXTURE", "PATH_EFFECT")) {
            assertTrue(tag in deferred, "deferred should record $tag")
        }
        assertEquals(12f, state.textSizePx, "TEXT_SIZE applied to state")
    }

    @Test
    fun buildPath_inverseWindingFallsBackToNonZeroAndIsLogged() {
        // P1: CMP PathFillType has no Inverse → winding 2/3 → non-zero + logged (no silent cap).
        val context = RemoteContext()
        context.putPathData(1, triangle())
        context.putPathWinding(1, 2) // inverse
        val deferred = mutableSetOf<String>()
        val path = PathGeometry.buildPath(context, 1, 0f, 1f, deferred)
        assertEquals(PathFillType.NonZero, path.fillType, "inverse winding falls back to non-zero")
        assertTrue("INVERSE_WINDING" in deferred, "inverse winding must be logged")
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
        val delegate = GeometryPaintDelegate(context, Canvas(ImageBitmap(32, 32)), PlayerPaintState())

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
