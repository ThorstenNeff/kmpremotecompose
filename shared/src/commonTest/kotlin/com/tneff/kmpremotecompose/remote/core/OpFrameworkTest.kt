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
import com.tneff.kmpremotecompose.remote.core.operations.OperationReader
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

/** A no-op reader used to exercise the registry composition without depending on REM-4 ops. */
private val NOOP = OperationReader { _, _ -> }

class OpFrameworkTest {

    // Hand-extracted golden header of the upstream screenshottest.rc (offsets 0x00..0x30, 49 bytes):
    // map-form header, magic 0x048C, v1.1.0, 4 props: WIDTH=320, HEIGHT=470, CONTENT_DESCRIPTION="",
    // PROFILES=512 (ANDROIDX). Confirmed against the oracle hexdump.
    private val goldenHeader = bytes(
        0x00,
        0x04, 0x8C, 0x00, 0x01,
        0x00, 0x00, 0x00, 0x01,
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x04,
        0x00, 0x05, 0x00, 0x04, 0x00, 0x00, 0x01, 0x40,
        0x00, 0x06, 0x00, 0x04, 0x00, 0x00, 0x01, 0xD6,
        0x0C, 0x09, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x0E, 0x00, 0x04, 0x00, 0x00, 0x02, 0x00,
    )

    @AfterTest
    fun cleanup() {
        Operations.resetReaders()
    }

    // ---------------------------------------------------------------------------------------------
    // Header round-trip (byte-exact anchor) + document container.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun writer_emitsByteExactGoldenHeader() {
        val writer = RemoteComposeWriter(
            width = 320,
            height = 470,
            profiles = Operations.PROFILE_ANDROIDX, // 512
            contentDescription = "",
        )
        assertContentEquals(goldenHeader, writer.encodeToByteArray())
    }

    @Test
    fun headerOnly_roundTripsThroughReaderAndBack() {
        val encoded = RemoteComposeWriter(320, 470, Operations.PROFILE_ANDROIDX, "").encodeToByteArray()

        val doc = DocumentReader.inflate(encoded)
        assertEquals(1, doc.operations.size)
        assertEquals(320, doc.width)
        assertEquals(470, doc.height)
        assertEquals(7, doc.apiLevel)
        assertEquals(Operations.PROFILE_ANDROIDX, doc.profiles)

        // Re-encoding the read-back header reproduces the original bytes exactly (idempotent).
        val reEncoded = reencodeHeader(doc.header!!)
        assertContentEquals(goldenHeader, reEncoded)
    }

    @Test
    fun emptyBuffer_inflatesToEmptyDocument() {
        val doc = DocumentReader.inflate(ByteArray(0))
        assertEquals(0, doc.operations.size)
        assertEquals(null, doc.header)
    }

    // ---------------------------------------------------------------------------------------------
    // Debug dump (opcode, byteStart, byteEnd, fields).
    // ---------------------------------------------------------------------------------------------

    @Test
    fun debugDump_yieldsSpanPerOperation() {
        val (doc, spans) = DocumentReader.inflateWithTrace(goldenHeader)
        assertEquals(1, doc.operations.size)
        assertEquals(1, spans.size)
        val header = spans[0]
        assertEquals(Operations.HEADER, header.opcode)
        assertEquals("HEADER", header.name)
        assertEquals(0, header.byteStart)
        assertEquals(goldenHeader.size, header.byteEnd) // 49 — whole header consumed
        assertTrue(header.fields.contains("w=320"))
        assertTrue(header.fields.contains("profiles=512"))
    }

    // ---------------------------------------------------------------------------------------------
    // Inflate loop: unknown opcode fails closed with a byte offset.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun unknownOpcode_failsWithByteOffset() {
        // Append opcode 4 (LOAD_BITMAP) — an orphan that is never registered.
        val withOrphan = goldenHeader + byteArrayOf(0x04)
        val ex = assertFailsWith<IllegalStateException> { DocumentReader.inflate(withOrphan) }
        assertTrue(ex.message!!.contains("unknown opcode 4"))
        assertTrue(ex.message!!.contains("byte ${goldenHeader.size}")) // offset of the bad opcode
    }

    // ---------------------------------------------------------------------------------------------
    // Registry: version/profile-gated composition.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun registry_v7BaseAlwaysComposed() {
        Operations.resetReaders()
        Operations.register(Operations.Layer.V7_BASE, Operations.DRAW_LINE, NOOP)
        assertTrue(Operations.readerMapFor(7, Operations.PROFILE_BASELINE).containsKey(Operations.DRAW_LINE))
        assertTrue(Operations.readerMapFor(7, Operations.PROFILE_ANDROIDX).containsKey(Operations.DRAW_LINE))
    }

    @Test
    fun registry_profileOverlayOnlyWhenSelected() {
        Operations.resetReaders()
        Operations.register(Operations.Layer.V7_ANDROIDX, Operations.DRAW_RECT, NOOP)
        assertTrue(Operations.isValid(Operations.DRAW_RECT, 7, Operations.PROFILE_ANDROIDX))
        assertFalse(Operations.isValid(Operations.DRAW_RECT, 7, Operations.PROFILE_BASELINE))
        assertFalse(Operations.isValid(Operations.DRAW_RECT, 7, Operations.PROFILE_WIDGETS))
    }

    @Test
    fun registry_multiProfileIntersectsOverlays() {
        Operations.resetReaders()
        // DRAW_RECT valid in BOTH androidx and widgets; DRAW_CIRCLE only in androidx.
        Operations.register(Operations.Layer.V7_ANDROIDX, Operations.DRAW_RECT, NOOP)
        Operations.register(Operations.Layer.V7_WIDGETS, Operations.DRAW_RECT, NOOP)
        Operations.register(Operations.Layer.V7_ANDROIDX, Operations.DRAW_CIRCLE, NOOP)

        val both = Operations.PROFILE_ANDROIDX or Operations.PROFILE_WIDGETS
        val map = Operations.readerMapFor(7, both)
        assertTrue(map.containsKey(Operations.DRAW_RECT)) // in the intersection
        assertFalse(map.containsKey(Operations.DRAW_CIRCLE)) // androidx-only → excluded
    }

    @Test
    fun registry_v6UsesV6LayerOnly() {
        Operations.resetReaders()
        Operations.register(Operations.Layer.V6, Operations.DRAW_RECT, NOOP)
        Operations.register(Operations.Layer.V7_BASE, Operations.DRAW_CIRCLE, NOOP)
        assertTrue(Operations.readerMapFor(6, 0).containsKey(Operations.DRAW_RECT))
        assertFalse(Operations.readerMapFor(6, 0).containsKey(Operations.DRAW_CIRCLE))
    }

    @Test
    fun registry_androidNativeProfileIsRejected() {
        assertFailsWith<UnsupportedOperationException> {
            Operations.readerMapFor(7, Operations.PROFILE_ANDROID_NATIVE)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Orphan opcodes: never dispatchable, never registrable.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun orphanOpcodes_matchReconcileSet() {
        assertEquals(
            setOf(4, 132, 162, 195, 251, 252, 253, 254, 255),
            Operations.ORPHAN_OPCODES,
        )
    }

    @Test
    fun register_rejectsOrphanOpcode() {
        assertFailsWith<IllegalArgumentException> {
            Operations.register(Operations.Layer.V7_BASE, Operations.LOAD_BITMAP, NOOP)
        }
    }
}

/** Re-encode a header via the same write path the writer uses, returning its bytes. */
private fun reencodeHeader(header: Header): ByteArray {
    val buffer = com.tneff.kmpremotecompose.remote.wire.WireBuffer()
    header.write(buffer)
    return buffer.toByteArray()
}
