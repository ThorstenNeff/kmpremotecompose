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
import com.tneff.kmpremotecompose.remote.creation.POS_CENTER
import com.tneff.kmpremotecompose.remote.creation.Profile
import com.tneff.kmpremotecompose.remote.creation.defaultRcPlatformServices
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * REM-128 S2 — Triple-Pin Stage-2 anchors for the container surface (RemoteBox + RemoteColumn +
 * RemoteRow). Each pin builds the Compose-DSL equivalent of a corpus container fixture and asserts
 * byte-equality against the upstream-produced oracle. Three sources of bytes for the same input;
 * Stage-2 is the source-of-truth gate (TechSpec §3, mirror REM-119/126).
 *
 * The childless-Box routing is the load-bearing detail (see [RemoteBoxNode.render]): the
 * inner-Boxes in `c_column.rc` / `c_row.rc` are content-less leaves (single `CONTAINER_END`, no
 * `LayoutContent`), which our existing REM-96 `box()` helper would byte-diverge against. REM-96-
 * followup `0ed918d` added [boxLeaf]; this triple-pin proves the Compose-DSL routes correctly.
 *
 * **Bug #2 lesson applied (REM-96-followup):** `DOC_CONTENT_DESCRIPTION` is present in every
 * `c_*.rc` fixture as a zero-length STRING (verified empirically against c_column/c_row, not just
 * c_box) — passing `contentDescription = ""` is required for byte-equality on all three.
 *
 * **Profile:** every container fixture is `PROFILE_ANDROIDX (0x200) / apiLevel=7 (map-form
 * header)` — verified via the REM-128-S2 recon spike. Same value as the boxLeaf byte-anchor.
 */
class ComposeCreationContainerAnchorTest {

    private val androidx: Profile = Profile(
        operationsProfiles = Operations.PROFILE_ANDROIDX,
        services = defaultRcPlatformServices(),
    )

    // ---- Stage-2 vs c_box.rc (RemoteBox alone — childless → routes to boxLeaf) ----

    /**
     * `c_box.rc` (128 B): RootLayout + leaf-BoxLayout(POS_CENTER/POS_CENTER) + Width(EXACT, 200)
     * + Height(EXACT, 200) + Background(red) + single CONTAINER_END + RootLayout's CONTAINER_END.
     * The same upstream oracle the REM-96-followup `BoxLeafByteAnchorTest` pinned via the
     * procedural DSL — this pin proves the *Compose surface* hits identical bytes.
     */
    @Test
    fun stage2_box_matchesCBoxOracle_byteForByte() = runBlocking {
        val produced = captureSingleRemoteDocument(
            width = 400,
            height = 400,
            profile = androidx,
            contentDescription = "",
        ) {
            RemoteRoot {
                RemoteBox(
                    modifier = LayoutModifier()
                        .width(DimensionType.EXACT, 200f)
                        .height(DimensionType.EXACT, 200f)
                        .background(color = 0xffff0000.toInt()),
                    horizontal = POS_CENTER,
                    vertical = POS_CENTER,
                )
            }
        }
        assertContentEquals(
            RcCorpus.readFixture("corpus/c_box.rc"),
            produced,
            "Compose-DSL RemoteBox (childless → boxLeaf) must byte-match c_box.rc oracle.",
        )
    }

    // ---- Stage-2 vs c_column.rc (RemoteColumn + 3 childless RemoteBoxes — proves recursive walk) ----

    /**
     * `c_column.rc` (348 B): RootLayout + ColumnLayout(POS_START/POS_TOP/spacedBy=0) + Height(FILL,
     * NaN) + Background(grey 0.8/0.8/0.8/1) + LayoutContent + 3 childless BoxLayouts (POS_CENTER/
     * POS_CENTER, 50×50, red/green/blue, each with a single CONTAINER_END) + dual CONTAINER_END
     * (closes LayoutContent + ColumnLayout) + ROOT's CONTAINER_END. This is the **recursive
     * container-walk gate**: if the depth-first child render were wrong (W2 order), the inner-Box
     * sequence would diverge.
     */
    @Test
    fun stage2_column_matchesCColumnOracle_byteForByte() = runBlocking {
        val produced = captureSingleRemoteDocument(
            width = 400,
            height = 400,
            profile = androidx,
            contentDescription = "",
        ) {
            RemoteRoot {
                RemoteColumn(
                    modifier = LayoutModifier()
                        .height(DimensionType.FILL, Float.NaN)
                        .background(r = 0.8f, g = 0.8f, b = 0.8f, a = 1f),
                ) {
                    coloredLeafBox(0xffff0000.toInt())
                    coloredLeafBox(0xff00ff00.toInt())
                    coloredLeafBox(0xff0000ff.toInt())
                }
            }
        }
        assertContentEquals(
            RcCorpus.readFixture("corpus/c_column.rc"),
            produced,
            "Compose-DSL RemoteColumn { 3× childless RemoteBox } must byte-match c_column.rc oracle " +
                "— proves the recursive child render-walk + childless→boxLeaf routing.",
        )
    }

    // ---- Stage-2 vs c_row.rc (RemoteRow + 3 childless RemoteBoxes) ----

    /**
     * `c_row.rc` (348 B): same shape as c_column but with `RowLayout` + `WidthModifier(FILL, NaN)`
     * (rows want full width). Same triple of red/green/blue 50×50 boxLeaf children.
     */
    @Test
    fun stage2_row_matchesCRowOracle_byteForByte() = runBlocking {
        val produced = captureSingleRemoteDocument(
            width = 400,
            height = 400,
            profile = androidx,
            contentDescription = "",
        ) {
            RemoteRoot {
                RemoteRow(
                    modifier = LayoutModifier()
                        .width(DimensionType.FILL, Float.NaN)
                        .background(r = 0.8f, g = 0.8f, b = 0.8f, a = 1f),
                ) {
                    coloredLeafBox(0xffff0000.toInt())
                    coloredLeafBox(0xff00ff00.toInt())
                    coloredLeafBox(0xff0000ff.toInt())
                }
            }
        }
        assertContentEquals(
            RcCorpus.readFixture("corpus/c_row.rc"),
            produced,
            "Compose-DSL RemoteRow { 3× childless RemoteBox } must byte-match c_row.rc oracle.",
        )
    }

    /**
     * Helper for the c_column / c_row inner-Box pattern: a childless [RemoteBox] (50×50 + a solid
     * background colour). DRY — the three inner boxes in each fixture differ only in colour.
     */
    @androidx.compose.runtime.Composable
    private fun coloredLeafBox(argb: Int) {
        RemoteBox(
            modifier = LayoutModifier()
                .width(DimensionType.EXACT, 50f)
                .height(DimensionType.EXACT, 50f)
                .background(color = argb),
        )
    }
}
