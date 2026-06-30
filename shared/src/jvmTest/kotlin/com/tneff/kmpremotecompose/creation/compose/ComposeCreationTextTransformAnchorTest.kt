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

import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.creation.Profile
import com.tneff.kmpremotecompose.remote.creation.defaultRcPlatformServices
import com.tneff.kmpremotecompose.remote.creation.document
import com.tneff.kmpremotecompose.remote.creation.textTransform
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * REM-149-S2 — Stage-1 (compose==procedural) byte-equivalence pin for the Compose-DSL
 * TEXT_TRANSFORM primitive ([RemoteTextTransform]).
 *
 * **Anchor strategy.** The Compose-DSL node defers to the byte-proven procedural helper
 * (`RemoteComposeContext.textTransform`) at Phase-B render time. Stage-1 collapses to: at the same
 * allocator state with the same args, the Compose-DSL must produce a 21B TEXT_TRANSFORM sub-span
 * byte-identical to the procedural-DSL's. Stage-2 (procedural==corpus) is pinned independently in
 * `TextTransformByteTest`; transitivity gives compose==corpus.
 *
 * **Setup.** Both surfaces use PROFILE_ANDROIDX (required since TEXT_TRANSFORM is an
 * AndroidX-overlay op), the simplest matching allocator state — flat-form reserves id=42 for
 * contentDescription, no burn → first user `nextId()` returns 43 → textTransform textId=43 in
 * both surfaces. Args: srcId1=55, start=0f, len=-1f (corpus convention), operation=2.
 */
class ComposeCreationTextTransformAnchorTest {

    private val androidx: Profile = Profile(
        operationsProfiles = Operations.PROFILE_ANDROIDX,
        services = defaultRcPlatformServices(),
    )

    @Test
    fun stage1_compose_textTransform_subSpan_byteEquals_procedural() = runBlocking {
        val procedural = document(
            width = 400, height = 400, profile = androidx, contentDescription = "tt",
        ) {
            textTransform(srcId1 = 55, start = 0.0f, len = -1.0f, operation = 2)
        }
        val proceduralSpan = extractFirstTextTransformSubSpan(procedural)
        assertEquals(21, proceduralSpan.size, "procedural sub-span size != 21B (fixed-width)")

        val produced = captureSingleRemoteDocument(
            width = 400, height = 400, profile = androidx, contentDescription = "tt",
        ) {
            val slot = rememberRemoteTextSlot()
            RemoteRoot {
                RemoteTextTransform(
                    slot = slot,
                    srcId1 = 55,
                    start = 0.0f,
                    len = -1.0f,
                    operation = 2,
                )
            }
        }
        val producedSpan = extractFirstTextTransformSubSpan(produced)
        assertEquals(21, producedSpan.size, "compose-DSL sub-span size != 21B")

        assertContentEquals(
            proceduralSpan, producedSpan,
            "Compose-DSL RemoteTextTransform must emit the same 21B TEXT_TRANSFORM sub-span as " +
                "the procedural textTransform helper at the same allocator state — proves Stage-1 " +
                "(compose==procedural) for REM-149-S2.",
        )
    }

    /**
     * Stage-3 determinism for the Compose-DSL path — two independent captures of the same Compose
     * tree must produce byte-identical TEXT_TRANSFORM sub-spans. Uses a NaN-encoded var-ref in
     * `start` (signaling-NaN payload `0xFF800030`); any Compose-runtime canonicalisation of NaN
     * bits would surface as cross-capture drift.
     */
    @Test
    fun stage3_compose_textTransform_twoCaptures_subSpan_byteIdentical() = runBlocking {
        val nanVar = Float.fromBits(0xFF800030.toInt())
        val first = captureSingleRemoteDocument(
            width = 400, height = 400, profile = androidx, contentDescription = "tt",
        ) {
            val slot = rememberRemoteTextSlot()
            RemoteRoot {
                RemoteTextTransform(
                    slot = slot, srcId1 = 55, start = nanVar, len = -1.0f, operation = 3,
                )
            }
        }
        val second = captureSingleRemoteDocument(
            width = 400, height = 400, profile = androidx, contentDescription = "tt",
        ) {
            val slot = rememberRemoteTextSlot()
            RemoteRoot {
                RemoteTextTransform(
                    slot = slot, srcId1 = 55, start = nanVar, len = -1.0f, operation = 3,
                )
            }
        }
        assertContentEquals(
            extractFirstTextTransformSubSpan(first),
            extractFirstTextTransformSubSpan(second),
            "Two independent Compose captures produced divergent TEXT_TRANSFORM sub-spans — non-determinism",
        )
    }

    private fun extractFirstTextTransformSubSpan(docBytes: ByteArray): ByteArray {
        val spans = DocumentReader.inflateWithTrace(docBytes).second
        val ttSpan = spans.first { it.opcode == Operations.TEXT_TRANSFORM }
        return docBytes.copyOfRange(ttSpan.byteStart, ttSpan.byteEnd)
    }
}
