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

import com.tneff.kmpremotecompose.conformance.RcCorpus
import com.tneff.kmpremotecompose.remote.core.operations.Builtins
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.player.core.PathDataResolver
import com.tneff.kmpremotecompose.remote.player.core.PathGenerator
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.RpnFloatEvaluator
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * REM-127 S2 — PathGenerator output must match the consumer's slot layout EXACTLY (§8.3 hard gate):
 * `MOVE`=3 slots, `CUBIC`=9 (marker + 8 floats; consumer skips the start pair), `CLOSE`=1. The producer
 * output walks cleanly under [PathDataResolver.commandLength] (the REM-121 single-source contract both
 * sides share) — no desync — and round-trips through `resolvePathData` unchanged (no var-refs).
 */
class Rem127PathGeneratorTest {

    private val VAR1 = WireTypes.asNan(0x310000 + 70)
    private val ctx = RemoteContext()
    private val eval: (FloatArray, Float) -> Float = { expr, t -> RpnFloatEvaluator.eval(expr, expr.size, ctx, t) }

    /** Walk a path-data array by [PathDataResolver.commandLength]; return the markers, or fail on desync. */
    private fun markers(path: FloatArray): List<Int> {
        val out = ArrayList<Int>()
        var i = 0
        while (i < path.size) {
            val m = path[i]
            assertTrue(m.isNaN(), "slot $i must be a marker NaN, was $m")
            val len = PathDataResolver.commandLength(m)
            assertTrue(len > 0, "unknown marker at $i")
            out += WireTypes.idFromNan(m)
            i += len
        }
        assertEquals(path.size, i, "command lengths must consume the buffer EXACTLY (no slot desync)")
        return out
    }

    @Test
    fun linearPath_layoutMatchesConsumerContract() {
        // x=VAR1 (identity → t), y=VAR1 → points (0,0),(5,5),(10,10). LINEAR → straight-line cubics.
        val path = PathGenerator.getPath(floatArrayOf(VAR1), floatArrayOf(VAR1), 0f, 10f, 3, PathGenerator.LINEAR, false, eval)
        assertEquals(PathGenerator.getReturnLength(3, false), path.size, "len = moveTo(3) + 2·cubic(9) = 21")
        assertEquals(listOf(PathDataResolver.MOVE, PathDataResolver.CUBIC, PathDataResolver.CUBIC), markers(path))
        // MOVE = [marker, 0, 0]
        assertEquals(0f, path[1]); assertEquals(0f, path[2])
        // 1st CUBIC at index 3: [marker, startX, startY, c1x,c1y, c2x,c2y, endX,endY]
        assertEquals(0f, path[4], "cubic start pair = previous point"); assertEquals(0f, path[5])
        assertEquals(5f, path[10], 1e-3f, "1st segment end x = sample t=5"); assertEquals(5f, path[11], 1e-3f)
        // 2nd CUBIC end = (10,10)
        assertEquals(10f, path[19], 1e-3f); assertEquals(10f, path[20], 1e-3f)
    }

    @Test
    fun splineLoop_endsWithCloseAndConsumesExactly() {
        val path = PathGenerator.getPath(floatArrayOf(VAR1), floatArrayOf(VAR1), 0f, 4f, 4, PathGenerator.SPLINE, true, eval)
        assertEquals(PathGenerator.getReturnLength(4, true), path.size, "loop len = 3 + 4·9 + 1 = 40")
        val ms = markers(path)
        assertEquals(PathDataResolver.MOVE, ms.first())
        assertEquals(PathDataResolver.CLOSE, ms.last(), "loop path must end with CLOSE")
    }

    @Test
    fun output_roundTripsThroughResolverUnchanged() {
        val path = PathGenerator.getPath(floatArrayOf(VAR1), floatArrayOf(VAR1), 0f, 10f, 3, PathGenerator.LINEAR, false, eval)
        val resolved = PathDataResolver.resolvePathData(ctx, path)
        // No data-var refs in a generated path → resolvePathData returns an equivalent buffer (same bits).
        assertEquals(path.size, resolved.size)
        for (i in path.indices) assertEquals(path[i].toRawBits(), resolved[i].toRawBits(), "slot $i changed")
    }

    @Test
    fun polarPath_centersAtCoordArray() {
        // r=VAR1 (=t), center (100,200), one full turn, LINEAR. At t=0: x=100+0, y=200+0.
        val path = PathGenerator.getPolarPath(floatArrayOf(VAR1), floatArrayOf(100f, 200f), 0f, 6.2831855f, 8, PathGenerator.LINEAR, true, eval)
        markers(path) // asserts clean stride
        assertEquals(100f, path[1], 1e-2f, "MOVE x = cx + r(0)·cos0 = 100"); assertEquals(200f, path[2], 1e-2f)
    }

    @Test
    fun pathExpression_loadsNonDegeneratePathDataEndToEnd() {
        // S3 producer wiring: paint plot2 (PathExpression id=44, SPLINE) → its path-data is in the store,
        // non-degenerate (>1 distinct sampled point) so the existing DrawPath#44 renders a real curve
        // (was BLANK pre-REM-127: PathExpression was a gap → no path-data → DrawPath drew nothing).
        Builtins.register()
        val doc = DocumentReader.inflate(RcCorpus.readFixture("corpus/plot2.rc"))
        val ctx = RemoteContext()
        RemoteComposePlayer(ctx).paint(doc, NoOpPaintContext(ctx))
        val path = ctx.getPathData(44)
        assertTrue(path != null && path.isNotEmpty(), "PathExpression must load path-data for DrawPath#44")
        val coords = path!!.filterIndexed { i, v -> !v.isNaN() }.toSet()
        assertTrue(coords.size > 2, "sampled path must be non-degenerate (distinct points), got ${coords.size}")
    }

    @Test
    fun monotonicMode_isLoudGuarded() {
        assertFailsWith<IllegalArgumentException>("MONOTONIC is corpus-absent → must throw, not silently mis-render") {
            PathGenerator.getPath(floatArrayOf(VAR1), floatArrayOf(VAR1), 0f, 4f, 4, PathGenerator.MONOTONIC, false, eval)
        }
    }
}
