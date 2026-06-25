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

import com.tneff.kmpremotecompose.remote.core.operations.draw.ClipRect
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawBitmapScaled
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawOps
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawToBitmap
import com.tneff.kmpremotecompose.remote.core.operations.layout.LayoutOps
import com.tneff.kmpremotecompose.remote.core.operations.layout.VisibilityModifier
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Per-op byte tests for the REM-25 P2 group-B round-4 ops. All four are anchored to **real corpus**
 * bytes extracted via inflateWithTrace (demo_bitmap_drawing_bit_draw1 / demo_winding_rule_path_winding /
 * c_modifier_visibility).
 */
class P2GroupBR4ByteTest {

    @AfterTest fun cleanup() = Operations.resetReaders()

    private fun bytes(vararg v: Int): ByteArray = ByteArray(v.size) { v[it].toByte() }
    private fun writeBytes(op: Operation): ByteArray = WireBuffer().also { op.write(it) }.toByteArray()

    @Test fun drawToBitmap_writesExactBytes() {
        // 190 (0xBE) + bitmapId 47 + mode 0 + color 0. Real golden: demo_bitmap_drawing_bit_draw1.rc @0x111.
        assertContentEquals(
            bytes(0xBE, 0x00, 0x00, 0x00, 0x2F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            writeBytes(DrawToBitmap(47, 0, 0)),
        )
    }

    @Test fun drawBitmapScaled_writesExactBytes() {
        // 149 (0x95) + imageId 47 + src(0,0,256,256) + dst(0,0,128,128) + scaleType 4 + scaleFactor 0f + cdId 49.
        // Real golden: demo_bitmap_drawing_bit_draw1.rc @0x1cc.
        assertContentEquals(
            bytes(
                0x95, 0x00, 0x00, 0x00, 0x2F,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x43, 0x80, 0x00, 0x00, 0x43, 0x80, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x43, 0x00, 0x00, 0x00, 0x43, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x31,
            ),
            writeBytes(DrawBitmapScaled(47, 0f, 0f, 256f, 256f, 0f, 0f, 128f, 128f, 4, 0f, 49)),
        )
    }

    @Test fun clipRect_writesExactBytes() {
        // 39 (0x27) + (0f, 0f, 200f, 200f). Real golden: demo_winding_rule_path_winding.rc @0x918.
        assertContentEquals(
            bytes(0x27, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x43, 0x48, 0x00, 0x00, 0x43, 0x48, 0x00, 0x00),
            writeBytes(ClipRect(0f, 0f, 200f, 200f)),
        )
    }

    @Test fun visibilityModifier_writesExactBytes() {
        // 211 (0xD3) + valueId 42. Real golden: c_modifier_visibility.rc @0x93.
        assertContentEquals(bytes(0xD3, 0x00, 0x00, 0x00, 0x2A), writeBytes(VisibilityModifier(42)))
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

    @Test fun allR4Ops_roundTrip() {
        assertRoundTrips(DrawToBitmap(47, 1, -0x1000000), DrawToBitmap)
        assertRoundTrips(DrawBitmapScaled(47, 0f, 0f, 256f, 256f, 0f, 0f, 128f, 128f, 4, 1.5f, 49), DrawBitmapScaled)
        assertRoundTrips(ClipRect(0f, 0f, 200f, 200f), ClipRect)
        assertRoundTrips(VisibilityModifier(42), VisibilityModifier)
    }

    @Test fun register_r4() {
        Operations.resetReaders()
        DrawOps.register()
        LayoutOps.register()
        for (op in listOf(Operations.CLIP_RECT, Operations.DRAW_BITMAP_SCALED, Operations.MODIFIER_VISIBILITY)) {
            assertTrue(Operations.isValid(op, 6, 0), "v6 ${Operations.name(op)}")
            assertTrue(Operations.isValid(op, 7, 0), "v7 ${Operations.name(op)}")
        }
        // DRAW_TO_BITMAP: androidx + widgets overlay.
        assertFalse(Operations.isValid(Operations.DRAW_TO_BITMAP, 7, 0), "not baseline")
        assertTrue(Operations.isValid(Operations.DRAW_TO_BITMAP, 7, Operations.PROFILE_ANDROIDX), "androidx")
        assertTrue(Operations.isValid(Operations.DRAW_TO_BITMAP, 7, Operations.PROFILE_WIDGETS), "widgets")
    }
}
