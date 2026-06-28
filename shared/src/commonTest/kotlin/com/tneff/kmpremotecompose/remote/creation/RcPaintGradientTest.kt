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
package com.tneff.kmpremotecompose.remote.creation

import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.draw.PaintData
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-92 (E4 + E2-tail) — gradient-slot byte tests for [RcPaint]. Pins the slot layout matched by
 * `PaintData.resolveBundle`'s GRADIENT walk + the upstream `PaintBundle` cursor:
 *
 *  `[GRADIENT | (type shl 16)]`, `[colorCount]`, `colorCount × colour ints`, `[stopsLen]`,
 *  `colorCount × stop floats (if stopsLen > 0)`, then `geometry` (LINEAR: 4 coords + tile;
 *  RADIAL: 3 coords + tile; SWEEP: 2 coords).
 */
class RcPaintGradientTest {

    @Test
    fun linearGradient_noStops_emitsExpectedSlotLayout() {
        val bytes = document(width = 100, height = 100) {
            paint {
                linearGradient(
                    x0 = 0f, y0 = 0f, x1 = 100f, y1 = 100f,
                    colors = intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt()),
                )
            }
        }
        val v = (DocumentReader.inflate(bytes).operations.first { it is PaintData } as PaintData).values
        // [GRADIENT | (LINEAR=0 shl 16)], [2 = colorCount], [color1], [color2], [0 = stopsLen],
        // [x0=0f], [y0=0f], [x1=100f], [y1=100f], [tile = TILE_CLAMP = 0]
        assertEquals(10, v.size)
        assertEquals(PaintData.GRADIENT, v[0] and 0xFFFF, "low 16 = GRADIENT tag")
        assertEquals(0, v[0] shr 16, "high 16 = type LINEAR (0)")
        assertEquals(2, v[1], "color count = 2")
        assertEquals(0xFF000000.toInt(), v[2])
        assertEquals(0xFFFFFFFF.toInt(), v[3])
        assertEquals(0, v[4], "stopsLen = 0 (no stops)")
        assertEquals(0f, Float.fromBits(v[5]))
        assertEquals(0f, Float.fromBits(v[6]))
        assertEquals(100f, Float.fromBits(v[7]))
        assertEquals(100f, Float.fromBits(v[8]))
        assertEquals(RcPaint.TILE_CLAMP, v[9])
    }

    @Test
    fun linearGradient_withStops_emitsStopFloats() {
        val bytes = document(width = 100, height = 100) {
            paint {
                linearGradient(
                    x0 = 0f, y0 = 0f, x1 = 50f, y1 = 50f,
                    colors = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt()),
                    stops = floatArrayOf(0f, 0.5f, 1f),
                    tile = RcPaint.TILE_REPEAT,
                )
            }
        }
        val v = (DocumentReader.inflate(bytes).operations.first { it is PaintData } as PaintData).values
        // tag, count=3, c1, c2, c3, stopsLen=3, s1, s2, s3, x0, y0, x1, y1, tile
        assertEquals(14, v.size)
        assertEquals(3, v[1])
        assertEquals(3, v[5], "stopsLen = 3")
        assertEquals(0f, Float.fromBits(v[6]))
        assertEquals(0.5f, Float.fromBits(v[7]))
        assertEquals(1f, Float.fromBits(v[8]))
        assertEquals(RcPaint.TILE_REPEAT, v[13])
    }

    @Test
    fun radialGradient_emitsThreeCoordsAndTile() {
        val bytes = document(width = 100, height = 100) {
            paint {
                radialGradient(
                    centerX = 50f, centerY = 50f, radius = 40f,
                    colors = intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()),
                )
            }
        }
        val v = (DocumentReader.inflate(bytes).operations.first { it is PaintData } as PaintData).values
        // tag, count=2, c1, c2, stopsLen=0, cx, cy, radius, tile
        assertEquals(9, v.size)
        assertEquals(1, v[0] shr 16, "type RADIAL = 1")
        assertEquals(2, v[1])
        assertEquals(0, v[4])
        assertEquals(50f, Float.fromBits(v[5]))
        assertEquals(50f, Float.fromBits(v[6]))
        assertEquals(40f, Float.fromBits(v[7]))
        assertEquals(RcPaint.TILE_CLAMP, v[8])
    }

    @Test
    fun sweepGradient_emitsTwoCoordsNoTile() {
        val bytes = document(width = 100, height = 100) {
            paint {
                sweepGradient(
                    centerX = 30f, centerY = 40f,
                    colors = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt()),
                )
            }
        }
        val v = (DocumentReader.inflate(bytes).operations.first { it is PaintData } as PaintData).values
        // tag, count=3, c1, c2, c3, stopsLen=0, cx, cy
        assertEquals(8, v.size)
        assertEquals(2, v[0] shr 16, "type SWEEP = 2")
        assertEquals(3, v[1])
        assertEquals(0, v[5])
        assertEquals(30f, Float.fromBits(v[6]))
        assertEquals(40f, Float.fromBits(v[7]))
    }

    @Test
    fun gradient_emptyColors_emitsHeaderOnly_noGeometry() {
        // Upstream contract: geometry is only emitted when colors is non-empty (see
        // PaintData.resolveBundle GRADIENT walk).
        val bytes = document(width = 100, height = 100) {
            paint {
                linearGradient(x0 = 0f, y0 = 0f, x1 = 0f, y1 = 0f, colors = intArrayOf())
            }
        }
        val v = (DocumentReader.inflate(bytes).operations.first { it is PaintData } as PaintData).values
        // [tag], [count=0], [stopsLen=0]
        assertEquals(3, v.size)
        assertEquals(0, v[1])
        assertEquals(0, v[2])
    }
}
