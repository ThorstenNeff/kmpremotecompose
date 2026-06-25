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
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Synthetic per-op byte tests for `PAINT_VALUES` ([PaintData]).
 *
 * Wire format = `[opcode][int count][int×count]` — a length-prefixed raw int array (≙ upstream
 * `PaintBundle.writeBundle/readBundle`). Expected bytes follow directly from that spec (count, then
 * each int big-endian), so they are oracle-derived, not impl-derived. The [PaintData.Builder]
 * encodings are anchored to the verified upstream `mArray` population.
 */
class PaintDataByteTest {

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

    @Test
    fun emptyBundle_writesCountZero() {
        // opcode 40 (0x28) + count 0
        assertContentEquals(bytes(0x28, 0x00, 0x00, 0x00, 0x00), writeBytes(PaintData(intArrayOf())))
    }

    @Test
    fun rawIntArray_writesCountThenInts() {
        // opcode + count 2 + two raw ints (COLOR tag=4, then ARGB 0xFF112233)
        val expected = bytes(
            0x28,
            0x00, 0x00, 0x00, 0x02,
            0x00, 0x00, 0x00, 0x04,
            0xFF, 0x11, 0x22, 0x33,
        )
        assertContentEquals(expected, writeBytes(PaintData(intArrayOf(0x04, 0xFF112233.toInt()))))
    }

    @Test
    fun builder_encodesVerifiedAttributeBytes() {
        // color(0xFF112233) -> [4, 0xFF112233]; strokeWidth(2f) -> [5, 0x40000000];
        // style(1) -> [8 | (1 shl 16)] = 0x00010008. count = 5.
        val paint = PaintData.Builder()
            .color(0xFF112233.toInt())
            .strokeWidth(2f)
            .style(1)
            .build()
        val expected = bytes(
            0x28,
            0x00, 0x00, 0x00, 0x05,
            0x00, 0x00, 0x00, 0x04, // COLOR tag
            0xFF, 0x11, 0x22, 0x33, // argb
            0x00, 0x00, 0x00, 0x05, // STROKE_WIDTH tag
            0x40, 0x00, 0x00, 0x00, // 2.0f raw bits
            0x00, 0x01, 0x00, 0x08, // STYLE | (1 shl 16)
        )
        assertContentEquals(expected, writeBytes(paint))
    }

    @Test
    fun roundTrip_preservesArbitraryInts() {
        val arrays = listOf(
            intArrayOf(),
            intArrayOf(0x04, 0xFF112233.toInt()),
            intArrayOf(5, 2.5f.toRawBits(), 8 or (2 shl 16), -1, Int.MIN_VALUE, Int.MAX_VALUE),
            IntArray(300) { it * 7 - 1 }, // larger bundle, still <= 1024
        )
        for (arr in arrays) {
            val encoded = writeBytes(PaintData(arr))
            val buffer = WireBuffer.fromBytes(encoded)
            assertEquals(Operations.PAINT_VALUES, buffer.readByte(), "opcode")
            val decoded = mutableListOf<Operation>()
            PaintData.read(buffer, decoded)
            assertEquals(1, decoded.size)
            assertEquals(PaintData(arr), decoded[0], "decoded equals original")
            assertFalse(buffer.available(), "all bytes consumed")
            assertContentEquals(encoded, writeBytes(decoded[0]), "re-encode byte-identical")
        }
    }

    @Test
    fun read_rejectsCorruptLength() {
        // count = 1025 (> MAX_BUNDLE_INTS) must fail closed.
        val buffer = WireBuffer().apply { writeInt(PaintData.MAX_BUNDLE_INTS + 1) }
        val replay = WireBuffer.fromBytes(buffer.toByteArray())
        assertFailsWith<IllegalArgumentException> { PaintData.read(replay, mutableListOf()) }
    }

    @Test
    fun register_putsPaintInBaseLayers() {
        Operations.resetReaders()
        DrawOps.register()
        assertTrue(Operations.isValid(Operations.PAINT_VALUES, 6, Operations.PROFILE_BASELINE))
        assertTrue(Operations.isValid(Operations.PAINT_VALUES, 7, Operations.PROFILE_BASELINE))
    }
}
