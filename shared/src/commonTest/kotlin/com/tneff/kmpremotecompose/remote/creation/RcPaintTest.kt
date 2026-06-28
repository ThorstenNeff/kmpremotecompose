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
import kotlin.test.assertTrue

/**
 * REM-86 (E2) — `RcPaint` byte tests. Pins the slot layout produced by the DSL against the upstream-
 * verified [PaintData] tag layout (REM-37) and confirms the order is the call order.
 */
class RcPaintTest {

    @Test
    fun paint_color_emitsPaintValuesOp_withColorSlot() {
        val bytes = document(width = 100, height = 100) {
            paint { color(0xFF112233.toInt()) }
        }
        val ops = DocumentReader.inflate(bytes).operations
        val paint = ops.first { it is PaintData } as PaintData

        assertEquals(2, paint.values.size)
        assertEquals(PaintData.COLOR, paint.values[0])
        assertEquals(0xFF112233.toInt(), paint.values[1])
    }

    @Test
    fun paint_colorId_writesTwoIntSlot() {
        val bytes = document(width = 100, height = 100) {
            paint { colorId(99) }
        }
        val paint = DocumentReader.inflate(bytes).operations.first { it is PaintData } as PaintData
        assertEquals(intArrayOf(PaintData.COLOR_ID, 99).toList(), paint.values.toList())
    }

    @Test
    fun paint_strokeWidth_storesRawBits() {
        val bytes = document(width = 100, height = 100) {
            paint { strokeWidth(2.5f) }
        }
        val paint = DocumentReader.inflate(bytes).operations.first { it is PaintData } as PaintData
        assertEquals(PaintData.STROKE_WIDTH, paint.values[0])
        assertEquals(2.5f, Float.fromBits(paint.values[1]))
    }

    @Test
    fun paint_style_packsInHighHalf() {
        val bytes = document(width = 100, height = 100) {
            paint { style(RcPaint.STYLE_STROKE) }
        }
        val paint = DocumentReader.inflate(bytes).operations.first { it is PaintData } as PaintData
        // Packed: low 16 = tag, high 16 = value.
        assertEquals(1, paint.values.size)
        assertEquals(PaintData.STYLE, paint.values[0] and 0xFFFF)
        assertEquals(RcPaint.STYLE_STROKE, paint.values[0] shr 16)
    }

    @Test
    fun paint_strokeCap_packsInHighHalf() {
        val bytes = document(width = 100, height = 100) {
            paint { strokeCap(RcPaint.CAP_ROUND) }
        }
        val paint = DocumentReader.inflate(bytes).operations.first { it is PaintData } as PaintData
        assertEquals(PaintData.STROKE_CAP, paint.values[0] and 0xFFFF)
        assertEquals(RcPaint.CAP_ROUND, paint.values[0] shr 16)
    }

    @Test
    fun paint_strokeJoin_packsInHighHalf() {
        val bytes = document(width = 100, height = 100) {
            paint { strokeJoin(RcPaint.JOIN_BEVEL) }
        }
        val paint = DocumentReader.inflate(bytes).operations.first { it is PaintData } as PaintData
        assertEquals(PaintData.STROKE_JOIN, paint.values[0] and 0xFFFF)
        assertEquals(RcPaint.JOIN_BEVEL, paint.values[0] shr 16)
    }

    @Test
    fun paint_alpha_storesRawBits() {
        val bytes = document(width = 100, height = 100) {
            paint { alpha(0.5f) }
        }
        val paint = DocumentReader.inflate(bytes).operations.first { it is PaintData } as PaintData
        assertEquals(PaintData.ALPHA, paint.values[0])
        assertEquals(0.5f, Float.fromBits(paint.values[1]))
    }

    @Test
    fun paint_blendMode_packsInHighHalf() {
        val bytes = document(width = 100, height = 100) {
            paint { blendMode(5) } // arbitrary PorterDuff ordinal
        }
        val paint = DocumentReader.inflate(bytes).operations.first { it is PaintData } as PaintData
        assertEquals(PaintData.BLEND_MODE, paint.values[0] and 0xFFFF)
        assertEquals(5, paint.values[0] shr 16)
    }

    @Test
    fun paint_antiAlias_packsBoolean() {
        val on = document(width = 100, height = 100) { paint { antiAlias(true) } }
        val off = document(width = 100, height = 100) { paint { antiAlias(false) } }
        val onP = DocumentReader.inflate(on).operations.first { it is PaintData } as PaintData
        val offP = DocumentReader.inflate(off).operations.first { it is PaintData } as PaintData
        assertEquals(1, onP.values[0] shr 16)
        assertEquals(0, offP.values[0] shr 16)
    }

    @Test
    fun paint_multipleAttrs_preserveCallOrder() {
        // Slot layout = call order. Critical for the upstream PaintBundle cursor.
        val bytes = document(width = 100, height = 100) {
            paint {
                color(0xFF000000.toInt())
                strokeWidth(3f)
                style(RcPaint.STYLE_STROKE)
                strokeCap(RcPaint.CAP_ROUND)
            }
        }
        val paint = DocumentReader.inflate(bytes).operations.first { it is PaintData } as PaintData
        val v = paint.values
        // Order: COLOR, argb, STROKE_WIDTH, bits, STYLE|stroke<<16, STROKE_CAP|round<<16
        assertEquals(6, v.size)
        assertEquals(PaintData.COLOR, v[0])
        assertEquals(0xFF000000.toInt(), v[1])
        assertEquals(PaintData.STROKE_WIDTH, v[2])
        assertEquals(3f, Float.fromBits(v[3]))
        assertEquals(PaintData.STYLE, v[4] and 0xFFFF)
        assertEquals(RcPaint.STYLE_STROKE, v[4] shr 16)
        assertEquals(PaintData.STROKE_CAP, v[5] and 0xFFFF)
        assertEquals(RcPaint.CAP_ROUND, v[5] shr 16)
    }

    @Test
    fun paint_doesNotAllocateIds() {
        // W#3: PAINT_VALUES is NOT id-bearing.
        document(width = 100, height = 100, contentDescription = "Clock") {
            assertEquals(43, ids.peek())
            paint { color(0xFF000000.toInt()) }
            paint { strokeWidth(2f); style(RcPaint.STYLE_STROKE) }
            assertEquals(43, ids.peek())
        }
    }

    @Test
    fun paint_block_emitsSeparatePaintValuesOps() {
        val bytes = document(width = 100, height = 100) {
            paint { color(0xFFAAAAAA.toInt()) }
            paint { color(0xFFBBBBBB.toInt()) }
        }
        val paints = DocumentReader.inflate(bytes).operations.filterIsInstance<PaintData>()
        assertEquals(2, paints.size, "each paint { } block must emit its own PAINT_VALUES op")
        assertEquals(0xFFAAAAAA.toInt(), paints[0].values[1])
        assertEquals(0xFFBBBBBB.toInt(), paints[1].values[1])
    }

    @Test
    fun rcPaintConstants_matchUpstreamOrdinals() {
        // Sanity: caller-visible enum-style constants match upstream Paint.Style/Cap/Join ordinals.
        assertEquals(0, RcPaint.STYLE_FILL)
        assertEquals(1, RcPaint.STYLE_STROKE)
        assertEquals(2, RcPaint.STYLE_FILL_AND_STROKE)
        assertEquals(0, RcPaint.CAP_BUTT)
        assertEquals(1, RcPaint.CAP_ROUND)
        assertEquals(2, RcPaint.CAP_SQUARE)
        assertEquals(0, RcPaint.JOIN_MITER)
        assertEquals(1, RcPaint.JOIN_ROUND)
        assertEquals(2, RcPaint.JOIN_BEVEL)
        assertTrue(true)
    }
}
