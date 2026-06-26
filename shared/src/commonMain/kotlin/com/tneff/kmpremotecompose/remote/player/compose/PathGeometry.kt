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
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireTypes

/**
 * REM-31 (L2-S2) — player-side path-geometry helpers layered on the raw state store ([RemoteContext]).
 * Re-implemented from upstream `PathUtils.getPath` / `ComposePaintContext` path handling.
 */
internal object PathGeometry {

    // Winding rules (Android `Path.FillType` order; upstream `getPathWinding`).
    private const val WINDING_EVEN_ODD: Int = 1
    private const val WINDING_INVERSE_EVEN_ODD: Int = 2
    private const val WINDING_INVERSE_WINDING: Int = 3

    /**
     * Build (or fetch the cached) Compose [Path] for [id] over the fractional segment [[start], [end]].
     * Mirrors `RemoteComposeState.getPath(id, start, end)`: cache hit → return; else build from
     * path-data via [FloatsToPath], apply the winding fill type, and cache.
     *
     * Winding: `1` → even-odd, `2` → inverse-even-odd, `3` → inverse-winding. The inverse modes have no
     * Compose [PathFillType] equivalent (a CMP-common gap; solvable later via Skiko `PathFillMode.INVERSE_*`
     * as a small interop D-slice if a doc needs it) → for now they fall back to non-zero and are logged
     * in [deferred] (P1: the log IS the data-driven detection, no silent cap — like FILL_AND_STROKE).
     *
     * TODO(P2, deferred per PO 2026-06-26): this cache ignores [start]/[end] — `getPath(id)` returns
     * the path built on the FIRST call (with that call's trim), so a later draw of the same id at a
     * different start/end gets the stale path. **Faithful to upstream `PathUtils.getPath`** (identical
     * quirk) and safe for the static MVP (start/end = 0/1); since goldens are upstream-rendered the
     * quirk matches → no Maestro parity fail. Fix when **animated path-trimming** enters scope: cache
     * the FULL path (0,1) and trim per draw via `PathMeasure.getSegment(start*len, end*len)` instead of
     * baking the trim into the cached path.
     */
    fun buildPath(
        state: RemoteContext,
        id: Int,
        start: Float,
        end: Float,
        deferred: MutableSet<String>? = null,
    ): Path {
        state.getPath(id)?.let { return it }
        val path = Path()
        val pathData = state.getPathData(id) ?: return path
        FloatsToPath.genPath(path, resolvePathData(state, pathData), start, end)
        when (state.getPathWinding(id)) {
            WINDING_EVEN_ODD -> path.fillType = PathFillType.EvenOdd
            WINDING_INVERSE_WINDING, WINDING_INVERSE_EVEN_ODD ->
                deferred?.add("INVERSE_WINDING") // CMP PathFillType has no Inverse → non-zero
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
        state: RemoteContext,
        path1Id: Int,
        path2Id: Int,
        tween: Float,
        start: Float,
        end: Float,
    ): Path {
        val data1 = resolvePathData(state, state.getPathData(path1Id) ?: return Path())
        val data2 = resolvePathData(state, state.getPathData(path2Id) ?: return Path())
        val merged = tweenPathData(data1, data2, tween)
        val path = Path()
        FloatsToPath.genPath(path, merged, start, end)
        return path
    }

    /**
     * REM-36 E3 — resolve NaN **data-variable** elements of a path-data array against the store before
     * the path is built, so paths with variable coordinates render non-degenerate. Command markers
     * (region 0) and plain literals pass through unchanged; **operation/RPN** NaNs (region 3) are left
     * as-is for the RPN evaluator (E2 / E-D2). Returns the original array when it holds no data-vars
     * (no allocation for static paths).
     */
    private fun resolvePathData(state: RemoteContext, data: FloatArray): FloatArray {
        var hasDataVar = false
        for (f in data) if (WireTypes.isDataVariable(f)) { hasDataVar = true; break }
        if (!hasDataVar) return data
        return FloatArray(data.size) { i ->
            val f = data[i]
            if (WireTypes.isDataVariable(f)) state.getFloat(WireTypes.idFromNan(f)) else f
        }
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
