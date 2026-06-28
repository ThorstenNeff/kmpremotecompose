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
package com.tneff.kmpremotecompose.remote.player.core

import com.tneff.kmpremotecompose.remote.wire.WireTypes

/**
 * REM-121 — shared, **Compose-free** path-data helpers in `player.core`, so both the renderer
 * (`PathGeometry`/`FloatsToPath`, `player.compose`) and the path-building ops (`PathAppend`,
 * `core.operations.draw`) use ONE source for the command grammar — no layer-crossing dependency on the
 * Compose renderer and no duplicated command-length table (the drift this consolidates).
 *
 * The path-data wire grammar is a flat float list of commands; each command is a **marker** (a NaN-id
 * in [MOVE]..[DONE]) followed by fixed-width slots ([commandLength] = marker + slots). For `LINE` the two
 * coordinates sit in the LAST two slots (the first two are padding) — this resolver dereferences ALL
 * variable slots, leaving the literal padding untouched, so it matches [FloatsToPath]'s cursor exactly.
 */
internal object PathDataResolver {

    // Path command markers (NaN-id payloads). Single source; [FloatsToPath] references these.
    const val MOVE: Int = 10
    const val LINE: Int = 11
    const val QUADRATIC: Int = 12
    const val CONIC: Int = 13
    const val CUBIC: Int = 14
    const val CLOSE: Int = 15
    const val DONE: Int = 16

    /**
     * The full length of the command opened by [marker] (the marker float + its fixed slots), or `-1`
     * for an unknown marker (caller must stop — mirrors [FloatsToPath.genPath]'s fail-soft `else -> break`).
     * Lengths: MOVE 3, LINE 5, QUADRATIC 7, CONIC 8, CUBIC 9, CLOSE/DONE 1 — the canonical table that
     * `genPath`'s per-command cursor advances must sum to.
     */
    fun commandLength(marker: Float): Int = when (WireTypes.idFromNan(marker)) {
        MOVE -> 3
        LINE -> 5
        QUADRATIC -> 7
        CONIC -> 8
        CUBIC -> 9
        CLOSE, DONE -> 1
        else -> -1
    }

    /**
     * REM-36 (position-aware) — resolve NaN **variable** coordinates of a path-data array against the
     * [state] store, while NEVER touching the **command markers**. The float at each command position is a
     * marker (left raw — it dispatches MOVE/LINE/…); every following slot in that command is a coordinate
     * (dereferenced if it is a variable NaN). Resolving by POSITION (not region) is what makes path
     * system-variable coords (region 0 — WINDOW_WIDTH/TIME) resolvable without the marker/system-var
     * byte-collision. Operators (region 3) + literals pass through; unknown marker → stop. Allocation-free
     * when no coordinate needs resolving (static paths return the original array).
     *
     * REM-121: also called per loop-iteration by [com.tneff.kmpremotecompose.remote.core.operations.draw.PathAppend]
     * to **bake** the current coordinate values into the accumulated path — without this, a loop that
     * appends segments referencing a loop-variant variable would store the same NaN id N times and resolve
     * them ALL to the variable's final value at draw time (a degenerate single-point path).
     */
    fun resolvePathData(state: RemoteContext, data: FloatArray): FloatArray {
        if (data.isEmpty()) return data
        var out: FloatArray? = null
        var i = 0
        while (i < data.size) {
            val len = commandLength(data[i])
            if (len < 0) break // unknown marker — leave the remainder untouched (genPath stops too)
            var j = i + 1
            while (j < i + len && j < data.size) {
                val f = data[j]
                if (f.isNaN() && !WireTypes.isOperationVariable(f)) {
                    if (out == null) out = data.copyOf()
                    out!![j] = state.getFloat(WireTypes.idFromNan(f))
                }
                j++
            }
            i += len
        }
        return out ?: data
    }
}
