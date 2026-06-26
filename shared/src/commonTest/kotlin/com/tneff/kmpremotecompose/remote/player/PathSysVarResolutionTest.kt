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

import com.tneff.kmpremotecompose.remote.player.compose.FloatsToPath
import com.tneff.kmpremotecompose.remote.player.compose.PathGeometry
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-36 (position-aware path resolution) — coords inside a path-data array dereference against the
 * store while command markers stay raw. The decisive property: resolution is by **position** (a slot
 * after a marker is a coord), not by region — so a coord whose id byte-collides with a marker id (e.g.
 * `MOVE == OFFSET_TO_UTC == 10`) is still resolved, while the marker itself is never touched.
 */
class PathSysVarResolutionTest {

    @Test
    fun positionAware_resolvesCoords_keepsMarkers_evenOnIdCollision() {
        val ctx = RemoteContext()
        ctx.loadFloat(10, 999f) // a variable whose id collides with the MOVE marker id (10)
        ctx.loadFloat(5, 50f)   // an ordinary system-var id

        // MOVE(len3): marker@0, coords@1,2 ; LINE(len5): marker@3, coords@4,5,6,7
        val data = floatArrayOf(
            WireTypes.asNan(FloatsToPath.MOVE), // 0 marker
            WireTypes.asNan(10),                // 1 coord — id 10 collides with MOVE id
            100f,                               // 2 literal coord
            WireTypes.asNan(FloatsToPath.LINE), // 3 marker
            0f, 0f,                             // 4,5 (start-point slots)
            WireTypes.asNan(5),                 // 6 coord — system var
            200f,                               // 7 literal coord
        )
        val out = PathGeometry.resolvePathData(ctx, data)

        // Markers untouched (still NaN, still their command id) — paths not corrupted.
        assertTrue(out[0].isNaN() && WireTypes.idFromNan(out[0]) == FloatsToPath.MOVE, "MOVE marker raw")
        assertTrue(out[3].isNaN() && WireTypes.idFromNan(out[3]) == FloatsToPath.LINE, "LINE marker raw")
        // Coords resolved — including the one whose id collides with a marker id (position wins).
        assertEquals(999f, out[1], "coord with marker-colliding id resolved by position")
        assertEquals(100f, out[2], "literal coord unchanged")
        assertEquals(50f, out[6], "system-var coord resolved")
        assertEquals(200f, out[7], "literal coord unchanged")
    }

    @Test
    fun allLiteralPath_returnsSameArrayInstance_noAlloc() {
        val ctx = RemoteContext()
        val data = floatArrayOf(WireTypes.asNan(FloatsToPath.MOVE), 1f, 2f, WireTypes.asNan(FloatsToPath.DONE))
        assertTrue(PathGeometry.resolvePathData(ctx, data) === data, "no variable coords → original array")
    }
}
