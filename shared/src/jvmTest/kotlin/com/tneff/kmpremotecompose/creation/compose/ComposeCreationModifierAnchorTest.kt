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

import androidx.compose.ui.graphics.Color
import com.tneff.kmpremotecompose.conformance.RcCorpus
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.layout.DimensionType
import com.tneff.kmpremotecompose.remote.creation.Profile
import com.tneff.kmpremotecompose.remote.creation.defaultRcPlatformServices
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * REM-128 S3a — Triple-Pin Stage-2 anchors for the T1 modifier MVP (width / height / background;
 * **86% corpus coverage with 3 modifiers**). Each pin builds the Compose-DSL equivalent of the
 * upstream `c_modifier_*.rc` fixture and asserts byte-equality against the oracle. Stage-2 is the
 * source-of-truth gate (TechSpec §3 / S3-§2 Q5: 3 separate anchors for failure attribution).
 *
 * **Bug #2 lesson pre-applied (TechSpec §3 / mirror REM-96-followup):** properties-table
 * empirically verified before writing the pins — all 3 oracles are `apiLevel=7 / PROFILE_ANDROIDX`
 * with header props `[5=400, 6=400, 9="", 14=512]` (decoded probe was transient, not committed):
 *
 *   - `c_modifier_width.rc` (128 B): RootLayout + leaf-BoxLayout(CENTER/CENTER) + Width(EXACT,
 *     250) + Height(EXACT, 100) + Background(blue) + 2× ContainerEnd.
 *   - `c_modifier_height.rc` (128 B): same shape, Width(EXACT, 100) + Height(EXACT, 250) +
 *     Background(green).
 *   - `c_modifier_background.rc` (247 B): RootLayout + ColumnLayout(POS_START/POS_TOP) + Width(FILL,
 *     NaN) + Height(FILL, NaN) + LayoutContent + 2× childless BoxLayouts (CENTER/CENTER, 100×100,
 *     red and blue) + 5× ContainerEnd. **Tests the S2 recursive container walk + S3 modifier
 *     emission together** — divergence in either would fail this pin.
 *
 * **`androidx` profile + `contentDescription = ""`:** mandatory for all three oracles (Bug #2
 * lesson). Not assumed — empirically verified.
 */
class ComposeCreationModifierAnchorTest {

    private val androidx: Profile = Profile(
        operationsProfiles = Operations.PROFILE_ANDROIDX,
        services = defaultRcPlatformServices(),
    )

    // ---- Stage-2 vs c_modifier_width.rc ----

    @Test
    fun stage2_width_matchesCModifierWidthOracle_byteForByte() = runBlocking {
        val produced = captureSingleRemoteDocument(
            width = 400,
            height = 400,
            profile = androidx,
            contentDescription = "",
        ) {
            RemoteRoot {
                RemoteBoxLeaf(
                    modifier = RemoteModifier
                        .width(DimensionType.EXACT, 250f)
                        .height(DimensionType.EXACT, 100f)
                        .background(color = 0xff0000ff.toInt()),
                )
            }
        }
        assertContentEquals(
            RcCorpus.readFixture("corpus/c_modifier_width.rc"),
            produced,
            "Compose-DSL RemoteModifier.width(EXACT, 250) must byte-match c_modifier_width.rc oracle.",
        )
    }

    // ---- Stage-2 vs c_modifier_height.rc ----

    @Test
    fun stage2_height_matchesCModifierHeightOracle_byteForByte() = runBlocking {
        val produced = captureSingleRemoteDocument(
            width = 400,
            height = 400,
            profile = androidx,
            contentDescription = "",
        ) {
            RemoteRoot {
                RemoteBoxLeaf(
                    modifier = RemoteModifier
                        .width(DimensionType.EXACT, 100f)
                        .height(DimensionType.EXACT, 250f)
                        .background(color = 0xff00ff00.toInt()),
                )
            }
        }
        assertContentEquals(
            RcCorpus.readFixture("corpus/c_modifier_height.rc"),
            produced,
            "Compose-DSL RemoteModifier.height(EXACT, 250) must byte-match c_modifier_height.rc oracle.",
        )
    }

    // ---- Stage-2 vs c_modifier_background.rc (container-+modifier combination) ----

    /**
     * `c_modifier_background.rc` (247 B) exercises both Background **and** the S2 recursive
     * container walk: a Column with two leaf-Box children. Note the Column has BOTH Width(FILL,
     * NaN) and Height(FILL, NaN) — different from c_column.rc which only has Height(FILL).
     */
    @Test
    fun stage2_background_matchesCModifierBackgroundOracle_byteForByte() = runBlocking {
        val produced = captureSingleRemoteDocument(
            width = 400,
            height = 400,
            profile = androidx,
            contentDescription = "",
        ) {
            RemoteRoot {
                RemoteColumn(
                    modifier = RemoteModifier
                        .width(DimensionType.FILL, Float.NaN)
                        .height(DimensionType.FILL, Float.NaN),
                ) {
                    RemoteBoxLeaf(
                        modifier = RemoteModifier
                            .width(DimensionType.EXACT, 100f)
                            .height(DimensionType.EXACT, 100f)
                            .background(color = 0xffff0000.toInt()),
                    )
                    RemoteBoxLeaf(
                        modifier = RemoteModifier
                            .width(DimensionType.EXACT, 100f)
                            .height(DimensionType.EXACT, 100f)
                            .background(color = 0xff0000ff.toInt()),
                    )
                }
            }
        }
        assertContentEquals(
            RcCorpus.readFixture("corpus/c_modifier_background.rc"),
            produced,
            "Compose-DSL RemoteColumn + 2× RemoteBoxLeaf(background) must byte-match c_modifier_background.rc.",
        )
    }

    // ---- Q1 sRGB round-trip guard: Color overload produces byte-identical output to Int ----

    /**
     * TechSpec §2 Q1 guard: `RemoteModifier.background(Color.Red)` routes through
     * `Color.toArgb()` to the `Int` path. Both must produce byte-identical output (no
     * sRGB/precision/premultiply surprise). Reproduces `c_modifier_height.rc` (green background)
     * via the [Color] overload and asserts equality against the same oracle the int-path test
     * pins — if the round-trip introduces any byte drift, this would catch it independently.
     */
    @Test
    fun stage2_height_viaColorOverload_matchesCModifierHeightOracle() = runBlocking {
        val produced = captureSingleRemoteDocument(
            width = 400,
            height = 400,
            profile = androidx,
            contentDescription = "",
        ) {
            RemoteRoot {
                RemoteBoxLeaf(
                    modifier = RemoteModifier
                        .width(DimensionType.EXACT, 100f)
                        .height(DimensionType.EXACT, 250f)
                        .background(Color(0xff00ff00.toInt())),
                )
            }
        }
        assertContentEquals(
            RcCorpus.readFixture("corpus/c_modifier_height.rc"),
            produced,
            "Color(0xff00ff00).toArgb() round-trip must produce byte-identical output to the Int " +
                "overload — pins TechSpec §2 Q1 sRGB/precision guard.",
        )
    }
}
