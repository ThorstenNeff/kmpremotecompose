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
import com.tneff.kmpremotecompose.remote.core.operations.ComponentValue
import com.tneff.kmpremotecompose.remote.core.operations.DataDynamicListFloat
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.OperationReader
import com.tneff.kmpremotecompose.remote.core.operations.Operations
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

/** REM-19 SB2: COMPONENT_VALUE (150, the 66-doc lever) + DYNAMIC_FLOAT_LIST (197). */
class ComponentValueTest {

    @AfterTest
    fun cleanup() {
        Operations.resetReaders()
    }

    @Test
    fun componentValue_writesExactCorpusBytes() {
        // Anchored to REAL corpus bytes: base.rc offset 0xB4 — opcode 150, type 0, componentId -7
        // (0xFFFFFFF9), valueId 42.
        assertContentEquals(
            bytes(0x96, 0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xF9, 0x00, 0x00, 0x00, 0x2A),
            encoded(ComponentValue(type = 0, componentId = -7, valueId = 42)),
        )
        // base.rc also has a second one at 0xC1: type 1, componentId -7, valueId 43.
        assertContentEquals(
            bytes(0x96, 0x00, 0x00, 0x00, 0x01, 0xFF, 0xFF, 0xFF, 0xF9, 0x00, 0x00, 0x00, 0x2B),
            encoded(ComponentValue(type = 1, componentId = -7, valueId = 43)),
        )
    }

    @Test
    fun componentValue_readMirrorsWrite() {
        val op = ComponentValue(0, -7, 42)
        assertEquals(op, reread(op, ComponentValue))
    }

    @Test
    fun componentValue_isRegisteredInBothBaseLayers() {
        Operations.resetReaders()
        Builtins.register()
        assertTrue(Operations.isValid(Operations.COMPONENT_VALUE, 6, 0))
        assertTrue(Operations.isValid(Operations.COMPONENT_VALUE, 7, 0))
    }

    @Test
    fun dynamicFloatList_bytesAndRoundTrip() {
        // Format-verified hand golden (write → exact bytes): opcode 197, id 5, nbValues 3.0f.
        val op = DataDynamicListFloat(5, 3.0f)
        assertContentEquals(bytes(0xC5, 0x00, 0x00, 0x00, 0x05, 0x40, 0x40, 0x00, 0x00), encoded(op))
        assertEquals(op, reread(op, DataDynamicListFloat))
    }

    @Test
    fun dynamicFloatList_isOverlayOp() {
        Operations.resetReaders()
        Builtins.register()
        // DYNAMIC_FLOAT_LIST resolves only under the androidx/widgets overlays, never base.
        assertFalse(Operations.isValid(Operations.DYNAMIC_FLOAT_LIST, 7, 0))
        assertFalse(Operations.isValid(Operations.DYNAMIC_FLOAT_LIST, 6, 0))
        assertTrue(Operations.isValid(Operations.DYNAMIC_FLOAT_LIST, 7, Operations.PROFILE_ANDROIDX))
        assertTrue(Operations.isValid(Operations.DYNAMIC_FLOAT_LIST, 7, Operations.PROFILE_WIDGETS))
    }
}
