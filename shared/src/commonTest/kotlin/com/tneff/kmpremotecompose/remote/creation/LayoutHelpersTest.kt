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
import com.tneff.kmpremotecompose.remote.core.operations.layout.RootContentBehavior
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-86 (E2) — `setRootContentBehavior` byte tests. Pins the W#2 default tuple `(0, 34, 2, 6)`
 * (PO 2026-06-28, assist-decoded from `procedure_gradient1`/`center_text1`/`look_up1`/`text_path_effects`).
 */
class LayoutHelpersTest {

    @Test
    fun setRootContentBehavior_defaults_match_W2_oracle() {
        val bytes = document(width = 100, height = 100) {
            setRootContentBehavior()
        }
        val op = DocumentReader.inflate(bytes).operations
            .first { it is RootContentBehavior } as RootContentBehavior
        assertEquals(0, op.scroll, "scroll = NONE")
        assertEquals(34, op.alignment, "alignment = CENTER (32+2)")
        assertEquals(2, op.sizing, "sizing = SIZING_SCALE")
        assertEquals(6, op.mode, "mode = SCALE_FILL_BOUNDS")
    }

    @Test
    fun setRootContentBehavior_customValues_roundTrip() {
        // Matches procedure_simple2: setRootContentBehavior(NONE, ALIGNMENT_CENTER, SIZING_SCALE, SCALE_FIT).
        val bytes = document(width = 100, height = 100) {
            setRootContentBehavior(
                scroll = ROOT_SCROLL_NONE,
                alignment = ROOT_ALIGNMENT_CENTER,
                sizing = ROOT_SIZING_SCALE,
                mode = ROOT_SCALE_FIT,
            )
        }
        val op = DocumentReader.inflate(bytes).operations
            .first { it is RootContentBehavior } as RootContentBehavior
        assertEquals(0, op.scroll)
        assertEquals(34, op.alignment)
        assertEquals(2, op.sizing)
        assertEquals(4, op.mode, "mode = SCALE_FIT (= 4)")
    }

    @Test
    fun layoutHelpers_constants_matchUpstreamOrdinals() {
        assertEquals(0, ROOT_SCROLL_NONE)
        assertEquals(2, ROOT_ALIGNMENT_VERTICAL_CENTER)
        assertEquals(32, ROOT_ALIGNMENT_HORIZONTAL_CENTER)
        assertEquals(34, ROOT_ALIGNMENT_CENTER)
        assertEquals(2, ROOT_SIZING_SCALE)
        assertEquals(4, ROOT_SCALE_FIT)
        assertEquals(6, ROOT_SCALE_FILL_BOUNDS)
    }

    @Test
    fun setRootContentBehavior_doesNotAllocateIds() {
        // W#3: ROOT_CONTENT_BEHAVIOR is NOT id-bearing.
        document(width = 100, height = 100, contentDescription = "Clock") {
            assertEquals(43, ids.peek())
            setRootContentBehavior()
            assertEquals(43, ids.peek())
        }
    }
}
