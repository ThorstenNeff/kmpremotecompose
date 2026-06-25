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
 * Per-op byte tests for the REM-23 P2 group-B round-2 ops.
 *
 * LOOP_START / LAYOUT_STATE are anchored to the **real corpus** bytes (extracted via inflateWithTrace
 * from calendar_heatmap_grid.rc / c_state_layout.rc). CANVAS_OPERATIONS is fieldless (its corpus doc
 * aborts tracing earlier at a not-yet-ported op, so it is spec-anchored to the upstream apply()).
 */
class P2GroupBR2ByteTest {

    @AfterTest fun cleanup() = Operations.resetReaders()

    private fun bytes(vararg v: Int): ByteArray = ByteArray(v.size) { v[it].toByte() }
    private fun writeBytes(op: Operation): ByteArray = WireBuffer().also { op.write(it) }.toByteArray()

    @Test fun loopStart_writesExactBytes() {
        // 215 (0xD7) + indexId 68 + from 0f + step 1f + until 35f. Real golden: calendar_heatmap_grid.rc @0x63f.
        assertContentEquals(
            bytes(
                0xD7, 0x00, 0x00, 0x00, 0x44, 0x00, 0x00, 0x00, 0x00,
                0x3F, 0x80, 0x00, 0x00, 0x42, 0x0C, 0x00, 0x00,
            ),
            writeBytes(LoopStart(68, 0f, 1f, 35f)),
        )
    }

    @Test fun layoutState_writesExactBytes() {
        // 217 (0xD9) + (-3,-1,0,0,42). Real golden: c_state_layout.rc @0x4b.
        assertContentEquals(
            bytes(
                0xD9, 0xFF, 0xFF, 0xFF, 0xFD, 0xFF, 0xFF, 0xFF, 0xFF,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x2A,
            ),
            writeBytes(StateLayout(-3, -1, 0, 0, 42)),
        )
    }

    @Test fun canvasOperations_writesExactBytes() {
        // 173 (0xAD), fieldless.
        assertContentEquals(bytes(0xAD), writeBytes(CanvasOperations()))
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

    @Test fun allR2Ops_roundTrip() {
        assertRoundTrips(LoopStart(68, 0f, 1f, 35f), LoopStart)
        assertRoundTrips(LoopStart(-5, 2.5f, -1f, 10f), LoopStart)
        assertRoundTrips(StateLayout(-3, -1, 0, 0, 42), StateLayout)
        assertRoundTrips(CanvasOperations(), CanvasOperations)
    }

    @Test fun register_r2BaseOps() {
        Operations.resetReaders()
        LayoutOps.register()
        for (op in listOf(Operations.LOOP_START, Operations.LAYOUT_STATE, Operations.CANVAS_OPERATIONS)) {
            assertTrue(Operations.isValid(op, 6, 0), "v6 ${Operations.name(op)}")
            assertTrue(Operations.isValid(op, 7, 0), "v7 ${Operations.name(op)}")
        }
    }
}
