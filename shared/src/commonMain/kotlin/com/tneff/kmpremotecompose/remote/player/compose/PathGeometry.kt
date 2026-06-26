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

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import com.tneff.kmpremotecompose.remote.player.core.RcRenderState

/**
 * REM-31 (L2-S2) — player-side path-geometry helpers layered on the raw state store ([RcRenderState]).
 * Re-implemented from upstream `PathUtils.getPath` / `ComposePaintContext` path handling.
 */
internal object PathGeometry {

    /** Even-odd winding marker (mirrors upstream `RemoteComposeState.getPathWinding == 1`). */
    private const val WINDING_EVEN_ODD: Int = 1

    /**
     * Build (or fetch the cached) Compose [Path] for [id] over the fractional segment [[start], [end]].
     * Mirrors `RemoteComposeState.getPath(id, start, end)`: cache hit → return; else build the path
     * from path-data via [FloatsToPath], apply even-odd fill type when [RcRenderState.getPathWinding]
     * is `1`, and cache it.
     */
    fun buildPath(state: RcRenderState, id: Int, start: Float, end: Float): Path {
        state.getPath(id)?.let { return it }
        val path = Path()
        val pathData = state.getPathData(id) ?: return path
        FloatsToPath.genPath(path, pathData, start, end)
        if (state.getPathWinding(id) == WINDING_EVEN_ODD) {
            path.fillType = PathFillType.EvenOdd
        }
        state.putPath(id, path)
        return path
    }

    /**
     * Interpolate raw path-data arrays for a tween. Mirrors upstream `getPathArray`: at `tween==0`/`1`
     * return the endpoint data verbatim; otherwise lerp element-wise, but where **either** endpoint
     * slot is NaN (a command marker / variable id) keep [data1]'s value (markers must not be lerped).
     * Iterates over [data2]'s length, matching upstream.
     */
    fun tweenPathData(data1: FloatArray, data2: FloatArray, tween: Float): FloatArray {
        if (tween == 0.0f) return data1
        if (tween == 1.0f) return data2
        return FloatArray(data2.size) { i ->
            val a = data1[i]
            val b = data2[i]
            if (a.isNaN() || b.isNaN()) a else (b - a) * tween + a
        }
    }

    /** Build a Compose [Path] from interpolated path-data over the segment [[start], [end]]. */
    fun buildTweenPath(
        state: RcRenderState,
        path1Id: Int,
        path2Id: Int,
        tween: Float,
        start: Float,
        end: Float,
    ): Path {
        val data1 = state.getPathData(path1Id) ?: return Path()
        val data2 = state.getPathData(path2Id) ?: return Path()
        val merged = tweenPathData(data1, data2, tween)
        val path = Path()
        FloatsToPath.genPath(path, merged, start, end)
        return path
    }

    /**
     * Map the `combinePath` operation byte to a [PathOperation]. Order verified against upstream
     * `ComposePaintContext.combinePath`: `0`=difference, `1`=intersect, `2`=reverse-difference,
     * `3`=union, `4`=xor.
     */
    fun pathOperation(operation: Byte): PathOperation =
        when (operation.toInt()) {
            0 -> PathOperation.Difference
            1 -> PathOperation.Intersect
            2 -> PathOperation.ReverseDifference
            3 -> PathOperation.Union
            4 -> PathOperation.Xor
            else -> PathOperation.Union
        }

    /** Combine two cached paths into a new [Path] via [operation]; result is `path1 OP path2`. */
    fun combinePaths(p1: Path, p2: Path, operation: Byte): Path =
        Path().apply { op(p1, p2, pathOperation(operation)) }
}
