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
package com.tneff.kmpremotecompose.remote.wire

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Bytes as unsigned ints, for readable expected-byte assertions. */
private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

class WireBufferTest {

    // ---------------------------------------------------------------------------------------------
    // Endianness — fixed-width big-endian (acceptance gate).
    // ---------------------------------------------------------------------------------------------

    @Test
    fun writeInt_isBigEndian() {
        val buf = WireBuffer(16)
        buf.writeInt(0x01020304)
        assertContentEquals(bytes(0x01, 0x02, 0x03, 0x04), buf.toByteArray())
    }

    @Test
    fun writeShort_isBigEndian() {
        val buf = WireBuffer(16)
        buf.writeShort(0x0102)
        assertContentEquals(bytes(0x01, 0x02), buf.toByteArray())
    }

    @Test
    fun writeLong_isBigEndian() {
        val buf = WireBuffer(16)
        buf.writeLong(0x0102030405060708L)
        assertContentEquals(bytes(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08), buf.toByteArray())
    }

    @Test
    fun primitives_roundTrip() {
        val buf = WireBuffer(64)
        buf.writeByte(0xAB)
        buf.writeBoolean(true)
        buf.writeBoolean(false)
        buf.writeShort(0xBEEF)
        buf.writeInt(-123456)
        buf.writeLong(-9_000_000_000L)
        buf.writeDouble(3.14159)
        buf.byteIndex = 0
        assertEquals(0xAB, buf.readByte())
        assertTrue(buf.readBoolean())
        assertTrue(!buf.readBoolean())
        assertEquals(0xBEEF, buf.readShort())
        assertEquals(-123456, buf.readInt())
        assertEquals(-9_000_000_000L, buf.readLong())
        assertEquals(3.14159, buf.readDouble())
    }

    // ---------------------------------------------------------------------------------------------
    // Float / NaN-id — raw bits round-trip; canonicalizing toBits() breaks (acceptance gate).
    // ---------------------------------------------------------------------------------------------

    @Test
    fun nanId_writesExactUpstreamBytes() {
        // asNan(42) = 0xFF80002A — a *signaling* NaN (mantissa MSB clear, payload non-zero).
        // Pin the absolute encoded bytes against the upstream bit pattern, NOT toRawBits()-vs-
        // toRawBits() (which both pass through the same Kotlin/Native handling) and NOT idFromNan()
        // (which masks out bit 22, the quiet/signaling bit). If Kotlin/Native quiets the NaN on iOS
        // (0xFF80002A -> 0xFFC0002A) the writer would emit wrong bytes; this assertion catches that
        // here instead of silently in the player.
        val buf = WireBuffer(16)
        buf.writeFloat(WireTypes.asNan(42))
        assertContentEquals(bytes(0xFF, 0x80, 0x00, 0x2A), buf.toByteArray())
    }

    @Test
    fun nanId_roundTripsBitExact() {
        val id = 42
        val nan = WireTypes.asNan(id)

        val buf = WireBuffer(16)
        buf.writeFloat(nan)
        buf.byteIndex = 0
        val read = buf.readFloat()

        // Round-trip through the buffer preserves the raw payload and decodes the id back.
        assertEquals(nan.toRawBits(), read.toRawBits())
        assertEquals(id, WireTypes.idFromNan(read))
    }

    @Test
    fun signalingNaN_doublePath_writesExactBytes() {
        // Same risk on the 8-byte path: a signaling double NaN (0x7FF0000000000001) must survive
        // Double.toRawBits() byte-for-byte. Quieting on Kotlin/Native would flip byte 1 to 0xF8.
        val buf = WireBuffer(16)
        buf.writeDouble(Double.fromBits(0x7FF0000000000001L))
        assertContentEquals(
            bytes(0x7F, 0xF0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01),
            buf.toByteArray(),
        )
    }

    @Test
    fun nanId_canonicalizingToBitsDestroysPayload() {
        val id = 42
        val nan = WireTypes.asNan(id)

        // The canonicalizing Float.toBits() collapses the NaN payload — proof that the encode side
        // must use raw bits. If WireBuffer ever switched to toBits(), this id would be lost.
        assertNotEquals(nan.toRawBits(), nan.toBits())
        val canonicalized = Float.fromBits(nan.toBits())
        assertNotEquals(id, WireTypes.idFromNan(canonicalized))
    }

    @Test
    fun ordinaryFloat_roundTrips() {
        val buf = WireBuffer(16)
        buf.writeFloat(1.0f)
        assertContentEquals(bytes(0x3F, 0x80, 0x00, 0x00), buf.toByteArray())
        buf.byteIndex = 0
        assertEquals(1.0f, buf.readFloat())
    }

    // ---------------------------------------------------------------------------------------------
    // Strings — 4-byte big-endian length + UTF-8 (acceptance gate + UTF-8 pinning).
    // ---------------------------------------------------------------------------------------------

    @Test
    fun writeUTF8_ascii_isLengthPrefixedBytes() {
        val buf = WireBuffer(32)
        buf.writeUTF8("Initial")
        assertContentEquals(
            bytes(0x00, 0x00, 0x00, 0x07, 0x49, 0x6E, 0x69, 0x74, 0x69, 0x61, 0x6C),
            buf.toByteArray(),
        )
    }

    @Test
    fun writeUTF8_isHardUtf8_notPlatformCharset() {
        // "ä" is one UTF-8 sequence of two bytes (0xC3 0xA4); length must be 2, never 1.
        val buf = WireBuffer(16)
        buf.writeUTF8("ä")
        assertContentEquals(bytes(0x00, 0x00, 0x00, 0x02, 0xC3, 0xA4), buf.toByteArray())
        buf.byteIndex = 0
        assertEquals("ä", buf.readUTF8())
    }

    @Test
    fun readUTF8_guard_rejectsOversizedLength() {
        val buf = WireBuffer(16)
        buf.writeInt(WireTypes.MAX_STRING_SIZE + 1)
        buf.byteIndex = 0
        assertFailsWith<IllegalStateException> { buf.readUTF8(WireTypes.MAX_STRING_SIZE) }
    }

    // ---------------------------------------------------------------------------------------------
    // Cursor: peek, back-patch.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun peekInt_doesNotAdvanceCursor() {
        val buf = WireBuffer(16)
        buf.writeInt(0x11223344)
        buf.byteIndex = 0
        assertEquals(0x11223344, buf.peekInt())
        assertEquals(0, buf.byteIndex)
        assertEquals(0x11223344, buf.readInt())
        assertEquals(4, buf.byteIndex)
    }

    @Test
    fun overwriteInt_backPatchesWithoutMovingCursorOrSize() {
        val buf = WireBuffer(16)
        buf.writeInt(0) // placeholder
        buf.writeInt(0x0A0B0C0D)
        val sizeBefore = buf.size
        buf.overwriteInt(0, 0x01020304)
        assertEquals(sizeBefore, buf.size)
        buf.byteIndex = 0
        assertEquals(0x01020304, buf.readInt())
        assertEquals(0x0A0B0C0D, buf.readInt())
    }

    // ---------------------------------------------------------------------------------------------
    // Conformance smoke test against HAND-EXTRACTED golden bytes.
    // These bytes were read by hand from a hexdump of the upstream screenshottest.rc DOC_WIDTH header
    // TLV (offsets 0x11..0x18: short tag(5), short len(4), int 320) and confirmed against the oracle.
    // Loading the real .rc fixture at test time is deferred to REM-7 (corpus loader); for now we pin
    // the known-good bytes so the primitives are proven byte-exact without the file dependency.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun primitives_matchHandExtractedGoldenWidthTlv() {
        val buf = WireBuffer(16)
        buf.writeShort(WireTypes.DATA_TYPE_INT shl WireTypes.TAG_TYPE_SHIFT or 5) // key 5 = DOC_WIDTH
        buf.writeShort(4)
        buf.writeInt(320)
        assertContentEquals(bytes(0x00, 0x05, 0x00, 0x04, 0x00, 0x00, 0x01, 0x40), buf.toByteArray())
    }
}
