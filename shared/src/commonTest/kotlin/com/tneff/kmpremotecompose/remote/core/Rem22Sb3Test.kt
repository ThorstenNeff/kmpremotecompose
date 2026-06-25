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
package com.tneff.kmpremotecompose.remote.core

import com.tneff.kmpremotecompose.remote.core.operations.Builtins
import com.tneff.kmpremotecompose.remote.core.operations.IdLookup
import com.tneff.kmpremotecompose.remote.core.operations.MatrixExpression
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.OperationReader
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.TextLookup
import com.tneff.kmpremotecompose.remote.core.operations.TextTransform
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

private fun encoded(op: Operation): ByteArray {
    val buffer = WireBuffer()
    op.write(buffer)
    return buffer.toByteArray()
}

private fun reread(op: Operation, reader: OperationReader): Operation {
    val buffer = WireBuffer()
    op.write(buffer)
    buffer.byteIndex = 0
    assertEquals(op.opcode, buffer.readByte())
    val decoded = mutableListOf<Operation>()
    reader.read(buffer, decoded)
    return decoded.single()
}

/**
 * REM-22 SB3: TEXT_LOOKUP(151, base), MATRIX_EXPRESSION(187, V7_BASE always-on),
 * ID_LOOKUP(192) + TEXT_TRANSFORM(199, androidx/widgets overlay). The varied layer placement is
 * asserted alongside the byte goldens and read round-trips.
 */
class Rem22Sb3Test {

    @AfterTest
    fun cleanup() {
        Operations.resetReaders()
    }

    @Test
    fun bytesAndRoundTrips() {
        // TEXT_LOOKUP: opcode 151, textId 10, dataSet 20, index 2.0.
        val tl = TextLookup(10, 20, 2.0f)
        assertContentEquals(bytes(0x97, 0, 0, 0, 0x0A, 0, 0, 0, 0x14, 0x40, 0, 0, 0), encoded(tl))
        assertEquals(tl, reread(tl, TextLookup))

        // MATRIX_EXPRESSION: opcode 187, matrixId 5, type 1, count 1, float 1.0.
        val me = MatrixExpression(5, 1, floatArrayOf(1.0f))
        assertContentEquals(bytes(0xBB, 0, 0, 0, 0x05, 0, 0, 0, 0x01, 0, 0, 0, 0x01, 0x3F, 0x80, 0, 0), encoded(me))
        assertEquals(me, reread(me, MatrixExpression))

        // ID_LOOKUP: opcode 192, id 7, dataSet 8, index 1.0.
        val il = IdLookup(7, 8, 1.0f)
        assertContentEquals(bytes(0xC0, 0, 0, 0, 0x07, 0, 0, 0, 0x08, 0x3F, 0x80, 0, 0), encoded(il))
        assertEquals(il, reread(il, IdLookup))

        // TEXT_TRANSFORM: opcode 199, textId 3, srcId1 4, start 0.0, len 5.0, operation 2.
        val tt = TextTransform(3, 4, 0.0f, 5.0f, 2)
        assertContentEquals(
            bytes(0xC7, 0, 0, 0, 0x03, 0, 0, 0, 0x04, 0, 0, 0, 0, 0x40, 0xA0, 0, 0, 0, 0, 0, 0x02),
            encoded(tt),
        )
        assertEquals(tt, reread(tt, TextTransform))
    }

    @Test
    fun layerPlacement() {
        Operations.resetReaders()
        Builtins.register()

        // TEXT_LOOKUP is base.
        assertTrue(Operations.isValid(Operations.TEXT_LOOKUP, 6, 0))
        assertTrue(Operations.isValid(Operations.TEXT_LOOKUP, 7, 0))

        // MATRIX_EXPRESSION is a V7_BASE always-on op: resolves at api 7 baseline, NOT api 6.
        assertTrue(Operations.isValid(Operations.MATRIX_EXPRESSION, 7, 0))
        assertFalse(Operations.isValid(Operations.MATRIX_EXPRESSION, 6, 0))

        // ID_LOOKUP + TEXT_TRANSFORM are androidx/widgets overlay ops: not v7 baseline.
        for (op in listOf(Operations.ID_LOOKUP, Operations.TEXT_TRANSFORM)) {
            assertFalse(Operations.isValid(op, 7, 0), "${Operations.name(op)} must not resolve at v7 baseline")
            assertTrue(Operations.isValid(op, 7, Operations.PROFILE_ANDROIDX))
            assertTrue(Operations.isValid(op, 7, Operations.PROFILE_WIDGETS))
        }
    }
}
