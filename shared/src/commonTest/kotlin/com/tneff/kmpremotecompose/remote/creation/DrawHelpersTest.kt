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
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawArc
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawCircle
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawLine
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawOval
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawRect
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawSector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-86 (E2) — draw-helper byte tests. Stage-1 (round-trip self-consistency) and Stage-2 (decode +
 * op-tree inspect) per helper. Stage-3 (byte-equality vs corpus oracle) lives in [DocumentDslSmokeTest].
 *
 * Pins three properties for every helper:
 *  1. The right op-type lands in the encoded stream (decode + assertEquals against the expected op).
 *  2. The operand floats round-trip raw-bit-exact (NaN-encoded variable refs would survive too).
 *  3. `Number` arguments work — passing `Int` literals produces the same bytes as the matching
 *     `Float` literals.
 */
class DrawHelpersTest {

    @Test
    fun drawCircle_emitsDrawCircleOp_andOperandsRoundTrip() {
        val bytes = document(width = 100, height = 100) {
            drawCircle(10f, 20f, 5f)
        }

        val ops = DocumentReader.inflate(bytes).operations
        assertTrue(ops.any { it is DrawCircle }, "DRAW_CIRCLE not in op stream")
        val drawCircle = ops.first { it is DrawCircle } as DrawCircle
        assertEquals(10f, drawCircle.centerX)
        assertEquals(20f, drawCircle.centerY)
        assertEquals(5f, drawCircle.radius)
    }

    @Test
    fun drawCircle_numberOverload_matchesFloatOverload() {
        val viaInts = document(width = 100, height = 100) { drawCircle(10, 20, 5) }
        val viaFloats = document(width = 100, height = 100) { drawCircle(10f, 20f, 5f) }
        assertTrue(viaInts.contentEquals(viaFloats), "Int and Float overloads diverge byte-wise")
    }

    @Test
    fun drawRect_emitsDrawRectOp_andOperandsRoundTrip() {
        val bytes = document(width = 100, height = 100) {
            drawRect(0f, 0f, 50f, 50f)
        }
        val drawRect = DocumentReader.inflate(bytes).operations.first { it is DrawRect } as DrawRect
        assertEquals(0f, drawRect.left)
        assertEquals(0f, drawRect.top)
        assertEquals(50f, drawRect.right)
        assertEquals(50f, drawRect.bottom)
    }

    @Test
    fun drawOval_emitsDrawOvalOp_andOperandsRoundTrip() {
        val bytes = document(width = 100, height = 100) {
            drawOval(0f, 10f, 100f, 110f)
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is DrawOval } as DrawOval
        assertEquals(0f, op.left); assertEquals(10f, op.top)
        assertEquals(100f, op.right); assertEquals(110f, op.bottom)
    }

    @Test
    fun drawLine_emitsDrawLineOp_andOperandsRoundTrip() {
        val bytes = document(width = 100, height = 100) {
            drawLine(0f, 0f, 50f, 100f)
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is DrawLine } as DrawLine
        assertEquals(0f, op.x1); assertEquals(0f, op.y1)
        assertEquals(50f, op.x2); assertEquals(100f, op.y2)
    }

    @Test
    fun drawArc_emitsDrawArcOp_withSixOperands() {
        val bytes = document(width = 100, height = 100) {
            drawArc(0f, 0f, 100f, 100f, 30f, 120f)
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is DrawArc } as DrawArc
        assertEquals(0f, op.left); assertEquals(0f, op.top)
        assertEquals(100f, op.right); assertEquals(100f, op.bottom)
        assertEquals(30f, op.startAngle); assertEquals(120f, op.sweepAngle)
    }

    @Test
    fun drawSector_emitsDrawSectorOp_withSixOperands() {
        val bytes = document(width = 100, height = 100) {
            drawSector(0f, 0f, 100f, 100f, 0f, 90f)
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is DrawSector } as DrawSector
        assertEquals(0f, op.left); assertEquals(0f, op.top)
        assertEquals(100f, op.right); assertEquals(100f, op.bottom)
        assertEquals(0f, op.startAngle); assertEquals(90f, op.sweepAngle)
    }

    @Test
    fun drawHelpers_doNotAllocateIds() {
        // W#3: DRAW_* ops are NOT id-bearing. id-allocator must stay at the prolog's reservation.
        val bytes = document(width = 100, height = 100, contentDescription = "Clock") {
            // Allocator is at 43 after the prolog reserved 42 for the content-description.
            assertEquals(43, ids.peek())
            drawCircle(10, 10, 5)
            drawRect(0, 0, 20, 20)
            drawOval(0, 0, 30, 30)
            drawLine(0, 0, 40, 40)
            drawArc(0, 0, 50, 50, 0, 45)
            drawSector(0, 0, 60, 60, 0, 90)
            assertEquals(43, ids.peek()) // unchanged
        }
        // sanity: decode succeeds
        DocumentReader.inflate(bytes)
    }
}
