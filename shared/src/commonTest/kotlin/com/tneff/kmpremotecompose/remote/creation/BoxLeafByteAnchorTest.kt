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

import com.tneff.kmpremotecompose.conformance.IgnoreOnWasm
import com.tneff.kmpremotecompose.conformance.RcCorpus
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.layout.DimensionType
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

private val ANDROIDX_PROFILE = Profile(
    operationsProfiles = com.tneff.kmpremotecompose.remote.core.operations.Operations.PROFILE_ANDROIDX,
    services = defaultRcPlatformServices(),
)

/**
 * REM-96-followup — byte-anchor for the additive [boxLeaf] helper. Pins two properties at once:
 *
 *   1. **Op-shape pin** — `boxLeaf{}` emits exactly `LAYOUT_BOX + <modifiers> + CONTAINER_END`
 *      (one end, no LayoutContent). The standard [box] helper emits `LAYOUT_BOX + <modifiers> +
 *      LAYOUT_CONTENT + CONTAINER_END + CONTAINER_END` (`LayoutContainerHelpersTest.kt:62-71`);
 *      `boxLeaf` is deliberately the *other* upstream shape (`RemoteComposeWriter.java:4043-4051`).
 *
 *   2. **Byte-pin vs `c_box.rc` corpus oracle (128 B, apiLevel=7)** — a `root { boxLeaf(...) }`
 *      document with the exact modifiers from the oracle (width 200/EXACT, height 200/EXACT,
 *      red background) round-trips byte-for-byte. The existing `box` helper *cannot* reproduce
 *      `c_box.rc` because its `LayoutContent`+dual-end shape is byte-divergent from the
 *      childless-Box corpus pattern — that mismatch is exactly the impedance gap REM-96-followup
 *      closes (and what REM-128-S2 needs to drive byte-true Compose-creation of container docs).
 */
class BoxLeafByteAnchorTest {

    @Test
    fun boxLeaf_emitsLayoutBox_modifiers_singleContainerEnd_noLayoutContent() {
        val bytes = document(width = 400, height = 400) {
            root {
                boxLeaf()
            }
        }
        val ops = DocumentReader.inflate(bytes).operations
        val boxIdx = ops.indexOfFirst { it.opcode == Operations.LAYOUT_BOX }
        // After the BOX open: exactly one CONTAINER_END (closing the box itself), then the
        // root's own CONTAINER_END. No LayoutContent in between.
        assertEquals(
            listOf(Operations.LAYOUT_BOX, Operations.CONTAINER_END, Operations.CONTAINER_END),
            ops.drop(boxIdx).map { it.opcode },
            "boxLeaf{} must emit LAYOUT_BOX + single CONTAINER_END (no LayoutContent, no dual end). " +
                "The trailing 2nd CONTAINER_END closes the surrounding root{}.",
        )
    }

    @Test
    @IgnoreOnWasm
    fun boxLeaf_matchesCBoxOracle_byteForByte() {
        // c_box.rc — decoded shape (REM-96-followup probe, verified):
        //   Header(w=400, h=400, apiLevel=7, profiles=0x200 = PROFILE_ANDROIDX → map-form header),
        //   RootLayout(componentId=-2),
        //   BoxLayout(componentId=-3, anim=-1, hPos=POS_CENTER, vPos=POS_CENTER),
        //   WidthModifier(EXACT, 200f), HeightModifier(EXACT, 200f),
        //   BackgroundModifier(r=1, g=0, b=0, a=1, shapeType=0),
        //   ContainerEnd (closes BoxLayout, no LayoutContent), ContainerEnd (closes RootLayout)
        // contentDescription="" (empty string) is the upstream default — c_box.rc has the
        // DOC_CONTENT_DESCRIPTION property present as a zero-length STRING; passing null here
        // would skip the property entirely and the property count would diverge (4 vs 3).
        val produced = document(
            width = 400,
            height = 400,
            profile = ANDROIDX_PROFILE,
            contentDescription = "",
        ) {
            root {
                boxLeaf(
                    modifier = LayoutModifier()
                        .width(DimensionType.EXACT, 200f)
                        .height(DimensionType.EXACT, 200f)
                        .background(color = 0xffff0000.toInt()),
                )
            }
        }
        val oracle = RcCorpus.readFixture("corpus/c_box.rc")
        assertContentEquals(
            oracle,
            produced,
            "boxLeaf + width/height/background must byte-match c_box.rc — proves the childless-Box " +
                "upstream pattern (RemoteComposeWriter.java:4043) is reproducible end-to-end via the DSL.",
        )
    }
}
