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
import com.tneff.kmpremotecompose.remote.core.operations.ColorAttribute
import com.tneff.kmpremotecompose.remote.core.operations.ConditionalOperations
import com.tneff.kmpremotecompose.remote.core.operations.DebugMessage
import com.tneff.kmpremotecompose.remote.core.operations.MatrixRestore
import com.tneff.kmpremotecompose.remote.core.operations.MatrixRotate
import com.tneff.kmpremotecompose.remote.core.operations.MatrixScale
import com.tneff.kmpremotecompose.remote.core.operations.MatrixSkew
import com.tneff.kmpremotecompose.remote.core.operations.MatrixTranslate
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.OperationReader
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.UpdateDynamicFloatList
import com.tneff.kmpremotecompose.remote.core.operations.WakeIn
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
 * REM-22 SB2: matrix transforms (RESTORE/TRANSLATE/SCALE/ROTATE/SKEW) + CONDITIONAL_OPERATIONS,
 * DEBUG_MESSAGE, ATTRIBUTE_COLOR (base) + UPDATE_DYNAMIC_FLOAT_LIST, WAKE_IN (overlay).
 * Format-verified hand goldens (write → exact bytes) + read round-trips + layer resolvability.
 */
class Rem22Sb2Test {

    @AfterTest
    fun cleanup() {
        Operations.resetReaders()
    }

    @Test
    fun matrixOps_bytesAndRoundTrip() {
        assertContentEquals(bytes(0x83), encoded(MatrixRestore()))
        assertEquals(MatrixRestore(), reread(MatrixRestore(), MatrixRestore))

        assertContentEquals(bytes(0x7F, 0x3F, 0x80, 0, 0, 0x40, 0, 0, 0), encoded(MatrixTranslate(1.0f, 2.0f)))
        assertEquals(MatrixTranslate(1.0f, 2.0f), reread(MatrixTranslate(1.0f, 2.0f), MatrixTranslate))

        assertContentEquals(
            bytes(0x7E, 0x3F, 0x80, 0, 0, 0x3F, 0x80, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            encoded(MatrixScale(1.0f, 1.0f, 0.0f, 0.0f)),
        )
        assertEquals(MatrixScale(1f, 1f, 0f, 0f), reread(MatrixScale(1f, 1f, 0f, 0f), MatrixScale))

        // rotate 90.0f = 0x42B40000.
        assertContentEquals(bytes(0x81, 0x42, 0xB4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), encoded(MatrixRotate(90.0f, 0f, 0f)))
        assertEquals(MatrixRotate(90f, 0f, 0f), reread(MatrixRotate(90f, 0f, 0f), MatrixRotate))

        assertContentEquals(bytes(0x80, 0x3F, 0, 0, 0, 0, 0, 0, 0), encoded(MatrixSkew(0.5f, 0.0f)))
        assertEquals(MatrixSkew(0.5f, 0f), reread(MatrixSkew(0.5f, 0f), MatrixSkew))
    }

    @Test
    fun conditional_debug_colorAttribute_bytesAndRoundTrip() {
        // CONDITIONAL_OPERATIONS: opcode 178, byte type 1, a=1.0, b=2.0.
        val cond = ConditionalOperations(1, 1.0f, 2.0f)
        assertContentEquals(bytes(0xB2, 0x01, 0x3F, 0x80, 0, 0, 0x40, 0, 0, 0), encoded(cond))
        assertEquals(cond, reread(cond, ConditionalOperations))

        // DEBUG_MESSAGE: opcode 179, textId 42, value 1.0, flags 0.
        val dbg = DebugMessage(42, 1.0f, 0)
        assertContentEquals(bytes(0xB3, 0, 0, 0, 0x2A, 0x3F, 0x80, 0, 0, 0, 0, 0, 0), encoded(dbg))
        assertEquals(dbg, reread(dbg, DebugMessage))

        // ATTRIBUTE_COLOR: opcode 180, id 5, colorId 10, short type 3.
        val attr = ColorAttribute(5, 10, 3)
        assertContentEquals(bytes(0xB4, 0, 0, 0, 0x05, 0, 0, 0, 0x0A, 0, 0x03), encoded(attr))
        assertEquals(attr, reread(attr, ColorAttribute))
    }

    @Test
    fun overlayOps_bytesRoundTrip_andLayerPlacement() {
        // UPDATE_DYNAMIC_FLOAT_LIST: opcode 198, id 7, index 2.0, value 5.0.
        val upd = UpdateDynamicFloatList(7, 2.0f, 5.0f)
        assertContentEquals(bytes(0xC6, 0, 0, 0, 0x07, 0x40, 0, 0, 0, 0x40, 0xA0, 0, 0), encoded(upd))
        assertEquals(upd, reread(upd, UpdateDynamicFloatList))

        // WAKE_IN: opcode 191, wake 1.5 = 0x3FC00000.
        val wake = WakeIn(1.5f)
        assertContentEquals(bytes(0xBF, 0x3F, 0xC0, 0, 0), encoded(wake))
        assertEquals(wake, reread(wake, WakeIn))

        Operations.resetReaders()
        Builtins.register()
        for (op in listOf(Operations.UPDATE_DYNAMIC_FLOAT_LIST, Operations.WAKE_IN)) {
            assertFalse(Operations.isValid(op, 7, 0), "${Operations.name(op)} must not resolve at v7 baseline")
            assertTrue(Operations.isValid(op, 7, Operations.PROFILE_ANDROIDX))
            assertTrue(Operations.isValid(op, 7, Operations.PROFILE_WIDGETS))
        }
    }

    @Test
    fun baseOps_resolveInBothBaseLayers() {
        Operations.resetReaders()
        Builtins.register()
        for (op in listOf(
            Operations.MATRIX_RESTORE, Operations.MATRIX_TRANSLATE, Operations.MATRIX_SCALE,
            Operations.MATRIX_ROTATE, Operations.MATRIX_SKEW, Operations.CONDITIONAL_OPERATIONS,
            Operations.DEBUG_MESSAGE, Operations.ATTRIBUTE_COLOR,
        )) {
            assertTrue(Operations.isValid(op, 6, 0), "${Operations.name(op)} not resolvable at api 6")
            assertTrue(Operations.isValid(op, 7, 0), "${Operations.name(op)} not resolvable at api 7")
        }
    }
}
