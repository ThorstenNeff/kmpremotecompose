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
import com.tneff.kmpremotecompose.remote.creation.document
import com.tneff.kmpremotecompose.remote.creation.pathTween
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * REM-148 S1 — Stage-1 (compose==procedural) byte-equivalence pin for the Compose-DSL PATH_TWEEN
 * primitive ([RemotePathTween]).
 *
 * **Anchor strategy.** The Compose-DSL node defers to the byte-proven procedural helper
 * (`RemoteComposeContext.pathTween`) at Phase-B render time. The Stage-1 invariant therefore
 * collapses to: at the same allocator state with the same args, the Compose-DSL must produce a
 * 17B PATH_TWEEN sub-span byte-identical to the procedural-DSL's. Stage-2 (procedural==corpus)
 * is pinned independently in `PathTweenByteTest`; transitivity gives compose==corpus.
 *
 * **Setup.** Both procedural and Compose use the simplest matching allocator state — no
 * contentDescription, Baseline profile → first user id allocation = 42 → PATH_TWEEN outId=42.
 * Input pathIds (42, 43) are passed verbatim; tween carries a NaN-encoded var-ref `0xFF80002B`
 * (the corpus' tween-payload for ANIMATED_FLOAT id=43) so the W14 NaN-raw-bits preservation
 * path is exercised end-to-end in both surfaces.
 */
class ComposeCreationPathTweenAnchorTest {

    private val tweenVarBits: Int = 0xFF80002B.toInt()
    private val tween: Float = Float.fromBits(tweenVarBits)

    @Test
    fun stage1_compose_pathTween_subSpan_byteEquals_procedural() = runBlocking {
        val procedural = document(
            width = 300, height = 300, profile = Profile.Baseline,
        ) {
            pathTween(pathId1 = 42, pathId2 = 43, tween = tween)
        }
        val proceduralSpan = extractFirstPathTweenSubSpan(procedural)
        assertEquals(17, proceduralSpan.size, "procedural sub-span must be 17B (fixed-width PATH_TWEEN)")

        val produced = captureSingleRemoteDocument(
            width = 300, height = 300, profile = Profile.Baseline,
        ) {
            val slot = rememberRemotePathSlot()
            RemoteRoot {
                RemotePathTween(slot = slot, pathId1 = 42, pathId2 = 43, tween = tween)
            }
        }
        val producedSpan = extractFirstPathTweenSubSpan(produced)
        assertEquals(17, producedSpan.size, "compose-DSL sub-span must be 17B")

        assertContentEquals(
            proceduralSpan, producedSpan,
            "Compose-DSL RemotePathTween must emit the same 17B PATH_TWEEN sub-span as the " +
                "procedural pathTween helper at the same allocator state — proves Stage-1 " +
                "(compose==procedural) for REM-148 S1.",
        )
    }

    /**
     * Stage-3 determinism for the Compose-DSL path — two independent captures of the same Compose
     * tree must produce byte-identical PATH_TWEEN sub-spans. Catches any per-capture allocator /
     * NaN-bit drift specific to the Compose runtime layer.
     */
    @Test
    fun stage3_compose_pathTween_twoCaptures_subSpan_byteIdentical() = runBlocking {
        val first = captureSingleRemoteDocument(
            width = 300, height = 300, profile = Profile.Baseline,
        ) {
            val slot = rememberRemotePathSlot()
            RemoteRoot {
                RemotePathTween(slot = slot, pathId1 = 42, pathId2 = 43, tween = tween)
            }
        }
        val second = captureSingleRemoteDocument(
            width = 300, height = 300, profile = Profile.Baseline,
        ) {
            val slot = rememberRemotePathSlot()
            RemoteRoot {
                RemotePathTween(slot = slot, pathId1 = 42, pathId2 = 43, tween = tween)
            }
        }
        assertContentEquals(
            extractFirstPathTweenSubSpan(first),
            extractFirstPathTweenSubSpan(second),
            "Two independent Compose captures produced divergent PATH_TWEEN sub-spans — non-determinism",
        )
    }

    private fun extractFirstPathTweenSubSpan(docBytes: ByteArray): ByteArray {
        val spans = DocumentReader.inflateWithTrace(docBytes).second
        val pathTweenSpan = spans.first { it.opcode == Operations.PATH_TWEEN }
        return docBytes.copyOfRange(pathTweenSpan.byteStart, pathTweenSpan.byteEnd)
    }
}
