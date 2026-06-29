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
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.creation.Profile
import com.tneff.kmpremotecompose.remote.creation.defaultRcPlatformServices
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * REM-145 S3 — Stage-2 sub-span byte-anchor tests for the Compose-DSL touch modifier surface
 * (`RemoteModifier.onTouchDown/Up/Cancel(actions...)` mirror REM-145 S1's procedural surface).
 *
 * **Anchor strategy:** the 3 corpus touch fixtures contain text content (`CORE_TEXT` / `DATA_TEXT`)
 * which is out of G1 scope (G4 lane). The touch-modifier-group sub-span (11 bytes: MODIFIER_TOUCH_*
 * + VALUE_INTEGER_CHANGE_ACTION + CONTAINER_END) is ID-decoupled at the op level — extract it from
 * the corpus and from a synthetic Compose-DSL emission, assert byte-equal. **Same approach as S1**
 * (proves the Compose-DSL → procedural-DSL → wire path is byte-identical to the procedural-DSL
 * path that S1 already corpus-anchored).
 *
 * **W12 sanity:** all 3 corpus fixtures use map-form non-empty `contentDescription` — REM-146 fix
 * is implicitly verified in the byte-anchor (any allocator drift would shift the sub-span position
 * and break the anchor).
 */
class ComposeCreationTouchModifierAnchorTest {

    private val androidxExperimental = Profile(
        operationsProfiles = Operations.PROFILE_ANDROIDX or Operations.PROFILE_EXPERIMENTAL,
        services = defaultRcPlatformServices(),
    )

    @Test
    fun stage2_onTouchDown_compose_subSpan_matchesCorpusFixture() = runBlocking {
        val corpus = RcCorpus.readFixture("corpus/c_modifier_on_touch_down.rc")
        val corpusGroup = extractTouchModifierGroup(corpus, Operations.MODIFIER_TOUCH_DOWN)
        assertEquals(11, corpusGroup.size)

        val produced = captureSingleRemoteDocument(
            width = 500, height = 500,
            profile = androidxExperimental,
            contentDescription = "DemoModifierOnTouchDown",
        ) {
            RemoteRoot {
                RemoteBox(
                    modifier = RemoteModifier
                        .onTouchDown(valueIntegerChange(valueId = 42, value = 2)),
                ) {}
            }
        }
        val producedGroup = extractTouchModifierGroup(produced, Operations.MODIFIER_TOUCH_DOWN)
        assertContentEquals(
            corpusGroup, producedGroup,
            "Compose-DSL .onTouchDown(valueIntegerChange(42, 2)) sub-span must byte-match " +
                "c_modifier_on_touch_down.rc — proves Q4-element-list ActionElement chain emits " +
                "via REM-145 S1 procedural surface unchanged.",
        )
    }

    @Test
    fun stage2_onTouchUp_compose_subSpan_matchesCorpusFixture() = runBlocking {
        val corpus = RcCorpus.readFixture("corpus/c_modifier_on_touch_up.rc")
        val corpusGroup = extractTouchModifierGroup(corpus, Operations.MODIFIER_TOUCH_UP)

        val produced = captureSingleRemoteDocument(
            width = 500, height = 500,
            profile = androidxExperimental,
            contentDescription = "DemoModifierOnTouchUp",
        ) {
            RemoteRoot {
                RemoteBox(
                    modifier = RemoteModifier
                        .onTouchUp(valueIntegerChange(valueId = 42, value = 3)),
                ) {}
            }
        }
        val producedGroup = extractTouchModifierGroup(produced, Operations.MODIFIER_TOUCH_UP)
        assertContentEquals(corpusGroup, producedGroup)
    }

    @Test
    fun stage2_onTouchCancel_compose_subSpan_matchesCorpusFixture() = runBlocking {
        val corpus = RcCorpus.readFixture("corpus/c_modifier_on_touch_cancel.rc")
        val corpusGroup = extractTouchModifierGroup(corpus, Operations.MODIFIER_TOUCH_CANCEL)

        val produced = captureSingleRemoteDocument(
            width = 500, height = 500,
            profile = androidxExperimental,
            contentDescription = "DemoModifierOnTouchCancel",
        ) {
            RemoteRoot {
                RemoteBox(
                    modifier = RemoteModifier
                        .onTouchCancel(valueIntegerChange(valueId = 42, value = 4)),
                ) {}
            }
        }
        val producedGroup = extractTouchModifierGroup(produced, Operations.MODIFIER_TOUCH_CANCEL)
        assertContentEquals(corpusGroup, producedGroup)
    }

    private fun extractTouchModifierGroup(docBytes: ByteArray, touchOpcode: Int): ByteArray {
        val spans = DocumentReader.inflateWithTrace(docBytes).second
        val touchIdx = spans.indexOfFirst { it.opcode == touchOpcode }
        require(touchIdx >= 0) { "Touch opcode $touchOpcode not found" }
        val endIdx = spans.subList(touchIdx + 1, spans.size)
            .indexOfFirst { it.opcode == Operations.CONTAINER_END }
        require(endIdx >= 0) { "No CONTAINER_END after touch opcode $touchOpcode" }
        val endSpan = spans[touchIdx + 1 + endIdx]
        return docBytes.copyOfRange(spans[touchIdx].byteStart, endSpan.byteEnd)
    }
}
