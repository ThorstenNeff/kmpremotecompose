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
package com.tneff.kmpremotecompose.remote.core.operations

import com.tneff.kmpremotecompose.remote.core.operations.draw.ClipPath
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawBitmapFontText
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawOps
import com.tneff.kmpremotecompose.remote.core.operations.draw.ParticlesCreate
import com.tneff.kmpremotecompose.remote.core.operations.layout.HapticFeedback
import com.tneff.kmpremotecompose.remote.core.operations.layout.LayoutOps
import com.tneff.kmpremotecompose.remote.core.operations.layout.ValueFloatExpressionChangeAction
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Per-op byte tests for the REM-28 P2 group-B round-6 tail. CLIP_PATH / DRAW_BITMAP_FONT_TEXT_RUN /
 * VALUE_FLOAT_EXPRESSION_CHANGE_ACTION are anchored to **real corpus** bytes (clock_demo1_clock1 /
 * digital_clock1 / paths_demos, via inflateWithTrace). PARTICLE_DEFINE / HAPTIC_FEEDBACK are
 * spec-anchored (their docs abort tracing earlier). PARTICLE_DEFINE is a `PaintOperation` (draw side) →
 * group B.
 */
class P2GroupBR6ByteTest {

    @AfterTest fun cleanup() = Operations.resetReaders()

    private fun bytes(vararg v: Int): ByteArray = ByteArray(v.size) { v[it].toByte() }
    private fun writeBytes(op: Operation): ByteArray = WireBuffer().also { op.write(it) }.toByteArray()

    @Test fun clipPath_writesExactBytes() {
        // 38 (0x26) + packed 0x37. Real golden: clock_demo1_clock1.rc @0x2ab.
        assertContentEquals(bytes(0x26, 0x00, 0x00, 0x00, 0x37), writeBytes(ClipPath(0x37)))
    }

    @Test fun drawBitmapFontText_writesExactBytes_noGlyphSpacing() {
        // 48 (0x30) + textId 62 (bit31 clear → no glyphSpacing) + font 149 + start 0 + end -1 + x 8f + y 24f.
        // Real golden: digital_clock1.rc @0x8ef1 (25 bytes).
        assertContentEquals(
            bytes(
                0x30, 0x00, 0x00, 0x00, 0x3E, 0x00, 0x00, 0x00, 0x95,
                0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFF, 0x41, 0x00, 0x00, 0x00, 0x41, 0xC0, 0x00, 0x00,
            ),
            writeBytes(DrawBitmapFontText(62, 149, 0, -1, 8f, 24f, 0f)),
        )
    }

    @Test fun drawBitmapFontText_writesExactBytes_withGlyphSpacing() {
        // glyphSpacing != 0 → textId marker bit set + float written: 0x30 (62|0x80000000) 2f ...
        assertContentEquals(
            bytes(
                0x30, 0x80, 0x00, 0x00, 0x3E, 0x40, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x95,
                0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFF, 0x41, 0x00, 0x00, 0x00, 0x41, 0xC0, 0x00, 0x00,
            ),
            writeBytes(DrawBitmapFontText(62, 149, 0, -1, 8f, 24f, 2f)),
        )
    }

    @Test fun valueFloatExpressionChangeAction_writesExactBytes() {
        // 227 (0xE3) + valueId 46 + value 53. Real golden: paths_demos.rc @0x1fb.
        assertContentEquals(
            bytes(0xE3, 0x00, 0x00, 0x00, 0x2E, 0x00, 0x00, 0x00, 0x35),
            writeBytes(ValueFloatExpressionChangeAction(46, 53)),
        )
    }

    @Test fun particleDefine_writesExactBytes() {
        // 161 (0xA1) + id 5 + count 10 + varCount 2 + {7,[1f,2f]} {8,[3f]} (spec-anchored).
        assertContentEquals(
            bytes(
                0xA1, 0x00, 0x00, 0x00, 0x05, 0x00, 0x00, 0x00, 0x0A, 0x00, 0x00, 0x00, 0x02,
                0x00, 0x00, 0x00, 0x07, 0x00, 0x00, 0x00, 0x02, 0x3F, 0x80, 0x00, 0x00, 0x40, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x08, 0x00, 0x00, 0x00, 0x01, 0x40, 0x40, 0x00, 0x00,
            ),
            writeBytes(ParticlesCreate(5, 10, intArrayOf(7, 8), arrayOf(floatArrayOf(1f, 2f), floatArrayOf(3f)))),
        )
    }

    @Test fun hapticFeedback_writesExactBytes() {
        // 177 (0xB1) + type 3 (spec-anchored).
        assertContentEquals(bytes(0xB1, 0x00, 0x00, 0x00, 0x03), writeBytes(HapticFeedback(3)))
    }

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

    @Test fun allR6Ops_roundTrip() {
        assertRoundTrips(ClipPath(0x05300037), ClipPath) // exercises bits 20-23 + regionOp preservation
        assertRoundTrips(DrawBitmapFontText(62, 149, 0, -1, 8f, 24f, 0f), DrawBitmapFontText)
        assertRoundTrips(DrawBitmapFontText(62, 149, 0, -1, 8f, 24f, 2.5f), DrawBitmapFontText) // glyph path
        assertRoundTrips(ValueFloatExpressionChangeAction(46, 53), ValueFloatExpressionChangeAction)
        assertRoundTrips(
            ParticlesCreate(5, 10, intArrayOf(7, 8), arrayOf(floatArrayOf(1f, 2f), floatArrayOf(3f))),
            ParticlesCreate,
        )
        assertRoundTrips(HapticFeedback(3), HapticFeedback)
    }

    @Test fun register_r6BaseOps() {
        Operations.resetReaders()
        DrawOps.register()
        LayoutOps.register()
        for (op in listOf(
            Operations.CLIP_PATH, Operations.DRAW_BITMAP_FONT_TEXT_RUN, Operations.PARTICLE_DEFINE,
            Operations.HAPTIC_FEEDBACK, Operations.VALUE_FLOAT_EXPRESSION_CHANGE_ACTION,
        )) {
            assertTrue(Operations.isValid(op, 6, 0), "v6 ${Operations.name(op)}")
            assertTrue(Operations.isValid(op, 7, 0), "v7 ${Operations.name(op)}")
        }
    }
}
