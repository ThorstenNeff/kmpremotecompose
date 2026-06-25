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
import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeWriter
import com.tneff.kmpremotecompose.remote.core.operations.Header
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

/**
 * The header writer must preserve a round-tripped document's PARSED version, not stamp the lib
 * constants (the F1 blocker: flat v1.0.0 re-encoded as v1.1.0 diverged at offset 8).
 */
class HeaderVersionTest {

    @AfterTest
    fun cleanup() {
        Operations.resetReaders()
    }

    // Flat header (API < 7), v1.0.0, 256x256, capabilities 0 — 29 bytes.
    private val flatV100 = bytes(
        0x00, // opcode
        0x00, 0x00, 0x00, 0x01, // major = 1
        0x00, 0x00, 0x00, 0x00, // minor = 0
        0x00, 0x00, 0x00, 0x00, // patch = 0
        0x00, 0x00, 0x01, 0x00, // width = 256
        0x00, 0x00, 0x01, 0x00, // height = 256
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // capabilities (long) = 0
    )

    @Test
    fun flatHeaderV100_roundTripsByteExact() {
        val doc = DocumentReader.inflate(flatV100)
        val header = doc.header!!
        // Parsed version is preserved on the instance...
        assertEquals(1, header.major)
        assertEquals(0, header.minor)
        assertEquals(0, header.patch)
        // ...and re-encoding reproduces the original bytes (previously diverged: minor/patch were
        // stamped as 1/0 → v1.1.0).
        val out = WireBuffer()
        header.write(out)
        assertContentEquals(flatV100, out.toByteArray())
    }

    @Test
    fun newWriter_stampsCurrentVersion() {
        // A freshly authored document stamps the current lib version (map form, v1.1.0 ⇒ api 7).
        val header = DocumentReader.inflate(
            RemoteComposeWriter(width = 100, height = 100).encodeToByteArray(),
        ).header!!
        assertEquals(Header.MAJOR_VERSION, header.major)
        assertEquals(Header.MINOR_VERSION, header.minor)
        assertEquals(Header.PATCH_VERSION, header.patch)
        assertEquals(7, header.apiLevel)
    }
}
