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

import com.tneff.kmpremotecompose.remote.core.operations.draw.PathCreate
import com.tneff.kmpremotecompose.remote.core.operations.draw.PathExpression
import com.tneff.kmpremotecompose.remote.core.operations.draw.PathTween
import com.tneff.kmpremotecompose.remote.core.operations.layout.FlowLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.LayoutOps
import com.tneff.kmpremotecompose.remote.core.operations.layout.TouchExpression
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawOps
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Per-op byte tests for the REM-24 P2 group-B round-3 ops (FLOW-7, path + touch families).
 *
 * PATH_EXPRESSION / TOUCH_EXPRESSION are anchored to the **real corpus** bytes (extracted via
 * inflateWithTrace from demo_path_expression_path_test1.rc / c_modifier_fill_parent_max_height.rc) —
 * the variable-length, NaN-id float-array shapes round-trip byte-exact. PATH_CREATE is spec-anchored;
 * LAYOUT_FLOW is spec-anchored to the **7-field** current ./androidx source (per human decision: source
 * wins, fixture c_flow.rc being adapted by test-1).
 */
class P2GroupBR3ByteTest {

    @AfterTest fun cleanup() = Operations.resetReaders()

    private fun bytes(vararg v: Int): ByteArray = ByteArray(v.size) { v[it].toByte() }
    private fun fb(bits: Int): Float = Float.fromBits(bits)
    private fun writeBytes(op: Operation): ByteArray = WireBuffer().also { op.write(it) }.toByteArray()

    @Test fun pathCreate_writesExactBytes() {
        // 159 (0x9F) + id 7 + startX 1f + startY 2f (spec-anchored).
        assertContentEquals(
            bytes(0x9F, 0x00, 0x00, 0x00, 0x07, 0x3F, 0x80, 0x00, 0x00, 0x40, 0x00, 0x00, 0x00),
            writeBytes(PathCreate(7, 1f, 2f)),
        )
    }

    @Test fun pathTween_writesExactBytes() {
        // 158 (0x9E) + outId 5 + pathId1 6 + pathId2 7 + tween 0.5f (spec-anchored).
        assertContentEquals(
            bytes(0x9E, 0x00, 0x00, 0x00, 0x05, 0x00, 0x00, 0x00, 0x06, 0x00, 0x00, 0x00, 0x07, 0x3F, 0x00, 0x00, 0x00),
            writeBytes(PathTween(5, 6, 7, 0.5f)),
        )
    }

    @Test fun flowLayout7_writesExactBytes() {
        // 240 (0xF0) + (-3,-1,1,4, 2.5f, maxItems 3, maxLines 5) — 7 fields, current source.
        assertContentEquals(
            bytes(
                0xF0, 0xFF, 0xFF, 0xFF, 0xFD, 0xFF, 0xFF, 0xFF, 0xFF,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x04, 0x40, 0x20, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x03, 0x00, 0x00, 0x00, 0x05,
            ),
            writeBytes(FlowLayout(-3, -1, 1, 4, 2.5f, 3, 5)),
        )
    }

    @Test fun pathExpression_writesExactBytes() {
        // Real golden: demo_path_expression_path_test1.rc @0x189 (69 bytes).
        val op = PathExpression(
            id = 0x34, flags = 8, min = fb(0x00000000), max = fb(0x40C90FDB), count = fb(0x42700000),
            expressionX = floatArrayOf(
                fb(0xFF800030.toInt()), fb(0x3F800000), fb(0xFFB10046.toInt()), fb(0x40490FDB),
                fb(0xFFB10001.toInt()), fb(0xFFB10012.toInt()), fb(0xFFB10002.toInt()), fb(0xFFB10003.toInt()),
            ),
            expressionY = floatArrayOf(fb(0xFF80002C.toInt()), fb(0xFF800033.toInt())),
        )
        assertContentEquals(
            bytes(
                0xC1, 0x00, 0x00, 0x00, 0x34, 0x00, 0x00, 0x00, 0x08, 0x00, 0x00, 0x00, 0x00,
                0x40, 0xC9, 0x0F, 0xDB, 0x42, 0x70, 0x00, 0x00, 0x00, 0x00, 0x00, 0x08,
                0xFF, 0x80, 0x00, 0x30, 0x3F, 0x80, 0x00, 0x00, 0xFF, 0xB1, 0x00, 0x46, 0x40, 0x49, 0x0F, 0xDB,
                0xFF, 0xB1, 0x00, 0x01, 0xFF, 0xB1, 0x00, 0x12, 0xFF, 0xB1, 0x00, 0x02, 0xFF, 0xB1, 0x00, 0x03,
                0x00, 0x00, 0x00, 0x02, 0xFF, 0x80, 0x00, 0x2C, 0xFF, 0x80, 0x00, 0x33,
            ),
            writeBytes(op),
        )
    }

    @Test fun touchExpression_writesExactBytes() {
        // Real golden: c_modifier_fill_parent_max_height.rc @0x78 (49 bytes).
        val op = TouchExpression(
            id = 0x2A, value = fb(0), min = fb(0), max = fb(0xFF80002B.toInt()), velocityId = fb(0),
            touchEffects = 3,
            exp = floatArrayOf(fb(0xFF80000E.toInt()), fb(0xBF800000.toInt()), fb(0xFFB10003.toInt())),
            stopLogic = 0, stops = floatArrayOf(), easing = floatArrayOf(),
        )
        assertContentEquals(
            bytes(
                0x9D, 0x00, 0x00, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0xFF, 0x80, 0x00, 0x2B, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03, 0x00, 0x00, 0x00, 0x03,
                0xFF, 0x80, 0x00, 0x0E, 0xBF, 0x80, 0x00, 0x00, 0xFF, 0xB1, 0x00, 0x03,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            ),
            writeBytes(op),
        )
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

    @Test fun allR3Ops_roundTrip() {
        assertRoundTrips(PathCreate(7, 1f, 2f), PathCreate)
        assertRoundTrips(PathTween(5, 6, 7, 0.5f), PathTween)
        assertRoundTrips(FlowLayout(-3, -1, 1, 4, 2.5f, 3, 5), FlowLayout)
        assertRoundTrips(
            PathExpression(52, 8, fb(0), fb(0x40C90FDB), fb(0x42700000), floatArrayOf(1f, 2f, 3f), floatArrayOf(4f)),
            PathExpression,
        )
        // TouchExpression with non-empty stops (stopLogic packs touchMode<<16) + easing — exercises all 3 arrays.
        assertRoundTrips(
            TouchExpression(
                42, 0f, 0f, fb(0xFF80002B.toInt()), 0f, 3,
                floatArrayOf(1f, 2f), stopLogic = (2 shl 16) or 2, stops = floatArrayOf(0.5f, 1f), easing = floatArrayOf(3f),
            ),
            TouchExpression,
        )
    }

    @Test fun register_r3() {
        Operations.resetReaders()
        DrawOps.register()
        LayoutOps.register()
        // Base.
        for (op in listOf(Operations.PATH_CREATE, Operations.PATH_TWEEN, Operations.TOUCH_EXPRESSION)) {
            assertTrue(Operations.isValid(op, 6, 0), "v6 ${Operations.name(op)}")
            assertTrue(Operations.isValid(op, 7, 0), "v7 ${Operations.name(op)}")
        }
        // PATH_EXPRESSION: androidx + widgets overlay.
        assertFalse(Operations.isValid(Operations.PATH_EXPRESSION, 7, 0), "PATH_EXPRESSION not baseline")
        assertTrue(Operations.isValid(Operations.PATH_EXPRESSION, 7, Operations.PROFILE_ANDROIDX), "PATH_EXPRESSION androidx")
        assertTrue(Operations.isValid(Operations.PATH_EXPRESSION, 7, Operations.PROFILE_WIDGETS), "PATH_EXPRESSION widgets")
        // LAYOUT_FLOW: experimental overlay.
        assertFalse(Operations.isValid(Operations.LAYOUT_FLOW, 7, 0), "FLOW not baseline")
        assertTrue(
            Operations.isValid(Operations.LAYOUT_FLOW, 7, Operations.PROFILE_ANDROIDX or Operations.PROFILE_EXPERIMENTAL),
            "FLOW androidx+exp",
        )
    }
}
