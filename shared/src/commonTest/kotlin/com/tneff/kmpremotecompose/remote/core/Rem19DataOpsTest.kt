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

import com.tneff.kmpremotecompose.remote.core.operations.Builtins
import com.tneff.kmpremotecompose.remote.core.operations.ColorExpression
import com.tneff.kmpremotecompose.remote.core.operations.ColorTheme
import com.tneff.kmpremotecompose.remote.core.operations.DataListFloat
import com.tneff.kmpremotecompose.remote.core.operations.DataMapIds
import com.tneff.kmpremotecompose.remote.core.operations.FloatExpression
import com.tneff.kmpremotecompose.remote.core.operations.NamedVariable
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.OperationReader
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.ShaderData
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

/**
 * REM-19 P2 group-A op batch. Byte goldens are format-verified hand goldens (write → exact bytes,
 * not self-referential), derived from the upstream `./androidx` write methods; read round-trips and
 * the per-op layer placement are pinned alongside.
 */
class Rem19DataOpsTest {

    @AfterTest
    fun cleanup() {
        Operations.resetReaders()
    }

    @Test
    fun animatedFloat_bytesAndRoundTrip() {
        // opcode 81, id 10, len = 2 (no animation), floats 1.0, 2.0.
        assertContentEquals(
            bytes(0x51, 0, 0, 0, 0x0A, 0, 0, 0, 0x02, 0x3F, 0x80, 0, 0, 0x40, 0, 0, 0),
            encoded(FloatExpression(10, floatArrayOf(1.0f, 2.0f))),
        )
        // With animation: len packs animation size in the high 16 bits (1 | 1<<16).
        assertContentEquals(
            bytes(0x51, 0, 0, 0, 0x0A, 0, 0x01, 0, 0x01, 0x3F, 0x80, 0, 0, 0x40, 0, 0, 0),
            encoded(FloatExpression(10, floatArrayOf(1.0f), floatArrayOf(2.0f))),
        )
        assertEquals(
            FloatExpression(10, floatArrayOf(1.0f), floatArrayOf(2.0f)),
            reread(FloatExpression(10, floatArrayOf(1.0f), floatArrayOf(2.0f)), FloatExpression),
        )
    }

    @Test
    fun namedVariable_bytesAndRoundTrip() {
        // opcode 137, varId 5, varType 1, name "x".
        assertContentEquals(
            bytes(0x89, 0, 0, 0, 0x05, 0, 0, 0, 0x01, 0, 0, 0, 0x01, 0x78),
            encoded(NamedVariable(5, 1, "x")),
        )
        assertEquals(NamedVariable(5, 1, "x"), reread(NamedVariable(5, 1, "x"), NamedVariable))
    }

    @Test
    fun colorExpression_bytesAndRoundTrip() {
        // opcode 134, id 7, four raw params.
        assertContentEquals(
            bytes(0x86, 0, 0, 0, 0x07, 0, 0, 0, 0x11, 0, 0, 0, 0x22, 0, 0, 0, 0x33, 0, 0, 0, 0x44),
            encoded(ColorExpression(7, 0x11, 0x22, 0x33, 0x44)),
        )
        assertEquals(
            ColorExpression(7, 0x11, 0x22, 0x33, 0x44),
            reread(ColorExpression(7, 0x11, 0x22, 0x33, 0x44), ColorExpression),
        )
    }

    @Test
    fun floatList_bytesAndRoundTrip() {
        // opcode 147, id 9, count 2, floats 1.0, 0.5.
        assertContentEquals(
            bytes(0x93, 0, 0, 0, 0x09, 0, 0, 0, 0x02, 0x3F, 0x80, 0, 0, 0x3F, 0, 0, 0),
            encoded(DataListFloat(9, floatArrayOf(1.0f, 0.5f))),
        )
        assertEquals(
            DataListFloat(9, floatArrayOf(1.0f, 0.5f)),
            reread(DataListFloat(9, floatArrayOf(1.0f, 0.5f)), DataListFloat),
        )
    }

    @Test
    fun idMap_bytesAndRoundTrip() {
        // opcode 145, id 11, count 1, entry name "a", type 2, valueId 100.
        val op = DataMapIds(11, listOf(DataMapIds.Entry("a", 2, 100)))
        assertContentEquals(
            bytes(0x91, 0, 0, 0, 0x0B, 0, 0, 0, 0x01, 0, 0, 0, 0x01, 0x61, 0x02, 0, 0, 0, 0x64),
            encoded(op),
        )
        assertEquals(op, reread(op, DataMapIds))
    }

    @Test
    fun colorTheme_bytesAndRoundTrip_isOverlayOp() {
        // opcode 196, id 3, group 4, lightMode 1, darkMode 2, fallbacks.
        val op = ColorTheme(3, 4, 1, 2, 0x0A0B0C0D, 0x01020304)
        assertContentEquals(
            bytes(0xC4, 0, 0, 0, 0x03, 0, 0, 0, 0x04, 0, 0x01, 0, 0x02, 0x0A, 0x0B, 0x0C, 0x0D, 0x01, 0x02, 0x03, 0x04),
            encoded(op),
        )
        assertEquals(op, reread(op, ColorTheme))

        Operations.resetReaders()
        Builtins.register()
        // COLOR_THEME resolves only under the androidx/widgets overlays, never base.
        assertFalse(Operations.isValid(Operations.COLOR_THEME, 7, 0))
        assertFalse(Operations.isValid(Operations.COLOR_THEME, 6, 0))
        assertTrue(Operations.isValid(Operations.COLOR_THEME, 7, Operations.PROFILE_ANDROIDX))
        assertTrue(Operations.isValid(Operations.COLOR_THEME, 7, Operations.PROFILE_WIDGETS))
    }

    @Test
    fun dataShader_bytesAndRoundTrip_isV6AndAndroidxOverlay() {
        // opcode 45, shaderId 12, shaderTextId 13, sizes=1 (one float uniform), uniform "u"=[1.0].
        val op = ShaderData(12, 13, floatUniforms = listOf(ShaderData.FloatUniform("u", floatArrayOf(1.0f))))
        assertContentEquals(
            bytes(0x2D, 0, 0, 0, 0x0C, 0, 0, 0, 0x0D, 0, 0, 0, 0x01, 0, 0, 0, 0x01, 0x75, 0, 0, 0, 0x01, 0x3F, 0x80, 0, 0),
            encoded(op),
        )
        assertEquals(op, reread(op, ShaderData))

        Operations.resetReaders()
        Builtins.register()
        // DATA_SHADER resolves at api 6 (V6 base) and under the androidx overlay — never V7 baseline.
        assertTrue(Operations.isValid(Operations.DATA_SHADER, 6, 0))
        assertFalse(Operations.isValid(Operations.DATA_SHADER, 7, 0))
        assertTrue(Operations.isValid(Operations.DATA_SHADER, 7, Operations.PROFILE_ANDROIDX))
    }
}
