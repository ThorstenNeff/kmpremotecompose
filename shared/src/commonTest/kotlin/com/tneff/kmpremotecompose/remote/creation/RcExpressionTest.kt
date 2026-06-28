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
import com.tneff.kmpremotecompose.remote.core.operations.FloatExpression
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-92 (E4) — `floatExpression` + `RcExpression` byte tests. Pins:
 *  - Operator constants encode as `asNan(OFFSET + n)` with `OFFSET = 0x310_000` (upstream-verified
 *    against `RpnFloatEvaluator.OFFSET`).
 *  - System-variable refs encode as `asNan(RemoteContext.ID_*)`.
 *  - `floatExpression(...)` returns a NaN-encoded id chainable into subsequent expressions / draws.
 *  - `FloatExpression` op carries the operand raw bits verbatim.
 */
class RcExpressionTest {

    @Test
    fun operatorConstants_encodeAsAsNanOfOffsetPlusN() {
        // The RPN byte-anchor: operator ids = OFFSET + index, NaN-encoded.
        assertEquals(0x310_000, RcExpression.OFFSET)
        assertEquals(0x310_001, WireTypes.idFromNan(RcExpression.ADD))
        assertEquals(0x310_002, WireTypes.idFromNan(RcExpression.SUB))
        assertEquals(0x310_003, WireTypes.idFromNan(RcExpression.MUL))
        assertEquals(0x310_004, WireTypes.idFromNan(RcExpression.DIV))
        assertEquals(0x310_005, WireTypes.idFromNan(RcExpression.MOD))
        assertEquals(0x310_006, WireTypes.idFromNan(RcExpression.MIN))
        assertEquals(0x310_007, WireTypes.idFromNan(RcExpression.MAX))
        assertEquals(0x310_008, WireTypes.idFromNan(RcExpression.POW))
        assertEquals(0x310_009, WireTypes.idFromNan(RcExpression.SQRT))
        assertEquals(0x310_00A, WireTypes.idFromNan(RcExpression.ABS))
        assertEquals(0x310_00B, WireTypes.idFromNan(RcExpression.SIGN))
        assertEquals(0x310_00D, WireTypes.idFromNan(RcExpression.EXP))
        assertEquals(0x310_00E, WireTypes.idFromNan(RcExpression.FLOOR))
        assertEquals(0x310_00F, WireTypes.idFromNan(RcExpression.LOG))
        assertEquals(0x310_010, WireTypes.idFromNan(RcExpression.LN))
        assertEquals(0x310_011, WireTypes.idFromNan(RcExpression.ROUND))
        assertEquals(0x310_012, WireTypes.idFromNan(RcExpression.SIN))
        assertEquals(0x310_013, WireTypes.idFromNan(RcExpression.COS))
        assertEquals(0x310_01B, WireTypes.idFromNan(RcExpression.CLAMP))
        assertEquals(0x310_01D, WireTypes.idFromNan(RcExpression.DEG))
        assertEquals(0x310_01E, WireTypes.idFromNan(RcExpression.RAD))
        // Array ops.
        assertEquals(0x310_020, WireTypes.idFromNan(RcExpression.A_DEREF))
        assertEquals(0x310_021, WireTypes.idFromNan(RcExpression.A_MAX))
        assertEquals(0x310_022, WireTypes.idFromNan(RcExpression.A_MIN))
        assertEquals(0x310_023, WireTypes.idFromNan(RcExpression.A_SUM))
        assertEquals(0x310_024, WireTypes.idFromNan(RcExpression.A_AVG))
        assertEquals(0x310_025, WireTypes.idFromNan(RcExpression.A_LEN))
    }

    @Test
    fun systemVariableRefs_encodeAsAsNanOfRemoteContextIds() {
        assertEquals(RemoteContext.ID_CONTINUOUS_SEC, WireTypes.idFromNan(RcExpression.CONTINUOUS_SEC))
        assertEquals(RemoteContext.ID_TIME_IN_SEC, WireTypes.idFromNan(RcExpression.TIME_IN_SEC))
        assertEquals(RemoteContext.ID_TIME_IN_MIN, WireTypes.idFromNan(RcExpression.TIME_IN_MIN))
        assertEquals(RemoteContext.ID_TIME_IN_HR, WireTypes.idFromNan(RcExpression.TIME_IN_HR))
        assertEquals(RemoteContext.ID_WINDOW_WIDTH, WireTypes.idFromNan(RcExpression.WINDOW_WIDTH))
        assertEquals(RemoteContext.ID_WINDOW_HEIGHT, WireTypes.idFromNan(RcExpression.WINDOW_HEIGHT))
        assertEquals(RemoteContext.ID_DENSITY, WireTypes.idFromNan(RcExpression.DENSITY))
        assertEquals(RemoteContext.ID_FONT_SIZE, WireTypes.idFromNan(RcExpression.FONT_SIZE))
    }

    @Test
    fun floatExpression_allocatesPlainId_andReturnsAsNanOfThatId() {
        var ret: Float = 0f
        var expectedId = -1
        document(width = 100, height = 100, contentDescription = "Clock") {
            expectedId = ids.peek() // = 43 after prolog claimed 42
            ret = floatExpression(60f, RcExpression.TIME_IN_SEC, RcExpression.MUL)
        }
        assertEquals(43, expectedId)
        assertEquals(43, WireTypes.idFromNan(ret), "return must be asNan(allocatedId)")
        assertTrue(ret.isNaN(), "return must be a NaN float for chaining")
    }

    @Test
    fun floatExpression_writesOperandsRawBitVerbatim() {
        val bytes = document(width = 100, height = 100) {
            floatExpression(60f, RcExpression.TIME_IN_SEC, RcExpression.MUL)
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is FloatExpression } as FloatExpression
        assertEquals(42, op.id, "first allocated id = 42 (no content-desc here)")
        assertEquals(3, op.value.size)
        assertEquals(60f, op.value[0])
        assertEquals(RemoteContext.ID_TIME_IN_SEC, WireTypes.idFromNan(op.value[1]))
        assertEquals(RcExpression.OFFSET + 3, WireTypes.idFromNan(op.value[2]))
    }

    @Test
    fun floatExpression_isChainable_resultEmbedsAsVariableRef() {
        // The chainable contract: a floatExpression's NaN-id can be passed as an operand to a later
        // floatExpression OR directly into a draw helper's coord slot.
        val bytes = document(width = 100, height = 100, contentDescription = "Clock") {
            val halfWidth = floatExpression(RcExpression.WINDOW_WIDTH, 2f, RcExpression.DIV) // 43
            val halfHeight = floatExpression(RcExpression.WINDOW_HEIGHT, 2f, RcExpression.DIV) // 44
            drawCircle(halfWidth, halfHeight, 25f)
        }
        val ops = DocumentReader.inflate(bytes).operations
        val floatExpressions = ops.filterIsInstance<FloatExpression>()
        assertEquals(2, floatExpressions.size)
        assertEquals(43, floatExpressions[0].id)
        assertEquals(44, floatExpressions[1].id)

        val drawCircle = ops.first { it is com.tneff.kmpremotecompose.remote.core.operations.draw.DrawCircle }
            as com.tneff.kmpremotecompose.remote.core.operations.draw.DrawCircle
        assertEquals(43, WireTypes.idFromNan(drawCircle.centerX), "drawCircle.centerX = asNan(43)")
        assertEquals(44, WireTypes.idFromNan(drawCircle.centerY), "drawCircle.centerY = asNan(44)")
        assertEquals(25f, drawCircle.radius)
    }

    @Test
    fun floatExpression_arrayOverload_carriesAnimationVerbatim() {
        val animationFloats = floatArrayOf(0.5f, 2.5f)
        val bytes = document(width = 100, height = 100) {
            floatExpression(floatArrayOf(60f, RcExpression.TIME_IN_SEC, RcExpression.MUL), animationFloats)
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is FloatExpression } as FloatExpression
        assertEquals(3, op.value.size)
        assertEquals(2, op.animation?.size)
        assertEquals(0.5f, op.animation!![0])
        assertEquals(2.5f, op.animation!![1])
    }

    @Test
    fun floatExpression_isIdBearing_onPlainPool() {
        // W#3: ANIMATED_FLOAT pulls region-0 plain. Not region-1, not region-2.
        document(width = 100, height = 100, contentDescription = "Clock") {
            assertEquals(43, ids.peek())
            assertEquals(IdAllocator.START_ARRAY, ids.peekArray())
            assertEquals(IdAllocator.START_VAR, ids.peekVariable())

            floatExpression(1f, 2f, RcExpression.ADD) // pulls 43

            assertEquals(44, ids.peek())
            assertEquals(IdAllocator.START_ARRAY, ids.peekArray(), "array counter unchanged")
            assertEquals(IdAllocator.START_VAR, ids.peekVariable(), "variable counter unchanged")
        }
    }
}
