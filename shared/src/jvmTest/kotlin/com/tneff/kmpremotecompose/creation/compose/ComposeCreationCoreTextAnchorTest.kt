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
import com.tneff.kmpremotecompose.remote.core.operations.layout.CoreText
import com.tneff.kmpremotecompose.remote.creation.Profile
import com.tneff.kmpremotecompose.remote.creation.coreText
import com.tneff.kmpremotecompose.remote.creation.coreTextBoolParam
import com.tneff.kmpremotecompose.remote.creation.coreTextFloatParam
import com.tneff.kmpremotecompose.remote.creation.coreTextIntParam
import com.tneff.kmpremotecompose.remote.creation.defaultRcPlatformServices
import com.tneff.kmpremotecompose.remote.creation.document
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * REM-149 — Stage-1 (compose==procedural) byte-equivalence pin for the Compose-DSL CORE_TEXT
 * primitive ([RemoteCoreText]).
 *
 * **Anchor strategy.** The Compose-DSL node defers to the byte-proven procedural helper
 * (`RemoteComposeContext.coreText`) at Phase-B render time. The Stage-1 invariant therefore
 * collapses to: with the same args, the Compose-DSL must produce a CORE_TEXT sub-span
 * byte-identical to the procedural-DSL's. Stage-2 (procedural==corpus) is pinned independently in
 * `CoreTextByteTest`; transitivity gives compose==corpus.
 *
 * **Setup.** Both surfaces use PROFILE_ANDROIDX (required since CORE_TEXT is an AndroidX-overlay
 * op). CORE_TEXT is not id-bearing so allocator alignment is unnecessary; `textId=42` is passed
 * explicitly, matching the corpus primary-anchor's first text id (corresponds to
 * `c_modifier_align_by_baseline.rc` op#0 in the CORE_TEXT family).
 */
class ComposeCreationCoreTextAnchorTest {

    private val androidx: Profile = Profile(
        operationsProfiles = Operations.PROFILE_ANDROIDX,
        services = defaultRcPlatformServices(),
    )

    /**
     * Primary 17B param set — same as `CoreTextByteTest`'s Stage-2 anchor.
     */
    private fun primaryParams(): List<CoreText.Param> = listOf(
        coreTextIntParam(id = 1, value = -5),
        coreTextFloatParam(id = 5, value = 60.0f),
    )

    @Test
    fun stage1_compose_coreText_subSpan_byteEquals_procedural() = runBlocking {
        val procedural = document(
            width = 500, height = 500, profile = androidx, contentDescription = "ct",
        ) {
            coreText(textId = 42, params = primaryParams())
        }
        val proceduralSpan = extractFirstCoreTextSubSpan(procedural)
        assertEquals(17, proceduralSpan.size, "procedural sub-span size != 17B")

        val produced = captureSingleRemoteDocument(
            width = 500, height = 500, profile = androidx, contentDescription = "ct",
        ) {
            RemoteRoot {
                RemoteCoreText(textId = 42, params = primaryParams())
            }
        }
        val producedSpan = extractFirstCoreTextSubSpan(produced)
        assertEquals(17, producedSpan.size, "compose-DSL sub-span size != 17B")

        assertContentEquals(
            proceduralSpan, producedSpan,
            "Compose-DSL RemoteCoreText must emit the same 17B CORE_TEXT sub-span as the " +
                "procedural coreText helper with the same args — proves Stage-1 " +
                "(compose==procedural) for REM-149.",
        )
    }

    /**
     * Stage-3 determinism for the Compose-DSL path — two independent captures of the same Compose
     * tree must produce byte-identical CORE_TEXT sub-spans. Includes a NaN-encoded P_FLOAT
     * variable ref (signaling-NaN payload `0xFF800030`) and a P_BOOLEAN param — any per-capture
     * NaN-bit drift or param-encoding non-determinism would surface here.
     */
    @Test
    fun stage3_compose_coreText_twoCaptures_subSpan_byteIdentical() = runBlocking {
        val nanVar = Float.fromBits(0xFF800030.toInt())
        val params = listOf(
            coreTextIntParam(id = 1, value = -5),
            coreTextFloatParam(id = 5, value = 60.0f),
            coreTextFloatParam(id = 7, value = nanVar), // fontWeight as var-ref
            coreTextBoolParam(id = 18, value = true),   // underline
        )
        val first = captureSingleRemoteDocument(
            width = 500, height = 500, profile = androidx, contentDescription = "ct",
        ) {
            RemoteRoot {
                RemoteCoreText(textId = 42, params = params)
            }
        }
        val second = captureSingleRemoteDocument(
            width = 500, height = 500, profile = androidx, contentDescription = "ct",
        ) {
            RemoteRoot {
                RemoteCoreText(textId = 42, params = params)
            }
        }
        assertContentEquals(
            extractFirstCoreTextSubSpan(first),
            extractFirstCoreTextSubSpan(second),
            "Two independent Compose captures produced divergent CORE_TEXT sub-spans — non-determinism",
        )
    }

    /**
     * Defensive-copy pin (Compose layer). The composable freezes its input list AND each param's
     * value-ByteArray at composition time. A caller mutating either after composition must NOT
     * drift the render-time bytes. The Compose path uses the same `coreText(...)` procedural
     * helper at render, which also `.copyOf()`s — so for full coverage we mutate the source
     * ByteArray AFTER composition and assert the first capture's bytes are unchanged.
     *
     * Note: in this synthetic test re-entering `captureSingleRemoteDocument` triggers a fresh
     * composition, so the SECOND capture WILL reflect the mutated input. We assert the FIRST
     * capture's bytes remained frozen — verifying the freeze happens at composition, not at
     * render.
     */
    @Test
    fun coreText_compose_inputParams_frozen_at_composition() = runBlocking {
        val mutableValueBytes = byteArrayOf(0x42, 0x70, 0x00, 0x00) // 60.0f
        val sharedParam = CoreText.Param(id = 5, value = mutableValueBytes)
        val first = captureSingleRemoteDocument(
            width = 500, height = 500, profile = androidx, contentDescription = "ct",
        ) {
            RemoteRoot {
                RemoteCoreText(
                    textId = 42,
                    params = listOf(coreTextIntParam(id = 1, value = -5), sharedParam),
                )
            }
        }
        // Mutate the source value bytes AFTER the capture. The captured bytes should be
        // unchanged because the composable copied the array at composition.
        mutableValueBytes[0] = 0x00
        val firstSpan = extractFirstCoreTextSubSpan(first)
        // 17B layout: bytes 0..6 header; bytes 7..11 = param#0 (id+4B value); bytes 12..16 =
        // param#1 (id 0x05 + 4B 60.0f). The mutated byte sits at sub-span index 13.
        assertContentEquals(
            byteArrayOf(0x42, 0x70, 0x00, 0x00),
            firstSpan.copyOfRange(13, 17),
            "first capture's param#1 value should remain 60.0f bytes (frozen at composition); " +
                "a mutation of the caller's source ByteArray leaked into the captured bytes — " +
                "Compose-layer .copyOf() broken.",
        )
    }

    private fun extractFirstCoreTextSubSpan(docBytes: ByteArray): ByteArray {
        val spans = DocumentReader.inflateWithTrace(docBytes).second
        val ctSpan = spans.first { it.opcode == Operations.CORE_TEXT }
        return docBytes.copyOfRange(ctSpan.byteStart, ctSpan.byteEnd)
    }
}
