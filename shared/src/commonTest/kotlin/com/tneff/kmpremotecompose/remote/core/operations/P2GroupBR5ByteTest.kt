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

import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawContent
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawOps
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawTextOnPath
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawTweenPath
import com.tneff.kmpremotecompose.remote.core.operations.draw.PathAppend
import com.tneff.kmpremotecompose.remote.core.operations.layout.HostAction
import com.tneff.kmpremotecompose.remote.core.operations.layout.HostMetadataAction
import com.tneff.kmpremotecompose.remote.core.operations.layout.ImpulseStart
import com.tneff.kmpremotecompose.remote.core.operations.layout.LayoutOps
import com.tneff.kmpremotecompose.remote.core.operations.layout.RunAction
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Per-op byte tests for the REM-26 P2 group-B round-5 ops. PATH_ADD / DRAW_TWEEN_PATH / DRAW_CONTENT /
 * DRAW_TEXT_ON_PATH / HOST_METADATA_ACTION / HOST_ACTION are anchored to **real corpus** bytes
 * (demo_graphs1 / demo_path_expression_path_test2 / attribute_string / clock / stock / text_refresh_bug,
 * via inflateWithTrace). IMPULSE_START / RUN_ACTION are spec-anchored (their docs abort tracing earlier).
 */
class P2GroupBR5ByteTest {

    @AfterTest fun cleanup() = Operations.resetReaders()

    private fun bytes(vararg v: Int): ByteArray = ByteArray(v.size) { v[it].toByte() }
    private fun fb(bits: Int): Float = Float.fromBits(bits)
    private fun writeBytes(op: Operation): ByteArray = WireBuffer().also { op.write(it) }.toByteArray()

    @Test fun pathAdd_writesExactBytes() {
        // 160 (0xA0) + id 94 + len 5 + data. Real golden: demo_graphs1.rc @0xb6d (29 bytes).
        val op = PathAppend(
            0x5E,
            floatArrayOf(fb(0xFF80000B.toInt()), fb(0), fb(0), fb(0xFF800061.toInt()), fb(0xFF800062.toInt())),
        )
        assertContentEquals(
            bytes(
                0xA0, 0x00, 0x00, 0x00, 0x5E, 0x00, 0x00, 0x00, 0x05,
                0xFF, 0x80, 0x00, 0x0B, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0xFF, 0x80, 0x00, 0x61, 0xFF, 0x80, 0x00, 0x62,
            ),
            writeBytes(op),
        )
    }

    @Test fun drawTweenPath_writesExactBytes() {
        // 125 (0x7D) + path1 50 + path2 51 + tween(NaN-id) + start 0f + stop 1f. Real: demo_path_expression_path_test2.rc @0x2af.
        assertContentEquals(
            bytes(
                0x7D, 0x00, 0x00, 0x00, 0x32, 0x00, 0x00, 0x00, 0x33,
                0xFF, 0x80, 0x00, 0x34, 0x00, 0x00, 0x00, 0x00, 0x3F, 0x80, 0x00, 0x00,
            ),
            writeBytes(DrawTweenPath(50, 51, fb(0xFF800034.toInt()), 0f, 1f)),
        )
    }

    @Test fun drawContent_writesExactBytes() {
        assertContentEquals(bytes(0x8B), writeBytes(DrawContent())) // 139, real: attribute_string.rc @0x140
    }

    @Test fun drawTextOnPath_writesExactBytes() {
        // 53 (0x35) + textId 52 + pathId 74 + vOffset 0f + hOffset 0f. Real golden: clock.rc @0x7a3.
        assertContentEquals(
            bytes(0x35, 0x00, 0x00, 0x00, 0x34, 0x00, 0x00, 0x00, 0x4A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            writeBytes(DrawTextOnPath(52, 74, 0f, 0f)),
        )
    }

    @Test fun hostMetadataAction_writesExactBytes() {
        // 216 (0xD8) + actionId 567 + metadataId 80. Real golden: stock.rc @0x3de3.
        assertContentEquals(
            bytes(0xD8, 0x00, 0x00, 0x02, 0x37, 0x00, 0x00, 0x00, 0x50),
            writeBytes(HostMetadataAction(0x237, 0x50)),
        )
    }

    @Test fun hostAction_writesExactBytes() {
        // 209 (0xD1) + actionId 0. Real golden: text_refresh_bug.rc @0x57e.
        assertContentEquals(bytes(0xD1, 0x00, 0x00, 0x00, 0x00), writeBytes(HostAction(0)))
    }

    @Test fun impulseStart_writesExactBytes() {
        // 164 (0xA4) + duration 1.5f + startAt 0f (spec-anchored).
        assertContentEquals(
            bytes(0xA4, 0x3F, 0xC0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            writeBytes(ImpulseStart(1.5f, 0f)),
        )
    }

    @Test fun runAction_writesExactBytes() {
        assertContentEquals(bytes(0xEC), writeBytes(RunAction())) // 236 (spec-anchored, fieldless)
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

    @Test fun allR5Ops_roundTrip() {
        assertRoundTrips(PathAppend(94, floatArrayOf(1f, 2f, fb(0xFF800061.toInt()))), PathAppend)
        assertRoundTrips(DrawTweenPath(50, 51, fb(0xFF800034.toInt()), 0f, 1f), DrawTweenPath)
        assertRoundTrips(DrawContent(), DrawContent)
        assertRoundTrips(DrawTextOnPath(52, 74, 3.5f, 7.5f), DrawTextOnPath)
        assertRoundTrips(ImpulseStart(1.5f, 0.25f), ImpulseStart)
        assertRoundTrips(RunAction(), RunAction)
        assertRoundTrips(HostMetadataAction(0x237, 0x50), HostMetadataAction)
        assertRoundTrips(HostAction(7), HostAction)
    }

    @Test fun register_r5BaseOps() {
        Operations.resetReaders()
        DrawOps.register()
        LayoutOps.register()
        for (op in listOf(
            Operations.PATH_ADD, Operations.DRAW_TWEEN_PATH, Operations.DRAW_TEXT_ON_PATH, Operations.DRAW_CONTENT,
            Operations.IMPULSE_START, Operations.RUN_ACTION, Operations.HOST_ACTION, Operations.HOST_METADATA_ACTION,
        )) {
            assertTrue(Operations.isValid(op, 6, 0), "v6 ${Operations.name(op)}")
            assertTrue(Operations.isValid(op, 7, 0), "v7 ${Operations.name(op)}")
        }
    }
}
