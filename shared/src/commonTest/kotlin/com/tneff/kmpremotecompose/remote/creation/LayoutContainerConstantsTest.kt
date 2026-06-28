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

import com.tneff.kmpremotecompose.remote.core.operations.layout.DimensionType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-96 (FC-Layout-Container) — wire-value byte-anchors for the support layer.
 *
 * `DimensionType` ordinals + `POS_*` int constants ARE the wire (written as `int` slots in
 * `WidthModifier`/`HeightModifier` / `BoxLayout`/`ColumnLayout`/etc.). Off-by-one here byte-diverges
 * every container/modifier byte test downstream — pin them against the verified upstream values.
 *
 * Sources (assist-decoded 2026-06-28 against ./androidx):
 *  - `DimensionType` order: `DimensionModifierOperation.Type` enum upstream (same 9-value order).
 *  - `POS_*` values: `BoxLayout.java:43-47` (START=1, CENTER=2, END=3, TOP=4, BOTTOM=5).
 */
class LayoutContainerConstantsTest {

    @Test
    fun dimensionType_ordinals_matchUpstreamWireValues() {
        // The wire writes `type.ordinal` as an int — pin all 9 values explicitly. Reordering
        // breaks every WidthModifier/HeightModifier byte test.
        assertEquals(0, DimensionType.EXACT.ordinal)
        assertEquals(1, DimensionType.FILL.ordinal)
        assertEquals(2, DimensionType.WRAP.ordinal)
        assertEquals(3, DimensionType.WEIGHT.ordinal)
        assertEquals(4, DimensionType.INTRINSIC_MIN.ordinal)
        assertEquals(5, DimensionType.INTRINSIC_MAX.ordinal)
        assertEquals(6, DimensionType.EXACT_DP.ordinal)
        assertEquals(7, DimensionType.FILL_PARENT_MAX_WIDTH.ordinal)
        assertEquals(8, DimensionType.FILL_PARENT_MAX_HEIGHT.ordinal)
        assertEquals(9, DimensionType.entries.size, "no extra entries — wire is locked to these 9")
    }

    @Test
    fun positioningConstants_matchUpstreamBoxLayoutValues() {
        // Wire ints — same slot used for horizontalPositioning and verticalPositioning (the
        // caller picks meaningful pairs). Off-by-one here would byte-diverge every container
        // open op.
        assertEquals(1, POS_START)
        assertEquals(2, POS_CENTER)
        assertEquals(3, POS_END)
        assertEquals(4, POS_TOP)
        assertEquals(5, POS_BOTTOM)
    }
}
