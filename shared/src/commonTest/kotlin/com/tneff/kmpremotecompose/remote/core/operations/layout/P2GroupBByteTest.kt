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
 * Per-op byte tests for the REM-20 P2 group-B batch (sub-batch 1: multi-doc + F7 ops).
 *
 * ROW / MODIFIER_CLIP_RECT / COLLAPSIBLE_ROW are anchored to the **real corpus** bytes (extracted via
 * inflateWithTrace from c_modifier_fill_max_height / c_modifier_clip_rect / c_collapsible_row);
 * VALUE_STRING_CHANGE_ACTION / ACCESSIBILITY_SEMANTICS to the real screenshottest bytes. CANVAS /
 * CANVAS_CONTENT / LAYOUT_COMPUTE are spec-anchored to the upstream `apply()` field layout (their
 * corpus docs abort tracing at an earlier group-A op, so no full-trace byte slice is available yet).
 */
class P2GroupBByteTest {

    @AfterTest
    fun cleanup() = Operations.resetReaders()

    private fun bytes(vararg v: Int): ByteArray = ByteArray(v.size) { v[it].toByte() }

    private fun writeBytes(op: Operation): ByteArray = WireBuffer().also { op.write(it) }.toByteArray()

    // ---- byte-exact write ----

    @Test fun canvas_writesExactBytes() {
        // 205 (0xCD) + componentId -3 + animationId -1 (spec-anchored, upstream CanvasLayout.apply).
        assertContentEquals(
            bytes(0xCD, 0xFF, 0xFF, 0xFF, 0xFD, 0xFF, 0xFF, 0xFF, 0xFF),
            writeBytes(CanvasLayout(-3, -1)),
        )
    }

    @Test fun canvasContent_writesExactBytes() {
        // 207 (0xCF) + componentId -4 (spec-anchored, upstream CanvasContent.apply).
        assertContentEquals(bytes(0xCF, 0xFF, 0xFF, 0xFF, 0xFC), writeBytes(CanvasContent(-4)))
    }

    @Test fun row_writesExactBytes() {
        // 203 (0xCB) + (-3,-1,1,4,0f). Real corpus golden: c_modifier_fill_max_height.rc @0x36.
        assertContentEquals(
            bytes(
                0xCB, 0xFF, 0xFF, 0xFF, 0xFD, 0xFF, 0xFF, 0xFF, 0xFF,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00,
            ),
            writeBytes(RowLayout(-3, -1, 1, 4, 0f)),
        )
    }

    @Test fun clipRect_writesExactBytes() {
        // 108 (0x6C), fieldless. Real corpus golden: c_modifier_clip_rect.rc @0x59.
        assertContentEquals(bytes(0x6C), writeBytes(ClipRectModifier()))
    }

    @Test fun collapsibleRow_writesExactBytes() {
        // 230 (0xE6) + (-3,-1,1,1,0f). Real corpus golden: c_collapsible_row.rc @0x36.
        assertContentEquals(
            bytes(
                0xE6, 0xFF, 0xFF, 0xFF, 0xFD, 0xFF, 0xFF, 0xFF, 0xFF,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,
            ),
            writeBytes(CollapsibleRowLayout(-3, -1, 1, 1, 0f)),
        )
    }

    @Test fun layoutCompute_writesExactBytes() {
        // 238 (0xEE) + type 1 + boundsId 42 + animateChanges true (spec-anchored, upstream apply).
        assertContentEquals(
            bytes(0xEE, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x2A, 0x01),
            writeBytes(LayoutCompute(1, 42, true)),
        )
    }

    @Test fun valueStringChangeAction_writesExactBytes() {
        // 213 (0xD5) + valueId 42 + value 43. Real screenshottest golden @0x7a.
        assertContentEquals(
            bytes(0xD5, 0x00, 0x00, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x2B),
            writeBytes(ValueStringChangeAction(42, 43)),
        )
    }

    @Test fun accessibilitySemantics_writesExactBytes() {
        // 250 (0xFA) + contentDescId 0 + role 0 + textId 0 + stateDescId 1 + mode 0 + enabled true + clickable false.
        // Real screenshottest golden @0x84.
        assertContentEquals(
            bytes(
                0xFA, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00,
            ),
            writeBytes(CoreSemantics(0, 0, 0, 1, 0, enabled = true, clickable = false)),
        )
    }

    // ---- round-trip: write → read (opcode consumed) → re-write byte-identical + fields equal ----

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

    @Test fun allP2GroupBOps_roundTrip() {
        assertRoundTrips(CanvasLayout(-3, -1), CanvasLayout)
        assertRoundTrips(CanvasContent(-4), CanvasContent)
        assertRoundTrips(RowLayout(-3, -1, 1, 4, 12.5f), RowLayout)
        assertRoundTrips(ClipRectModifier(), ClipRectModifier)
        assertRoundTrips(CollapsibleRowLayout(-3, -1, 1, 1, 7.5f), CollapsibleRowLayout)
        assertRoundTrips(LayoutCompute(2, -5, false), LayoutCompute)
        assertRoundTrips(ValueStringChangeAction(42, 43), ValueStringChangeAction)
        assertRoundTrips(CoreSemantics(-2, 3, -7, 1, 2, enabled = true, clickable = true), CoreSemantics)
    }

    // ---- registration: base ops in V6+V7 baseline; LAYOUT_COMPUTE in experimental overlays ----

    @Test fun register_baseOps() {
        Operations.resetReaders()
        LayoutOps.register()
        for (op in listOf(
            Operations.LAYOUT_CANVAS, Operations.LAYOUT_CANVAS_CONTENT, Operations.LAYOUT_ROW,
            Operations.MODIFIER_CLIP_RECT, Operations.LAYOUT_COLLAPSIBLE_ROW,
            Operations.VALUE_STRING_CHANGE_ACTION, Operations.ACCESSIBILITY_SEMANTICS,
        )) {
            assertTrue(Operations.isValid(op, 6, 0), "v6 ${Operations.name(op)}")
            assertTrue(Operations.isValid(op, 7, 0), "v7 ${Operations.name(op)}")
        }
    }

    @Test fun register_layoutComputeIsExperimentalOverlay() {
        Operations.resetReaders()
        LayoutOps.register()
        val op = Operations.LAYOUT_COMPUTE
        assertFalse(Operations.isValid(op, 7, 0), "not at v7 baseline")
        assertFalse(Operations.isValid(op, 7, Operations.PROFILE_ANDROIDX), "not at plain androidx")
        assertTrue(
            Operations.isValid(op, 7, Operations.PROFILE_ANDROIDX or Operations.PROFILE_EXPERIMENTAL),
            "androidx + experimental",
        )
        assertTrue(
            Operations.isValid(op, 7, Operations.PROFILE_WIDGETS or Operations.PROFILE_EXPERIMENTAL),
            "widgets + experimental",
        )
    }
}
