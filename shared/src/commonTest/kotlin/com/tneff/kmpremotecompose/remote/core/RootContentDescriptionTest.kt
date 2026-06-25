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
import com.tneff.kmpremotecompose.remote.core.operations.RootContentDescription
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

/**
 * ROOT_CONTENT_DESCRIPTION (op 103) — the F1 blocker: an API-6 (flat) document encodes the content
 * description as this operation (it was a header TLV in the API-7 fixture, hence missed). The byte
 * golden is anchored to the real `procedure_simple1.rc` op at offset 43 (not self-referential).
 */
class RootContentDescriptionTest {

    @AfterTest
    fun cleanup() {
        Operations.resetReaders()
    }

    @Test
    fun writesExactProcedureSimple1Bytes() {
        // Hand-extracted from procedure_simple1.rc, offset 0x2B (43): 67 00 00 00 2A — opcode 103,
        // contentDescription id 42 (references the "Clock" DATA_TEXT in that document).
        val buffer = WireBuffer()
        RootContentDescription(42).write(buffer)
        assertContentEquals(bytes(0x67, 0x00, 0x00, 0x00, 0x2A), buffer.toByteArray())
    }

    @Test
    fun readMirrorsWrite() {
        val buffer = WireBuffer()
        RootContentDescription(42).write(buffer)
        buffer.byteIndex = 0
        assertEquals(Operations.ROOT_CONTENT_DESCRIPTION, buffer.readByte())
        val ops = mutableListOf<Operation>()
        RootContentDescription.read(buffer, ops)
        assertEquals(RootContentDescription(42), ops.single())
    }

    @Test
    fun resolvesInApi6Document_theF1Scenario() {
        // Minimal API-6 flat document: header v1.0.0 (256x256) + ROOT_CONTENT_DESCRIPTION(42).
        // procedure_simple1 previously died here with "unknown opcode 103 at byte 43"; now it decodes.
        val doc = DocumentReader.inflate(
            bytes(
                0x00, // HEADER opcode
                0x00, 0x00, 0x00, 0x01, // major = 1
                0x00, 0x00, 0x00, 0x00, // minor = 0  → api 6
                0x00, 0x00, 0x00, 0x00, // patch = 0
                0x00, 0x00, 0x01, 0x00, // width = 256
                0x00, 0x00, 0x01, 0x00, // height = 256
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // capabilities
                0x67, 0x00, 0x00, 0x00, 0x2A, // ROOT_CONTENT_DESCRIPTION(42)
            ),
        )
        assertEquals(2, doc.operations.size)
        assertEquals(6, doc.apiLevel)
        assertEquals(RootContentDescription(42), doc.operations[1])
    }

    @Test
    fun isRegisteredInBothBaseLayers() {
        Operations.resetReaders()
        Builtins.register()
        assertTrue(Operations.isValid(Operations.ROOT_CONTENT_DESCRIPTION, 6, 0))
        assertTrue(Operations.isValid(Operations.ROOT_CONTENT_DESCRIPTION, 7, 0))
    }
}
