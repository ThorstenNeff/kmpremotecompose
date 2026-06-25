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

    // ---- sub-batch 2: spec-anchored byte-exact (upstream apply() verbatim) ----

    @Test fun touchDown_writesExactBytes() {
        assertContentEquals(bytes(0xDB), writeBytes(TouchDownModifier())) // 219
    }

    @Test fun valueIntegerChangeAction_writesExactBytes() {
        // 212 (0xD4) + valueId 7 + value -2.
        assertContentEquals(bytes(0xD4, 0x00, 0x00, 0x00, 0x07, 0xFF, 0xFF, 0xFF, 0xFE), writeBytes(ValueIntegerChangeAction(7, -2)))
    }

    @Test fun zIndex_writesExactBytes() {
        // 223 (0xDF) + 2.5f (0x40200000).
        assertContentEquals(bytes(0xDF, 0x40, 0x20, 0x00, 0x00), writeBytes(ZIndexModifier(2.5f)))
    }

    @Test fun textLayout_writesExactBytes() {
        // 208 (0xD0) + 11 distinct fields (catches field-order bugs in the largest layout op).
        assertContentEquals(
            bytes(
                0xD0,
                0xFF, 0xFF, 0xFF, 0xFD, // componentId -3
                0xFF, 0xFF, 0xFF, 0xFF, // animationId -1
                0x00, 0x00, 0x00, 0x05, // textId 5
                0x11, 0x22, 0x33, 0x44, // color
                0x3F, 0x80, 0x00, 0x00, // fontSize 1f
                0x00, 0x00, 0x00, 0x02, // fontStyle 2
                0x40, 0x40, 0x00, 0x00, // fontWeight 3f
                0xFF, 0xFF, 0xFF, 0xF9, // fontFamilyId -7
                0x00, 0x00, 0x00, 0x01, // textAlign 1
                0x00, 0x00, 0x00, 0x00, // overflow 0
                0x00, 0x00, 0x00, 0x63, // maxLines 99
            ),
            writeBytes(TextLayout(-3, -1, 5, 0x11223344, 1f, 2, 3f, -7, 1, 0, 99)),
        )
    }

    // ---- sub-batch 2: round-trips (write → read → re-write byte-identical + fields equal) ----

    @Test fun allP2GroupBSubBatch2_roundTrip() {
        assertRoundTrips(ImageLayout(-3, -1, 5, 2, 0.5f), ImageLayout)
        assertRoundTrips(TextLayout(-3, -1, 5, 0x11223344, 1f, 2, 3f, -7, 1, 0, 99), TextLayout)
        assertRoundTrips(FitBoxLayout(-3, -1, 1, 4), FitBoxLayout)
        assertRoundTrips(CollapsibleColumnLayout(-3, -1, 1, 4, 6.25f), CollapsibleColumnLayout)
        // LAYOUT_FLOW deferred (golden 5-field vs source 7-field skew) — see LayoutOps note.
        assertRoundTrips(BorderModifier(1, 42, 0, 0, 2f, 4f, 0.1f, 0.2f, 0.3f, 1f, 2), BorderModifier)
        assertRoundTrips(RoundedClipRectModifier(1f, 2f, 3f, 4f), RoundedClipRectModifier)
        assertRoundTrips(WidthInModifier(10f, 200f), WidthInModifier)
        assertRoundTrips(HeightInModifier(10f, 200f), HeightInModifier)
        assertRoundTrips(ZIndexModifier(2.5f), ZIndexModifier)
        assertRoundTrips(TouchDownModifier(), TouchDownModifier)
        assertRoundTrips(TouchUpModifier(), TouchUpModifier)
        assertRoundTrips(TouchCancelModifier(), TouchCancelModifier)
        assertRoundTrips(ValueIntegerChangeAction(7, -2), ValueIntegerChangeAction)
        assertRoundTrips(ClickArea(1, 42, 0f, 0f, 100f, 50f, -1), ClickArea)
        assertRoundTrips(ScrollModifier(1, 0.5f, 100f, 10f), ScrollModifier)
        assertRoundTrips(CollapsiblePriorityModifier(1, 3.5f), CollapsiblePriorityModifier)
        assertRoundTrips(AlignByModifier(2.5f, 1), AlignByModifier)
    }

    @Test fun register_subBatch2_baseAndExperimentalOverlay() {
        Operations.resetReaders()
        LayoutOps.register()
        for (op in listOf(
            Operations.LAYOUT_IMAGE, Operations.LAYOUT_TEXT, Operations.LAYOUT_FIT_BOX,
            Operations.LAYOUT_COLLAPSIBLE_COLUMN, Operations.MODIFIER_BORDER, Operations.MODIFIER_ROUNDED_CLIP_RECT,
            Operations.MODIFIER_WIDTH_IN, Operations.MODIFIER_HEIGHT_IN, Operations.MODIFIER_ZINDEX,
            Operations.MODIFIER_TOUCH_DOWN, Operations.MODIFIER_TOUCH_UP, Operations.MODIFIER_TOUCH_CANCEL,
            Operations.VALUE_INTEGER_CHANGE_ACTION, Operations.CLICK_AREA, Operations.MODIFIER_SCROLL,
            Operations.MODIFIER_COLLAPSIBLE_PRIORITY,
        )) {
            assertTrue(Operations.isValid(op, 6, 0), "v6 ${Operations.name(op)}")
            assertTrue(Operations.isValid(op, 7, 0), "v7 ${Operations.name(op)}")
        }
        // LAYOUT_FLOW deferred (golden/source field-count skew); MODIFIER_ALIGN_BY is the live overlay op.
        for (op in listOf(Operations.MODIFIER_ALIGN_BY)) {
            assertFalse(Operations.isValid(op, 7, 0), "not baseline ${Operations.name(op)}")
            assertTrue(
                Operations.isValid(op, 7, Operations.PROFILE_ANDROIDX or Operations.PROFILE_EXPERIMENTAL),
                "androidx+exp ${Operations.name(op)}",
            )
        }
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
