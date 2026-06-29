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
package com.tneff.kmpremotecompose.creation.compose

import com.tneff.kmpremotecompose.conformance.RcCorpus
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.layout.DimensionType
import com.tneff.kmpremotecompose.remote.creation.LayoutModifier
import com.tneff.kmpremotecompose.remote.creation.Profile
import com.tneff.kmpremotecompose.remote.creation.boxLeaf
import com.tneff.kmpremotecompose.remote.creation.colorExpressionHsv
import com.tneff.kmpremotecompose.remote.creation.defaultRcPlatformServices
import com.tneff.kmpremotecompose.remote.creation.document
import com.tneff.kmpremotecompose.remote.creation.root
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * REM-144 S3 — Stage-2 byte-anchor for the new `RemoteModifier.backgroundColorRef(colorId)` and
 * slot-form `background(colorIdSlot)` overloads. Closes the REM-130-T3-deferred dynamic-background
 * full-doc Stage-2 (the raw-int form reproduces `c_modifier_background_id.rc` byte-for-byte;
 * the slot-form is transitively §2-sound per TechSpec §6 W11 — same op encoding, region-0
 * colorId vs system colorId).
 *
 * Empirical-decode-grounded (REM-144 scoping, transient probe deleted):
 *  - `c_modifier_background_id.rc` (145 B, PROFILE_ANDROIDX, 400×400, contentDescription=""):
 *    LAYOUT_BOX(POS_CENTER, POS_CENTER) + width(EXACT 200) + height(EXACT 200) +
 *    MODIFIER_BACKGROUND(flags=2, colorId=1, rgba=(0,0,0,0), shape=0) + padding(10,10,10,10).
 *  - **colorId=1 is a system colour id, NOT a region-0 ColorExpression** — no primitive
 *    composable needed for the raw-int form; the caller writes the system id verbatim.
 */
class ComposeCreationDynamicBackgroundAnchorTest {

    private val androidx: Profile = Profile(
        operationsProfiles = Operations.PROFILE_ANDROIDX,
        services = defaultRcPlatformServices(),
    )

    /**
     * `c_modifier_background_id.rc` full-doc byte-anchor via the raw-int form
     * `RemoteModifier.backgroundColorRef(colorId = 1)`. **No primitive composable** — the
     * corpus references a system colour id directly.
     */
    @Test
    fun stage2_backgroundColorRef_rawInt_matchesCModifierBackgroundIdOracle_byteForByte() = runBlocking {
        val produced = captureSingleRemoteDocument(
            width = 400, height = 400, profile = androidx, contentDescription = "",
        ) {
            RemoteRoot {
                RemoteBoxLeaf(
                    modifier = RemoteModifier
                        .width(DimensionType.EXACT, 200f)
                        .height(DimensionType.EXACT, 200f)
                        .backgroundColorRef(colorId = 1)
                        .padding(10f),
                )
            }
        }
        assertContentEquals(
            RcCorpus.readFixture("corpus/c_modifier_background_id.rc"),
            produced,
            "Compose-DSL RemoteModifier.backgroundColorRef(colorId=1) must byte-match " +
                "c_modifier_background_id.rc — closes the REM-130-T3-deferred dynamic-background " +
                "full-doc Stage-2 anchor (raw-int form, system colourId path).",
        )
    }

    /**
     * Slot-form transitive §2 (TechSpec §6 W11): emit a region-0 ColorExpression via
     * `RemoteColorExpressionHsv`, drive the new `background(colorIdSlot)` overload, and compare
     * byte-for-byte against the procedural-DSL equivalent. The slot-form emits the SAME
     * MODIFIER_BACKGROUND op shape as the raw-int form — only the `colorId` value differs
     * (here a region-0 allocated id, vs the corpus's system colorId=1). Op shape is corpus-
     * anchored via the REM-144 S1 sub-span anchor + the raw-int full-doc anchor above.
     */
    @Test
    fun stage1_backgroundColorRef_slot_form_composeEqualsProcedural() = runBlocking {
        val viaCompose = captureSingleRemoteDocument(
            width = 400, height = 400, profile = androidx, contentDescription = "",
        ) {
            RemoteRoot {
                val colorSlot = rememberRemoteColorSlot()
                RemoteColorExpressionHsv(colorSlot, hue = 0.5f, saturation = 0.5f, value = 0.5f)
                RemoteBoxLeaf(
                    modifier = RemoteModifier
                        .width(DimensionType.EXACT, 100f)
                        .height(DimensionType.EXACT, 100f)
                        .background(colorIdSlot = colorSlot),
                )
            }
        }
        // The procedural equivalent: emit ColorExpression via colorExpressionHsv (returns plain
        // int id), then call backgroundColorRef(colorId) with that id. ID allocation determinism
        // (virgin pool — empty contentDescription per REM-141 DocumentDsl bug-fix) ensures the
        // ColorExpression id is 42 in both compose- and procedural-side captures.
        val viaProcedural = document(
            width = 400, height = 400, profile = androidx, contentDescription = "",
        ) {
            root {
                val colorId = colorExpressionHsv(hue = 0.5f, saturation = 0.5f, value = 0.5f)
                boxLeaf(
                    modifier = LayoutModifier()
                        .width(DimensionType.EXACT, 100f)
                        .height(DimensionType.EXACT, 100f)
                        .backgroundColorRef(colorId = colorId),
                )
            }
        }
        assertContentEquals(
            viaProcedural,
            viaCompose,
            "Compose-DSL background(colorIdSlot=…) must produce bytes byte-identical to the " +
                "procedural-DSL backgroundColorRef(colorId=ColorExpression.id) — slot-form " +
                "transitive §2 anchor (W11). The op shape is corpus-anchored via REM-144 S1.",
        )
    }
}
