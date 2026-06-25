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

import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.Builtins
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.TextFromFloat
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

/**
 * TEXT_FROM_FLOAT (op 135) — the F3 blocker: `small_animated.rc` renders a float as text
 * (`continuousSeconds().format()`). The byte golden is anchored to the real bytes at offset 51.
 */
class TextFromFloatTest {

    @AfterTest
    fun cleanup() {
        Operations.resetReaders()
    }

    // Hand-extracted from small_animated.rc, offset 0x33 (51), 17 bytes:
    // 87  00 00 00 2A  FF 80 00 01  00 02 00 02  00 00 00 00
    // opcode 135, textId 42, value = asNan(1) (the continuousSeconds variable id), digits 2.2, flags 0.
    private val golden = bytes(
        0x87,
        0x00, 0x00, 0x00, 0x2A, // textId = 42
        0xFF, 0x80, 0x00, 0x01, // value raw bits = asNan(1)
        0x00, 0x02, 0x00, 0x02, // digitsBefore=2 (hi), digitsAfter=2 (lo)
        0x00, 0x00, 0x00, 0x00, // flags = 0
    )

    @Test
    fun writesExactSmallAnimatedBytes() {
        val buffer = WireBuffer()
        TextFromFloat(textId = 42, value = WireTypes.asNan(1), digitsBefore = 2, digitsAfter = 2, flags = 0)
            .write(buffer)
        assertContentEquals(golden, buffer.toByteArray())
    }

    @Test
    fun readMirrorsWrite() {
        val op = TextFromFloat(7, WireTypes.asNan(3), digitsBefore = 4, digitsAfter = 1, flags = 0x20)
        val buffer = WireBuffer()
        op.write(buffer)
        buffer.byteIndex = 0
        assertEquals(Operations.TEXT_FROM_FLOAT, buffer.readByte())
        val ops = mutableListOf<Operation>()
        TextFromFloat.read(buffer, ops)
        assertEquals(op, ops.single())
    }

    @Test
    fun resolvesInDocument_theF3Scenario() {
        // Minimal document: flat header + TEXT_FROM_FLOAT(42, asNan(1), 2, 2, 0). small_animated
        // previously died with "unknown opcode 135 at byte 51"; now it decodes.
        val header = bytes(
            0x00,
            0x00, 0x00, 0x00, 0x01, // major 1
            0x00, 0x00, 0x00, 0x00, // minor 0
            0x00, 0x00, 0x00, 0x00, // patch 0
            0x00, 0x00, 0x01, 0x00, // width 256
            0x00, 0x00, 0x01, 0x00, // height 256
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // capabilities
        )
        val doc = DocumentReader.inflate(header + golden)
        assertEquals(2, doc.operations.size)
        assertEquals(
            TextFromFloat(42, WireTypes.asNan(1), 2, 2, 0),
            doc.operations[1],
        )
    }

    @Test
    fun isRegisteredInBothBaseLayers() {
        Operations.resetReaders()
        Builtins.register()
        assertTrue(Operations.isValid(Operations.TEXT_FROM_FLOAT, 6, 0))
        assertTrue(Operations.isValid(Operations.TEXT_FROM_FLOAT, 7, 0))
    }
}
