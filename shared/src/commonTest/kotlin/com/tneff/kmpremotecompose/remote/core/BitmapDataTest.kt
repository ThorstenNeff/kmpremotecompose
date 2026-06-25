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

import com.tneff.kmpremotecompose.remote.core.operations.BitmapData
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

private fun encoded(op: Operation): ByteArray {
    val buffer = WireBuffer()
    op.write(buffer)
    return buffer.toByteArray()
}

/**
 * Decode a DATA_BITMAP body (opcode already consumed) from raw field words. Used only for
 * fail-closed cases, which all reject before any pixel bytes are read, so no payload is needed.
 */
private fun readBitmapBody(imageId: Int, widthWord: Int, heightWord: Int, declaredLen: Int): List<Operation> {
    val b = WireBuffer()
    b.writeInt(imageId)
    b.writeInt(widthWord)
    b.writeInt(heightWord)
    b.writeInt(declaredLen) // crafted buffer length prefix
    b.byteIndex = 0
    val ops = mutableListOf<Operation>()
    BitmapData.read(b, ops)
    return ops
}

class BitmapDataTest {

    // ---------------------------------------------------------------------------------------------
    // Default form stays byte-identical (regression): type=PNG_8888, encoding=INLINE, dims ≤ 0xFFFF.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun defaultForm_isByteIdenticalToNaiveWidthHeight() {
        assertContentEquals(
            bytes(
                0x65, // opcode
                0x00, 0x00, 0x00, 0x07, // imageId
                0x00, 0x00, 0x00, 0x02, // widthWord = (0<<16)|2 == 2
                0x00, 0x00, 0x00, 0x02, // heightWord = (0<<16)|2 == 2
                0x00, 0x00, 0x00, 0x04, // buffer length
                0xDE, 0xAD, 0xBE, 0xEF,
            ),
            encoded(BitmapData(7, 2, 2, bytes(0xDE, 0xAD, 0xBE, 0xEF))),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Packed non-default form: type/encoding go into the high 16 bits.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun packedForm_writesTypeAndEncodingInHighBits() {
        // type=TYPE_PNG(1) → widthWord=0x00010003; encoding=ENCODING_EMPTY(3) → heightWord=0x00030004.
        val op = BitmapData(
            imageId = 9, width = 3, height = 4, data = bytes(0x01, 0x02),
            type = BitmapData.TYPE_PNG, encoding = BitmapData.ENCODING_EMPTY,
        )
        assertContentEquals(
            bytes(
                0x65,
                0x00, 0x00, 0x00, 0x09, // imageId
                0x00, 0x01, 0x00, 0x03, // (1<<16)|3
                0x00, 0x03, 0x00, 0x04, // (3<<16)|4
                0x00, 0x00, 0x00, 0x02, // buffer length
                0x01, 0x02,
            ),
            encoded(op),
        )
    }

    @Test
    fun packedForm_readMirrorsWrite() {
        for (op in listOf(
            BitmapData(7, 2, 2, bytes(0xDE, 0xAD, 0xBE, 0xEF)), // default
            BitmapData(9, 3, 4, bytes(0x01, 0x02), BitmapData.TYPE_PNG, BitmapData.ENCODING_EMPTY),
            BitmapData(11, 16, 16, ByteArray(8) { it.toByte() }, BitmapData.TYPE_RAW8888, BitmapData.ENCODING_INLINE),
        )) {
            val buffer = WireBuffer()
            op.write(buffer)
            buffer.byteIndex = 0
            assertEquals(op.opcode, buffer.readByte())
            val decoded = mutableListOf<Operation>()
            BitmapData.read(buffer, decoded)
            assertEquals(op, decoded.single())
        }
    }

    // ---------------------------------------------------------------------------------------------
    // read() fail-closed guards (a .rc is untrusted input).
    // ---------------------------------------------------------------------------------------------

    @Test
    fun read_rejectsUrlEncoding() {
        // heightWord = (ENCODING_URL=1 << 16) | 1
        val ex = assertFailsWith<IllegalStateException> {
            readBitmapBody(imageId = 5, widthWord = 1, heightWord = (BitmapData.ENCODING_URL shl 16) or 1, declaredLen = 0)
        }
        assertTrue(ex.message!!.contains("URL"))
    }

    @Test
    fun read_rejectsFileEncoding() {
        val ex = assertFailsWith<IllegalStateException> {
            readBitmapBody(imageId = 5, widthWord = 1, heightWord = (BitmapData.ENCODING_FILE shl 16) or 1, declaredLen = 0)
        }
        assertTrue(ex.message!!.contains("file"))
    }

    @Test
    fun read_rejectsOutOfRangeDimensions() {
        // width beyond MAX_IMAGE_DIMENSION (8000).
        assertFailsWith<IllegalStateException> {
            readBitmapBody(imageId = 5, widthWord = 9000, heightWord = 10, declaredLen = 0)
        }
        // zero dimension.
        assertFailsWith<IllegalStateException> {
            readBitmapBody(imageId = 5, widthWord = 0, heightWord = 10, declaredLen = 0)
        }
    }

    @Test
    fun read_boundsThePixelBufferLength() {
        // 1x1 → maxSize = 1*1*4 + 10000 = 10004; a declared buffer length above that must be rejected
        // BEFORE allocating (the guarded readBuffer trips on the length prefix).
        val ex = assertFailsWith<IllegalStateException> {
            readBitmapBody(imageId = 5, widthWord = 1, heightWord = 1, declaredLen = 20_000)
        }
        assertTrue(ex.message!!.contains("invalid size") || ex.message!!.contains("buffer"))
    }
}
