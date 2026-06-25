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
package com.tneff.kmpremotecompose.remote.core.operations.draw

import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Synthetic per-op byte tests for the REM-5 float draw ops (DrawBaseN family).
 *
 * The expected bytes are hand-computed big-endian IEEE-754 constants — an oracle independent of the
 * writer. DrawCircle uses `150f` (`0x43160000`), the exact value that appears in the upstream
 * `procedure_simple1.rc` golden (`2e 43160000 43160000 43160000`), anchoring the encoding.
 */
class DrawOpsByteTest {

    @AfterTest
    fun cleanup() {
        Operations.resetReaders()
    }

    // Big-endian IEEE-754 byte constants for the float values used below.
    // 1f=3F800000  2f=40000000  3f=40400000  4f=40800000  150f=43160000
    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun writeBytes(op: Operation): ByteArray {
        val buffer = WireBuffer()
        op.write(buffer)
        return buffer.toByteArray()
    }

    // ---------------------------------------------------------------------------------------------
    // Byte-exact write (oracle = hand-computed hex).
    // ---------------------------------------------------------------------------------------------

    @Test
    fun drawCircle_writesExactBytes() {
        // opcode 46 (0x2E) + 3× 150f. Matches procedure_simple1.rc golden snippet.
        val expected = bytes(
            0x2E,
            0x43, 0x16, 0x00, 0x00,
            0x43, 0x16, 0x00, 0x00,
            0x43, 0x16, 0x00, 0x00,
        )
        assertContentEquals(expected, writeBytes(DrawCircle(150f, 150f, 150f)))
    }

    @Test
    fun drawRect_writesExactBytes() {
        // opcode 42 (0x2A) + 1f,2f,3f,4f — distinct values catch field-order bugs.
        val expected = bytes(
            0x2A,
            0x3F, 0x80, 0x00, 0x00,
            0x40, 0x00, 0x00, 0x00,
            0x40, 0x40, 0x00, 0x00,
            0x40, 0x80, 0x00, 0x00,
        )
        assertContentEquals(expected, writeBytes(DrawRect(1f, 2f, 3f, 4f)))
    }

    @Test
    fun drawLine_writesExactBytes() {
        val expected = bytes(
            0x2F,
            0x3F, 0x80, 0x00, 0x00,
            0x40, 0x00, 0x00, 0x00,
            0x40, 0x40, 0x00, 0x00,
            0x40, 0x80, 0x00, 0x00,
        )
        assertContentEquals(expected, writeBytes(DrawLine(1f, 2f, 3f, 4f)))
    }

    @Test
    fun drawOval_writesExactBytes() {
        val expected = bytes(
            0x38,
            0x3F, 0x80, 0x00, 0x00,
            0x40, 0x00, 0x00, 0x00,
            0x40, 0x40, 0x00, 0x00,
            0x40, 0x80, 0x00, 0x00,
        )
        assertContentEquals(expected, writeBytes(DrawOval(1f, 2f, 3f, 4f)))
    }

    // 6-float draws: 1f..6f = 3F800000,40000000,40400000,40800000,40A00000,40C00000
    private fun sixFloatBytes(opcode: Int): ByteArray = bytes(
        opcode,
        0x3F, 0x80, 0x00, 0x00,
        0x40, 0x00, 0x00, 0x00,
        0x40, 0x40, 0x00, 0x00,
        0x40, 0x80, 0x00, 0x00,
        0x40, 0xA0, 0x00, 0x00,
        0x40, 0xC0, 0x00, 0x00,
    )

    @Test
    fun drawRoundRect_writesExactBytes() {
        assertContentEquals(sixFloatBytes(0x33), writeBytes(DrawRoundRect(1f, 2f, 3f, 4f, 5f, 6f)))
    }

    @Test
    fun drawArc_writesExactBytes() {
        assertContentEquals(sixFloatBytes(0x98), writeBytes(DrawArc(1f, 2f, 3f, 4f, 5f, 6f)))
    }

    @Test
    fun drawSector_writesExactBytes() {
        assertContentEquals(sixFloatBytes(0x34), writeBytes(DrawSector(1f, 2f, 3f, 4f, 5f, 6f)))
    }

    // ---------------------------------------------------------------------------------------------
    // Round-trip: write → read (opcode consumed by loop) → re-write byte-identical + fields equal.
    // ---------------------------------------------------------------------------------------------

    private fun assertRoundTrips(op: Operation) {
        val encoded = writeBytes(op)
        val buffer = WireBuffer.fromBytes(encoded)
        assertEquals(op.opcode, buffer.readByte(), "opcode byte")
        val decoded = mutableListOf<Operation>()
        when (op) {
            is DrawCircle -> DrawCircle.read(buffer, decoded)
            is DrawRect -> DrawRect.read(buffer, decoded)
            is DrawLine -> DrawLine.read(buffer, decoded)
            is DrawOval -> DrawOval.read(buffer, decoded)
            is DrawRoundRect -> DrawRoundRect.read(buffer, decoded)
            is DrawArc -> DrawArc.read(buffer, decoded)
            is DrawSector -> DrawSector.read(buffer, decoded)
            else -> error("unexpected op $op")
        }
        assertEquals(1, decoded.size)
        assertEquals(op, decoded[0], "decoded op equals original")
        assertFalse(buffer.available(), "all bytes consumed")
        assertContentEquals(encoded, writeBytes(decoded[0]), "re-encode byte-identical")
    }

    @Test
    fun allDrawOps_roundTrip() {
        assertRoundTrips(DrawCircle(150f, 150f, 150f))
        assertRoundTrips(DrawRect(1f, 2f, 3f, 4f))
        assertRoundTrips(DrawLine(-1.5f, 0f, 12.25f, 1000f))
        assertRoundTrips(DrawOval(1f, 2f, 3f, 4f))
        assertRoundTrips(DrawRoundRect(1f, 2f, 3f, 4f, 8f, 8f))
        assertRoundTrips(DrawArc(0f, 0f, 100f, 100f, 45f, 270f))
        assertRoundTrips(DrawSector(0f, 0f, 50f, 50f, -90f, 180f))
    }

    /** Model equality must use raw float bits, so two ops with identical NaN-id bits are equal. */
    @Test
    fun nanEncodedId_modelEqualityUsesRawBits() {
        val id = WireTypes.asNan(0x2A)
        // Naive Float `==` would make these unequal (NaN != NaN); toRawBits equality makes them equal.
        assertEquals(DrawCircle(id, 7f, id), DrawCircle(id, 7f, id))
        assertEquals(DrawCircle(id, 7f, id).hashCode(), DrawCircle(id, 7f, id).hashCode())
        // Different ids stay distinct.
        assertNotEquals(DrawCircle(id, 7f, id), DrawCircle(WireTypes.asNan(0x2B), 7f, id))
    }

    /** A float field carrying a NaN-encoded variable id must survive read→write bit-for-bit. */
    @Test
    fun nanEncodedId_survivesRoundTripRaw() {
        val idFloat = WireTypes.asNan(0x2A) // id 42 as a NaN float
        val encoded = writeBytes(DrawCircle(idFloat, 7f, idFloat))
        val buffer = WireBuffer.fromBytes(encoded)
        buffer.readByte() // opcode
        val decoded = mutableListOf<Operation>()
        DrawCircle.read(buffer, decoded)
        val circle = decoded[0] as DrawCircle
        // Raw bits preserved (NaN != NaN, so compare via re-encode byte-equality + id payload).
        assertContentEquals(encoded, writeBytes(circle))
        assertEquals(0x2A, WireTypes.idFromNan(circle.centerX))
    }

    // ---------------------------------------------------------------------------------------------
    // Registration: draw ops resolve in V6 and V7 base (profile-independent).
    // ---------------------------------------------------------------------------------------------

    @Test
    fun register_putsDrawOpsInBaseLayers() {
        Operations.resetReaders()
        DrawOps.register()
        val drawOpcodes = listOf(
            Operations.DRAW_CIRCLE, Operations.DRAW_RECT, Operations.DRAW_LINE, Operations.DRAW_OVAL,
            Operations.DRAW_ROUND_RECT, Operations.DRAW_ARC, Operations.DRAW_SECTOR,
        )
        for (op in drawOpcodes) {
            assertTrue(Operations.isValid(op, 6, Operations.PROFILE_BASELINE), "v6 ${Operations.name(op)}")
            assertTrue(Operations.isValid(op, 7, Operations.PROFILE_BASELINE), "v7 base ${Operations.name(op)}")
            assertTrue(Operations.isValid(op, 7, Operations.PROFILE_ANDROIDX), "v7 androidx ${Operations.name(op)}")
        }
    }
}
