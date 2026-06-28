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
import com.tneff.kmpremotecompose.remote.core.operations.ColorExpression
import com.tneff.kmpremotecompose.remote.core.operations.NamedVariable
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-92 (E4) — `COLOR_EXPRESSIONS` mode-builder byte tests. Pins the 4-int packing per mode
 * (mode in `param1.low8`, alpha/alphaId in `param1.high16`, channels/colours in `param2..param4`).
 */
class ColorExpressionHelpersTest {

    @Test
    fun colorExpressionInterpolate_mode0_literalLiteral() {
        var id = -1
        val bytes = document(width = 100, height = 100) {
            id = colorExpressionInterpolate(0xFF112233.toInt(), 0xFFAABBCC.toInt(), 0.5f)
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is ColorExpression } as ColorExpression
        assertEquals(id, op.id)
        assertEquals(0, op.param1, "mode 0 = both literals; high 16 unused")
        assertEquals(0xFF112233.toInt(), op.param2)
        assertEquals(0xFFAABBCC.toInt(), op.param3)
        assertEquals(0.5f, Float.fromBits(op.param4))
    }

    @Test
    fun colorExpressionInterpolateIdLit_mode1_idLiteral() {
        val bytes = document(width = 100, height = 100) {
            colorExpressionInterpolateIdLit(colorId1 = 99, color2 = 0xFFAABBCC.toInt(), tween = 0.3f)
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is ColorExpression } as ColorExpression
        assertEquals(1, op.param1 and 0xFF, "mode 1 = bit0 set (c1 is colorId)")
        assertEquals(99, op.param2)
        assertEquals(0xFFAABBCC.toInt(), op.param3)
        assertEquals(0.3f, Float.fromBits(op.param4))
    }

    @Test
    fun colorExpressionInterpolateLitId_mode2_literalId() {
        val bytes = document(width = 100, height = 100) {
            colorExpressionInterpolateLitId(color1 = 0xFFAABBCC.toInt(), colorId2 = 99, tween = 0.7f)
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is ColorExpression } as ColorExpression
        assertEquals(2, op.param1 and 0xFF, "mode 2 = bit1 set (c2 is colorId)")
        assertEquals(0xFFAABBCC.toInt(), op.param2)
        assertEquals(99, op.param3)
        assertEquals(0.7f, Float.fromBits(op.param4))
    }

    @Test
    fun colorExpressionInterpolateIds_mode3_idId() {
        val bytes = document(width = 100, height = 100) {
            colorExpressionInterpolateIds(colorId1 = 99, colorId2 = 100, tween = 0.2f)
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is ColorExpression } as ColorExpression
        assertEquals(3, op.param1 and 0xFF, "mode 3 = bit0 + bit1 set")
        assertEquals(99, op.param2)
        assertEquals(100, op.param3)
        assertEquals(0.2f, Float.fromBits(op.param4))
    }

    @Test
    fun colorExpressionHsv_mode4_packsAlphaInHigh16() {
        val bytes = document(width = 100, height = 100) {
            colorExpressionHsv(hue = 0.5f, saturation = 0.6f, value = 0.7f, alpha = 200)
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is ColorExpression } as ColorExpression
        assertEquals(4, op.param1 and 0xFF, "mode 4 = HSV")
        assertEquals(200, (op.param1 shr 16) and 0xFF, "alpha byte in param1.high16")
        assertEquals(0.5f, Float.fromBits(op.param2))
        assertEquals(0.6f, Float.fromBits(op.param3))
        assertEquals(0.7f, Float.fromBits(op.param4))
    }

    @Test
    fun colorExpressionArgb_mode5_packsAlphaTimes1024() {
        val bytes = document(width = 100, height = 100) {
            colorExpressionArgb(alpha = 0.5f, red = 1f, green = 0.5f, blue = 0f)
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is ColorExpression } as ColorExpression
        assertEquals(5, op.param1 and 0xFF, "mode 5 = ARGB float")
        assertEquals(512, op.param1 shr 16, "alpha quantised as (alpha * 1024).toInt()")
        assertEquals(1f, Float.fromBits(op.param2))
        assertEquals(0.5f, Float.fromBits(op.param3))
        assertEquals(0f, Float.fromBits(op.param4))
    }

    @Test
    fun colorExpressionArgbById_mode6_packsAlphaIdInHigh16() {
        val bytes = document(width = 100, height = 100) {
            colorExpressionArgbById(alphaId = 99, red = 1f, green = 0.5f, blue = 0f)
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is ColorExpression } as ColorExpression
        assertEquals(6, op.param1 and 0xFF, "mode 6 = ID-ARGB (alpha is a colorId)")
        assertEquals(99, op.param1 shr 16, "alphaId in param1.high16")
        assertEquals(1f, Float.fromBits(op.param2))
        assertEquals(0.5f, Float.fromBits(op.param3))
        assertEquals(0f, Float.fromBits(op.param4))
    }

    @Test
    fun colorExpressionHelpers_allocatePlainIds_monotonically() {
        document(width = 100, height = 100, contentDescription = "Clock") {
            assertEquals(43, ids.peek())
            colorExpressionInterpolate(0, 0, 0f) // 43
            colorExpressionHsv(0f, 0f, 0f) // 44
            colorExpressionArgb(1f, 1f, 1f, 1f) // 45
            assertEquals(46, ids.peek())
        }
    }

    @Test
    fun addNamedVariable_allocatesRegion1Id_andReturns1048618First() {
        var firstId = -1
        var secondId = -1
        val bytes = document(width = 100, height = 100) {
            firstId = addNamedVariable("system_accent1_500", varType = 0)
            secondId = addNamedVariable("system_accent2_500", varType = 0)
        }
        assertEquals(1048618, firstId, "first NamedVariable id = (1 shl 20) + 42 = 1048618")
        assertEquals(1048619, secondId)
        val ops = DocumentReader.inflate(bytes).operations.filterIsInstance<NamedVariable>()
        assertEquals(2, ops.size)
        assertEquals(1048618, ops[0].varId)
        assertEquals("system_accent1_500", ops[0].name)
        assertEquals(1048619, ops[1].varId)
    }

    @Test
    fun addNamedVariable_doesNotAdvancePlainOrArrayCounter() {
        document(width = 100, height = 100, contentDescription = "Clock") {
            assertEquals(43, ids.peek())
            addNamedVariable("v", 0)
            assertEquals(43, ids.peek(), "plain counter unchanged after named variable")
            assertEquals(IdAllocator.START_ARRAY, ids.peekArray(), "array counter unchanged")
        }
    }
}
