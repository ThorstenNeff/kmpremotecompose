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
package com.tneff.kmpremotecompose.remote.core.operations.layout

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

/**
 * Synthetic per-op byte tests for the REM-5 layout / container / modifier ops.
 *
 * Several expected-byte vectors are taken from the hand-verified `screenshottest.rc` golden:
 * `LAYOUT_ROOT id=-2`, `MODIFIER_WIDTH FILL 1.0`, `MODIFIER_HEIGHT FILL 1.0` appear there verbatim.
 */
class LayoutOpsByteTest {

    @AfterTest
    fun cleanup() {
        Operations.resetReaders()
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun writeBytes(op: Operation): ByteArray {
        val buffer = WireBuffer()
        op.write(buffer)
        return buffer.toByteArray()
    }

    // ---------------------------------------------------------------------------------------------
    // Byte-exact write.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun rootLayout_writesExactBytes() {
        // opcode 200 (0xC8) + int -2 (0xFFFFFFFE) — matches screenshottest LAYOUT_ROOT(-2).
        assertContentEquals(bytes(0xC8, 0xFF, 0xFF, 0xFF, 0xFE), writeBytes(RootLayout(-2)))
    }

    @Test
    fun containerEnd_writesOpcodeOnly() {
        assertContentEquals(bytes(0xD6), writeBytes(ContainerEnd()))
    }

    @Test
    fun componentStart_writesExactBytes() {
        // opcode 2 + int type=1 + int componentId=7
        assertContentEquals(
            bytes(0x02, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x07),
            writeBytes(ComponentStart(type = 1, componentId = 7)),
        )
    }

    @Test
    fun clickModifier_writesOpcodeOnly() {
        assertContentEquals(bytes(0x3B), writeBytes(ClickModifier()))
    }

    @Test
    fun widthModifier_writesExactBytes() {
        // opcode 16 (0x10) + int FILL(=1) + float 1.0 (0x3F800000) — matches screenshottest.
        assertContentEquals(
            bytes(0x10, 0x00, 0x00, 0x00, 0x01, 0x3F, 0x80, 0x00, 0x00),
            writeBytes(WidthModifier(DimensionType.FILL, 1f)),
        )
    }

    @Test
    fun heightModifier_writesExactBytes() {
        // opcode 67 (0x43) + int FILL(=1) + float 1.0 — matches screenshottest.
        assertContentEquals(
            bytes(0x43, 0x00, 0x00, 0x00, 0x01, 0x3F, 0x80, 0x00, 0x00),
            writeBytes(HeightModifier(DimensionType.FILL, 1f)),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Round-trip: write → read (opcode consumed) → re-write byte-identical + fields equal.
    // ---------------------------------------------------------------------------------------------

    private fun assertRoundTrips(op: Operation, reader: OperationReader) {
        val encoded = writeBytes(op)
        val buffer = WireBuffer.fromBytes(encoded)
        assertEquals(op.opcode, buffer.readByte(), "opcode byte")
        val decoded = mutableListOf<Operation>()
        reader.read(buffer, decoded)
        assertEquals(1, decoded.size)
        assertEquals(op, decoded[0], "decoded equals original")
        assertFalse(buffer.available(), "all bytes consumed")
        assertContentEquals(encoded, writeBytes(decoded[0]), "re-encode byte-identical")
    }

    @Test
    fun allLayoutOps_roundTrip() {
        assertRoundTrips(RootLayout(-2), RootLayout)
        assertRoundTrips(ContainerEnd(), ContainerEnd)
        assertRoundTrips(ComponentStart(3, 11), ComponentStart)
        assertRoundTrips(ClickModifier(), ClickModifier)
        assertRoundTrips(WidthModifier(DimensionType.EXACT, 120f), WidthModifier)
        assertRoundTrips(HeightModifier(DimensionType.WEIGHT, 2.5f), HeightModifier)
    }

    @Test
    fun dimensionType_ordinalsAreWireStable() {
        // Ordinal is the wire value — pin the full set against accidental reordering.
        assertEquals(0, DimensionType.EXACT.ordinal)
        assertEquals(1, DimensionType.FILL.ordinal)
        assertEquals(2, DimensionType.WRAP.ordinal)
        assertEquals(3, DimensionType.WEIGHT.ordinal)
        assertEquals(8, DimensionType.FILL_PARENT_MAX_HEIGHT.ordinal)
        assertEquals(DimensionType.WRAP, DimensionType.fromInt(2))
    }

    // ---------------------------------------------------------------------------------------------
    // Registration: resolve in V6 and V7 base.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun register_putsLayoutOpsInBaseLayers() {
        Operations.resetReaders()
        LayoutOps.register()
        val opcodes = listOf(
            Operations.LAYOUT_ROOT, Operations.CONTAINER_END, Operations.COMPONENT_START,
            Operations.MODIFIER_WIDTH, Operations.MODIFIER_HEIGHT, Operations.MODIFIER_CLICK,
        )
        for (op in opcodes) {
            assertTrue(Operations.isValid(op, 6, Operations.PROFILE_BASELINE), "v6 ${Operations.name(op)}")
            assertTrue(Operations.isValid(op, 7, Operations.PROFILE_BASELINE), "v7 ${Operations.name(op)}")
        }
    }
}
