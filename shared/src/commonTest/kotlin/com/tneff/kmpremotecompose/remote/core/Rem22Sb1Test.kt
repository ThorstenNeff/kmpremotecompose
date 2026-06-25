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
import com.tneff.kmpremotecompose.remote.core.operations.DataListIds
import com.tneff.kmpremotecompose.remote.core.operations.MatrixSave
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.OperationReader
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.TextMerge
import com.tneff.kmpremotecompose.remote.core.operations.Theme
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
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

/** REM-22 SB1: MATRIX_SAVE (130, the 35-doc lever) + TEXT_MERGE (136) + ID_LIST (146) + THEME (63). */
class Rem22Sb1Test {

    @AfterTest
    fun cleanup() {
        Operations.resetReaders()
    }

    @Test
    fun matrixSave_isOpcodeOnly() {
        assertContentEquals(bytes(0x82), encoded(MatrixSave()))
        assertEquals(MatrixSave(), reread(MatrixSave(), MatrixSave))
    }

    @Test
    fun textMerge_bytesAndRoundTrip() {
        // opcode 136, textId 42, src1 10, src2 11.
        val op = TextMerge(42, 10, 11)
        assertContentEquals(
            bytes(0x88, 0, 0, 0, 0x2A, 0, 0, 0, 0x0A, 0, 0, 0, 0x0B),
            encoded(op),
        )
        assertEquals(op, reread(op, TextMerge))
    }

    @Test
    fun idList_bytesAndRoundTrip() {
        // opcode 146, id 5, count 2, ids 100, 200.
        val op = DataListIds(5, intArrayOf(100, 200))
        assertContentEquals(
            bytes(0x92, 0, 0, 0, 0x05, 0, 0, 0, 0x02, 0, 0, 0, 0x64, 0, 0, 0, 0xC8),
            encoded(op),
        )
        assertEquals(op, reread(op, DataListIds))
    }

    @Test
    fun theme_bytesAndRoundTrip() {
        // opcode 63, theme 2.
        val op = Theme(2)
        assertContentEquals(bytes(0x3F, 0, 0, 0, 0x02), encoded(op))
        assertEquals(op, reread(op, Theme))
    }

    @Test
    fun allResolveInBothBaseLayers() {
        Operations.resetReaders()
        Builtins.register()
        for (op in listOf(Operations.MATRIX_SAVE, Operations.TEXT_MERGE, Operations.ID_LIST, Operations.THEME)) {
            assertTrue(Operations.isValid(op, 6, 0), "${Operations.name(op)} not resolvable at api 6")
            assertTrue(Operations.isValid(op, 7, 0), "${Operations.name(op)} not resolvable at api 7")
        }
    }
}
