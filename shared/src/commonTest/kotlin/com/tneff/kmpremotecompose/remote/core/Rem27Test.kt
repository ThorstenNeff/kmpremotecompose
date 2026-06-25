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

import com.tneff.kmpremotecompose.remote.core.operations.BitmapFontData
import com.tneff.kmpremotecompose.remote.core.operations.Builtins
import com.tneff.kmpremotecompose.remote.core.operations.DataMapLookup
import com.tneff.kmpremotecompose.remote.core.operations.IntegerExpression
import com.tneff.kmpremotecompose.remote.core.operations.MatrixVectorMath
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.OperationReader
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.TextAttribute
import com.tneff.kmpremotecompose.remote.core.operations.TextMeasure
import com.tneff.kmpremotecompose.remote.core.operations.TimeAttribute
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

private fun encoded(op: Operation): ByteArray {
    val buffer = WireBuffer()
    op.write(buffer)
    return buffer.toByteArray()
}

private fun reread(op: Operation, reader: OperationReader): Operation {
    val buffer = WireBuffer()
    op.write(buffer)
    buffer.byteIndex = 0
    assertEquals(op.opcode, buffer.readByte())
    val decoded = mutableListOf<Operation>()
    reader.read(buffer, decoded)
    return decoded.single()
}

/** REM-27 P2 group-A tail: format-verified byte goldens + read round-trips + layer resolvability. */
class Rem27Test {

    @AfterTest
    fun cleanup() {
        Operations.resetReaders()
    }

    @Test
    fun simpleOps_bytesAndRoundTrip() {
        // TEXT_MEASURE (155): id 1, textId 2, type 3.
        assertContentEquals(bytes(0x9B, 0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 3), encoded(TextMeasure(1, 2, 3)))
        assertEquals(TextMeasure(1, 2, 3), reread(TextMeasure(1, 2, 3), TextMeasure))

        // DATA_MAP_LOOKUP (154): id 1, dataMapId 2, keyStringId 3.
        assertContentEquals(bytes(0x9A, 0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 3), encoded(DataMapLookup(1, 2, 3)))
        assertEquals(DataMapLookup(1, 2, 3), reread(DataMapLookup(1, 2, 3), DataMapLookup))

        // ATTRIBUTE_TEXT (170): id 5, textId 6, short type 7, short 0 (reserved).
        assertContentEquals(bytes(0xAA, 0, 0, 0, 5, 0, 0, 0, 6, 0, 7, 0, 0), encoded(TextAttribute(5, 6, 7)))
        assertEquals(TextAttribute(5, 6, 7), reread(TextAttribute(5, 6, 7), TextAttribute))

        // INTEGER_EXPRESSION (144): id 1, mask 0, count 1, value 0x0A.
        val ie = IntegerExpression(1, 0, intArrayOf(0x0A))
        assertContentEquals(bytes(0x90, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0x0A), encoded(ie))
        assertEquals(ie, reread(ie, IntegerExpression))
    }

    @Test
    fun timeAttribute_withAndWithoutArgs() {
        // No args: short argCount 0.
        assertContentEquals(bytes(0xAC, 0, 0, 0, 5, 0, 0, 0, 6, 0, 7, 0, 0), encoded(TimeAttribute(5, 6, 7)))
        assertEquals(TimeAttribute(5, 6, 7), reread(TimeAttribute(5, 6, 7), TimeAttribute))
        // With one arg: short argCount 1, then the int arg.
        val withArgs = TimeAttribute(5, 6, 7, intArrayOf(9))
        assertContentEquals(bytes(0xAC, 0, 0, 0, 5, 0, 0, 0, 6, 0, 7, 0, 1, 0, 0, 0, 9), encoded(withArgs))
        assertEquals(withArgs, reread(withArgs, TimeAttribute))
    }

    @Test
    fun matrixVectorMath_bytesRoundTrip_isV7BaseAlwaysOn() {
        // opcode 188, short type 1, matrixId 2, outCount 1, outputs[3], inCount 1, inputs[1.0].
        val op = MatrixVectorMath(1, 2, intArrayOf(3), floatArrayOf(1.0f))
        assertContentEquals(
            bytes(0xBC, 0, 1, 0, 0, 0, 2, 0, 0, 0, 1, 0, 0, 0, 3, 0, 0, 0, 1, 0x3F, 0x80, 0, 0),
            encoded(op),
        )
        assertEquals(op, reread(op, MatrixVectorMath))

        Operations.resetReaders()
        Builtins.register()
        // V7_BASE always-on: resolves at api 7 baseline, NOT api 6.
        assertTrue(Operations.isValid(Operations.MATRIX_VECTOR_MATH, 7, 0))
        assertFalse(Operations.isValid(Operations.MATRIX_VECTOR_MATH, 6, 0))
    }

    @Test
    fun bitmapFont_bytesRoundTrip_withAndWithoutKerning() {
        // No kerning → version 0; glyph count 1; glyph "A", bitmapId 2, margins 0, size 8x10.
        val noKern = BitmapFontData(
            id = 1,
            glyphs = listOf(BitmapFontData.Glyph("A", 2, 0, 0, 0, 0, 8, 10)),
        )
        assertContentEquals(
            bytes(
                0xA7, 0, 0, 0, 1, // opcode + id
                0, 0, 0, 1, // version 0 | glyph count 1
                0, 0, 0, 1, 0x41, // "A"
                0, 0, 0, 2, // bitmapId
                0, 0, 0, 0, 0, 0, 0, 0, // margins L/T/R/B
                0, 8, 0, 0x0A, // bitmap 8x10
            ),
            encoded(noKern),
        )
        assertEquals(noKern, reread(noKern, BitmapFontData))

        // With kerning → version V2 in the high 16 bits + a kerning table.
        val withKern = BitmapFontData(
            id = 1,
            glyphs = listOf(BitmapFontData.Glyph("A", 2, 0, 0, 0, 0, 8, 10)),
            kerning = listOf(BitmapFontData.KerningEntry("AB", 5)),
        )
        assertEquals(withKern, reread(withKern, BitmapFontData))
        // Version high-bit is set when kerning is present.
        assertEquals(0x00010001, encoded(withKern).let {
            ((it[5].toInt() and 0xFF) shl 24) or ((it[6].toInt() and 0xFF) shl 16) or
                ((it[7].toInt() and 0xFF) shl 8) or (it[8].toInt() and 0xFF)
        })
    }

    @Test
    fun baseOps_resolveInBothBaseLayers() {
        Operations.resetReaders()
        Builtins.register()
        for (op in listOf(
            Operations.DATA_BITMAP_FONT, Operations.INTEGER_EXPRESSION, Operations.TEXT_MEASURE,
            Operations.DATA_MAP_LOOKUP, Operations.ATTRIBUTE_TEXT, Operations.ATTRIBUTE_TIME,
        )) {
            assertTrue(Operations.isValid(op, 6, 0), "${Operations.name(op)} not resolvable at api 6")
            assertTrue(Operations.isValid(op, 7, 0), "${Operations.name(op)} not resolvable at api 7")
        }
    }
}
