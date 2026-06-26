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

import androidx.compose.ui.graphics.PathOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * REM-31 (L2-S2) — pure-logic tests (no render surface) for the path-geometry helpers. Runs on every
 * target including the jvm host. Render correctness itself is verified by Maestro image parity (S4).
 */
class PathGeometryTest {

    @Test
    fun pathOperation_mapsByteToComposeOperation() {
        // Order verified against upstream ComposePaintContext.combinePath.
        assertEquals(PathOperation.Difference, PathGeometry.pathOperation(0))
        assertEquals(PathOperation.Intersect, PathGeometry.pathOperation(1))
        assertEquals(PathOperation.ReverseDifference, PathGeometry.pathOperation(2))
        assertEquals(PathOperation.Union, PathGeometry.pathOperation(3))
        assertEquals(PathOperation.Xor, PathGeometry.pathOperation(4))
        // Out-of-range falls back to Union (fail-soft).
        assertEquals(PathOperation.Union, PathGeometry.pathOperation(9))
    }

    @Test
    fun tweenPathData_returnsEndpointsVerbatimAtZeroAndOne() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(4f, 5f, 6f)
        // Upstream returns the SAME array instance (no copy) at the extremes.
        assertSame(a, PathGeometry.tweenPathData(a, b, 0f))
        assertSame(b, PathGeometry.tweenPathData(a, b, 1f))
    }

    @Test
    fun tweenPathData_lerpsInteriorValues() {
        val a = floatArrayOf(0f, 10f, -4f)
        val b = floatArrayOf(10f, 20f, 4f)
        val mid = PathGeometry.tweenPathData(a, b, 0.5f)
        assertEquals(5f, mid[0])
        assertEquals(15f, mid[1])
        assertEquals(0f, mid[2])
    }

    @Test
    fun tweenPathData_keepsFirstValueWhenEitherEndpointIsNaN() {
        // NaN slots are command markers / variable ids — must NOT be lerped (keep data1's value).
        val a = floatArrayOf(Float.NaN, 2f, 7f)
        val b = floatArrayOf(9f, Float.NaN, 11f)
        val mid = PathGeometry.tweenPathData(a, b, 0.5f)
        assertTrue(mid[0].isNaN(), "a[0] NaN → keep a")
        assertEquals(2f, mid[1], "b[1] NaN → keep a's 2f")
        assertEquals(9f, mid[2], "both finite → lerp")
    }

    @Test
    fun tweenPathData_iteratesOverSecondArrayLength() {
        // Mirrors upstream getPathArray: result length tracks data2.
        val a = floatArrayOf(0f, 0f, 0f, 0f)
        val b = floatArrayOf(2f, 2f)
        assertEquals(2, PathGeometry.tweenPathData(a, b, 0.5f).size)
    }
}
