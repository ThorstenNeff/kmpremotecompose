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
import com.tneff.kmpremotecompose.remote.core.operations.BitmapData
import com.tneff.kmpremotecompose.remote.core.operations.ColorConstant
import com.tneff.kmpremotecompose.remote.core.operations.FloatConstant
import com.tneff.kmpremotecompose.remote.core.operations.IntegerConstant
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.OperationReader
import com.tneff.kmpremotecompose.remote.core.operations.TextData
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

/** Write [op], rewind, consume the opcode and decode via [reader] — proves read mirrors write. */
private fun reread(op: Operation, reader: OperationReader): Operation {
    val buffer = WireBuffer()
    op.write(buffer)
    buffer.byteIndex = 0
    assertEquals(op.opcode, buffer.readByte())
    val decoded = mutableListOf<Operation>()
    reader.read(buffer, decoded)
    return decoded.single()
}

private fun encoded(op: Operation): ByteArray {
    val buffer = WireBuffer()
    op.write(buffer)
    return buffer.toByteArray()
}

class DataOpsTest {

    // ---------------------------------------------------------------------------------------------
    // Per-op synthetic byte goldens (write → exact bytes). Each op stands alone, no reader needed.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun dataText_writesExactGoldenBytes() {
        // Matches the real screenshottest.rc DATA_TEXT op at offsets 0x36..0x45.
        assertContentEquals(
            bytes(0x66, 0x00, 0x00, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x07, 0x49, 0x6E, 0x69, 0x74, 0x69, 0x61, 0x6C),
            encoded(TextData(42, "Initial")),
        )
    }

    @Test
    fun dataFloat_writesExactBytes() {
        assertContentEquals(
            bytes(0x50, 0x00, 0x00, 0x00, 0x01, 0x3F, 0x80, 0x00, 0x00),
            encoded(FloatConstant(1, 1.0f)),
        )
    }

    @Test
    fun dataFloat_preservesNanIdBytes() {
        // A NaN-encoded id stored as the float value must survive byte-for-byte (raw bits).
        assertContentEquals(
            bytes(0x50, 0x00, 0x00, 0x00, 0x00, 0xFF, 0x80, 0x00, 0x2A),
            encoded(FloatConstant(0, WireTypes.asNan(42))),
        )
    }

    @Test
    fun dataInt_writesExactBytes() {
        assertContentEquals(
            bytes(0x8C, 0x00, 0x00, 0x00, 0x01, 0x0A, 0x0B, 0x0C, 0x0D),
            encoded(IntegerConstant(1, 0x0A0B0C0D)),
        )
    }

    @Test
    fun colorConstant_writesExactBytes() {
        assertContentEquals(
            bytes(0x8A, 0x00, 0x00, 0x00, 0x01, 0xFF, 0x11, 0x22, 0x33),
            encoded(ColorConstant(1, 0xFF112233.toInt())),
        )
    }

    @Test
    fun bitmapData_writesExactBytes() {
        assertContentEquals(
            bytes(
                0x65, // opcode
                0x00, 0x00, 0x00, 0x07, // imageId
                0x00, 0x00, 0x00, 0x02, // width
                0x00, 0x00, 0x00, 0x02, // height
                0x00, 0x00, 0x00, 0x04, // buffer length
                0xDE, 0xAD, 0xBE, 0xEF, // pixels
            ),
            encoded(BitmapData(7, 2, 2, bytes(0xDE, 0xAD, 0xBE, 0xEF))),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Per-op read round-trip (read mirrors write).
    // ---------------------------------------------------------------------------------------------

    @Test
    fun eachOp_readMirrorsWrite() {
        assertEquals(TextData(42, "Initial"), reread(TextData(42, "Initial"), TextData))
        assertEquals(TextData(7, "äöü€"), reread(TextData(7, "äöü€"), TextData)) // non-ASCII UTF-8
        assertEquals(FloatConstant(1, 1.0f), reread(FloatConstant(1, 1.0f), FloatConstant))
        assertEquals(
            FloatConstant(0, WireTypes.asNan(42)),
            reread(FloatConstant(0, WireTypes.asNan(42)), FloatConstant),
        )
        assertEquals(IntegerConstant(1, -123456), reread(IntegerConstant(1, -123456), IntegerConstant))
        assertEquals(
            ColorConstant(1, 0xFF112233.toInt()),
            reread(ColorConstant(1, 0xFF112233.toInt()), ColorConstant),
        )
        assertEquals(
            BitmapData(7, 2, 2, bytes(0xDE, 0xAD, 0xBE, 0xEF)),
            reread(BitmapData(7, 2, 2, bytes(0xDE, 0xAD, 0xBE, 0xEF)), BitmapData),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Full document round-trip through the facades (writer → reader).
    // ---------------------------------------------------------------------------------------------

    @Test
    fun fullDocument_roundTripsThroughFacades() {
        val writer = RemoteComposeWriter(width = 100, height = 100)
        writer.add(TextData(42, "Initial"))
        writer.add(FloatConstant(7, WireTypes.asNan(3)))
        writer.add(IntegerConstant(8, -1))
        writer.add(ColorConstant(9, 0xFF112233.toInt()))
        writer.add(BitmapData(10, 1, 1, bytes(0x7F)))

        val doc = DocumentReader.inflate(writer.encodeToByteArray())

        assertEquals(6, doc.operations.size) // header + 5 data ops
        assertEquals(TextData(42, "Initial"), doc.operations[1])
        assertEquals(FloatConstant(7, WireTypes.asNan(3)), doc.operations[2])
        assertEquals(IntegerConstant(8, -1), doc.operations[3])
        assertEquals(ColorConstant(9, 0xFF112233.toInt()), doc.operations[4])
        assertEquals(BitmapData(10, 1, 1, bytes(0x7F)), doc.operations[5])
    }

    @Test
    fun debugTrace_coversEveryDataOp() {
        val writer = RemoteComposeWriter(width = 100, height = 100)
        writer.add(TextData(42, "Initial"))
        writer.add(IntegerConstant(8, -1))

        val (_, spans) = DocumentReader.inflateWithTrace(writer.encodeToByteArray())
        assertEquals(3, spans.size) // header + 2 ops
        // Byte ranges are contiguous and non-overlapping, ending where the next begins.
        assertEquals(spans[0].byteEnd, spans[1].byteStart)
        assertEquals(spans[1].byteEnd, spans[2].byteStart)
        assertEquals("DATA_TEXT", spans[1].name)
        assertEquals("DATA_INT", spans[2].name)
    }
}
