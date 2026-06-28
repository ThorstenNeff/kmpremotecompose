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
package com.tneff.kmpremotecompose.remote.creation

import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.layout.BoxLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.CanvasContent
import com.tneff.kmpremotecompose.remote.core.operations.layout.CanvasLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.ColumnLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.ContainerEnd
import com.tneff.kmpremotecompose.remote.core.operations.layout.LayoutContent
import com.tneff.kmpremotecompose.remote.core.operations.layout.RootLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.RowLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.StateLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * REM-96 (FC-Layout-Container) — handcrafted byte-anchor tests for the container helpers.
 *
 * The 3 `c_*.rc` corpus fixtures cover Box/Column/Width/Background/Text only — Border, Padding,
 * Scroll, the dual-`ContainerEnd` edge cases, the negative-counter, and the canvas/root
 * Sonderfälle have **no fixture coverage**. These tests are the per-helper byte-anchors that
 * stand in for fixtures (REM-92-Lektion: corpus is necessary, not sufficient).
 *
 * Each test states the verified upstream source it pins (file:line at `./androidx/...`) so the
 * assertion is traceable back to the byte-format authority.
 */
class LayoutContainerHelpersTest {

    @Test
    fun box_emits_open_layoutContent_dualEnd() {
        // Pins upstream `startBox(modifier, START, TOP)` + `endBox()`:
        //   addBoxStart(componentId, -1, hPos, vPos)   // RemoteComposeWriter.java:3551
        //   addContentStart()                          // RemoteComposeWriter.java:3555
        //   <children>
        //   addContainerEnd(); addContainerEnd()       // RemoteComposeWriter.java:3286-3289-shape
        val bytes = document(width = 100, height = 100) {
            box {}
        }
        val ops = DocumentReader.inflate(bytes).operations
        // Skip the prolog (HEADER, possibly more) and find the box-open onwards.
        val boxOpenIdx = ops.indexOfFirst { it is BoxLayout }
        assertTrue(boxOpenIdx >= 0, "BoxLayout op must be emitted")
        val opcodes = ops.drop(boxOpenIdx).map { it.opcode }
        assertEquals(
            listOf(
                Operations.LAYOUT_BOX,
                Operations.LAYOUT_CONTENT,
                Operations.CONTAINER_END,
                Operations.CONTAINER_END,
            ),
            opcodes,
            "box{} must emit BoxLayout + LayoutContent + 2 × ContainerEnd",
        )
    }

    @Test
    fun box_pinsPositioningSlots_fromHorizontalVertical() {
        // Wire-int pin: BoxLayout's horizontalPositioning/verticalPositioning fields take the
        // POS_* int values. Off-by-one here byte-diverges every container open.
        val bytes = document(width = 100, height = 100) {
            box(horizontal = POS_END, vertical = POS_BOTTOM) {}
        }
        val box = DocumentReader.inflate(bytes).operations.first { it is BoxLayout } as BoxLayout
        assertEquals(POS_END, box.horizontalPositioning, "horizontalPositioning = POS_END (3)")
        assertEquals(POS_BOTTOM, box.verticalPositioning, "verticalPositioning = POS_BOTTOM (5)")
        assertEquals(-1, box.animationId, "animationId is always the -1 sentinel")
    }

    @Test
    fun box_userProvidedComponentId_overridesNegativeCounter() {
        // RecordingModifier.componentId(id) → upstream getComponentId(id) passes through if id != -1.
        val bytes = document(width = 100, height = 100) {
            box(modifier = LayoutModifier().componentId(99)) {}
        }
        val box = DocumentReader.inflate(bytes).operations.first { it is BoxLayout } as BoxLayout
        assertEquals(99, box.componentId, "user-pinned componentId must pass through")
    }

    @Test
    fun column_carriesSpacedByFromModifier() {
        val bytes = document(width = 100, height = 100) {
            column(modifier = LayoutModifier().spacedBy(8f)) {}
        }
        val column = DocumentReader.inflate(bytes).operations.first { it is ColumnLayout } as ColumnLayout
        assertEquals(8f, column.spacedBy)
    }

    @Test
    fun row_carriesSpacedByFromModifier() {
        val bytes = document(width = 100, height = 100) {
            row(modifier = LayoutModifier().spacedBy(4f)) {}
        }
        val row = DocumentReader.inflate(bytes).operations.first { it is RowLayout } as RowLayout
        assertEquals(4f, row.spacedBy)
    }

    @Test
    fun nestedContainers_advanceNegativeCounter_perOpenAndPerLayoutContent() {
        // The verified upstream contract (RemoteComposeBuffer.java:1687-1696):
        //   - mGeneratedComponentId init = -1, post-decrement on each getComponentId(-1) call.
        //   - container open with default componentId = -1 → decrements counter (returns -2, -3, ...)
        //   - LayoutContent emit always uses getComponentId(-1) → ALSO decrements.
        // So 3 nested boxes consume 6 decrements: BoxLayout ids = -2, -4, -6; LayoutContent ids = -3, -5, -7.
        val bytes = document(width = 100, height = 100) {
            box {
                box {
                    box {}
                }
            }
        }
        val ops = DocumentReader.inflate(bytes).operations
        val boxes = ops.filterIsInstance<BoxLayout>()
        val contents = ops.filterIsInstance<LayoutContent>()
        assertEquals(3, boxes.size)
        assertEquals(3, contents.size)
        // Outer-first emit order: outer-Box → outer-LayoutContent → middle-Box → middle-LayoutContent → …
        assertEquals(listOf(-2, -4, -6), boxes.map { it.componentId },
            "BoxLayout componentIds = decremented per outer-first open")
        assertEquals(listOf(-3, -5, -7), contents.map { it.componentId },
            "LayoutContent componentIds = interleaved decrement after each container open")
    }

    @Test
    fun negativeCounter_userPinnedDoesNotAdvanceFor_thatContainer() {
        // If user pins componentId(99), upstream getComponentId(99) returns 99 untouched —
        // counter only advances for the LayoutContent (always -1 sentinel) under it.
        val bytes = document(width = 100, height = 100) {
            box(modifier = LayoutModifier().componentId(99)) {
                box {}
            }
        }
        val ops = DocumentReader.inflate(bytes).operations
        val boxes = ops.filterIsInstance<BoxLayout>()
        val contents = ops.filterIsInstance<LayoutContent>()
        assertEquals(listOf(99, -3), boxes.map { it.componentId },
            "outer Box: user-pinned 99 (no counter tick); inner Box: counter advanced from -2 to -3",
        )
        assertEquals(listOf(-2, -4), contents.map { it.componentId },
            "LayoutContent ids: -2 (outer, first decrement) + -4 (inner, after inner-Box -3 decrement)",
        )
    }

    @Test
    fun root_emits_rootLayout_andSingleContainerEnd() {
        // Pins upstream startRoot()/endRoot() (RemoteComposeWriter.java:3350-3359):
        //   - NO LayoutContent (root has no content marker)
        //   - SINGLE ContainerEnd (root is the only container that ends with 1 op, not 2)
        val bytes = document(width = 100, height = 100) {
            root {}
        }
        val ops = DocumentReader.inflate(bytes).operations
        val rootIdx = ops.indexOfFirst { it is RootLayout }
        assertTrue(rootIdx >= 0, "RootLayout must be emitted")
        val tail = ops.drop(rootIdx).map { it.opcode }
        assertEquals(
            listOf(Operations.LAYOUT_ROOT, Operations.CONTAINER_END),
            tail,
            "root{} = RootLayout + 1 × ContainerEnd (NO LayoutContent, NOT 2 × End)",
        )
    }

    @Test
    fun canvas_apiLevel7_emits_canvasContent_andTripleEnd() {
        // Pins upstream startCanvas()/endCanvas() at mApiLevel <= 7 (RemoteComposeWriter.java:3489-3506):
        //   CanvasLayout + LayoutContent + CanvasContent(-1) + <children> + 3 × ContainerEnd.
        // Document default apiLevel = 7 (flat-form baseline). Verify this sequence end-to-end.
        val bytes = document(width = 100, height = 100) {
            canvas {}
        }
        val ops = DocumentReader.inflate(bytes).operations
        val canvasIdx = ops.indexOfFirst { it is CanvasLayout }
        assertTrue(canvasIdx >= 0, "CanvasLayout must be emitted")
        val opcodes = ops.drop(canvasIdx).map { it.opcode }
        assertEquals(
            listOf(
                Operations.LAYOUT_CANVAS,
                Operations.LAYOUT_CONTENT,
                Operations.LAYOUT_CANVAS_CONTENT,
                Operations.CONTAINER_END,
                Operations.CONTAINER_END,
                Operations.CONTAINER_END,
            ),
            opcodes,
            "canvas{} at apiLevel<=7 = CanvasLayout + LayoutContent + CanvasContent + 3 × ContainerEnd",
        )
    }

    @Test
    fun canvas_apiLevel7_canvasContent_usesNegativeCounter() {
        // Pins addCanvasContentStart(-1) → getComponentId(-1) (RemoteComposeBuffer.java:2116-2118):
        // the CanvasContent componentId is ALSO drawn from the negative counter, AFTER the
        // CanvasLayout and the LayoutContent have each consumed one.
        val bytes = document(width = 100, height = 100) {
            canvas {}
        }
        val ops = DocumentReader.inflate(bytes).operations
        val canvas = ops.first { it is CanvasLayout } as CanvasLayout
        val content = ops.first { it is LayoutContent } as LayoutContent
        val canvasContent = ops.first { it is CanvasContent } as CanvasContent
        assertEquals(-2, canvas.componentId, "CanvasLayout consumes the first decrement")
        assertEquals(-3, content.componentId, "LayoutContent consumes the second decrement")
        assertEquals(-4, canvasContent.componentId, "CanvasContent consumes the third decrement")
    }

    @Test
    fun column_modifierOps_emittedBetweenOpen_andLayoutContent() {
        // Per upstream startColumn (RemoteComposeWriter.java:3275-3283): the modifier-ops loop
        // runs AFTER `addColumnStart` and BEFORE `addContentStart`. Stage a fake modifier-op via
        // direct ModifierList manipulation (the 14 typed modifier methods land in the next sub-
        // commit; this test asserts the SEQUENCING invariant regardless of which ops).
        val markerOp = ContainerEnd() // arbitrary op as a sequencing marker — we just need ANY op
        val modifier = LayoutModifier()
        modifier.ops.add(markerOp)
        val bytes = document(width = 100, height = 100) {
            column(modifier = modifier) {}
        }
        val ops = DocumentReader.inflate(bytes).operations
        val openIdx = ops.indexOfFirst { it is ColumnLayout }
        val contentIdx = ops.indexOfFirst { it is LayoutContent }
        // Find the marker between them.
        val markerIdx = ops.subList(openIdx + 1, contentIdx).indexOfFirst { it is ContainerEnd }
        assertTrue(
            openIdx >= 0 && contentIdx > openIdx && markerIdx >= 0,
            "modifier ops must be emitted strictly between Column open and LayoutContent",
        )
    }

    @Test
    fun state_emits_stateLayout_carriesIndexId() {
        val bytes = document(width = 100, height = 100) {
            state(indexId = 77) {}
        }
        val st = DocumentReader.inflate(bytes).operations.first { it is StateLayout } as StateLayout
        assertEquals(77, st.indexId)
        assertEquals(-1, st.animationId)
    }

    @Test
    fun balanceOnException_emitsContainerEnds_anyway() {
        // The try/finally guarantee — mirrors matrixSaved precedent. If `block` throws, the close
        // ops must still emit so the document parses back cleanly.
        val ctx = RemoteComposeContext(
            writer = com.tneff.kmpremotecompose.remote.core.document.RemoteComposeWriter(
                width = 100, height = 100, apiLevel = 6,
            ),
            profile = Profile.Baseline,
        )
        val ex = RuntimeException("simulated")
        try {
            ctx.box {
                throw ex
            }
            fail("box{} block must propagate the exception")
        } catch (caught: RuntimeException) {
            assertEquals(ex, caught)
        }
        val ops = DocumentReader.inflate(ctx.encodeToByteArray()).operations
        // The close ops must still be present.
        val tail = ops.takeLast(2)
        assertEquals(2, tail.count { it is ContainerEnd }, "both ContainerEnd ops must emit in finally")
    }
}
