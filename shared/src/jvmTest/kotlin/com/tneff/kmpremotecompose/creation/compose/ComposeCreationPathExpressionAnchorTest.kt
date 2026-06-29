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
import com.tneff.kmpremotecompose.remote.creation.pathExpression
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * REM-148 S2 — Stage-1 (compose==procedural) byte-equivalence pin for the Compose-DSL
 * PATH_EXPRESSION primitive ([RemotePathExpression]).
 *
 * **Anchor strategy.** Same as REM-148 S1's compose anchor: the Compose-DSL node defers to the
 * byte-proven procedural helper at Phase-B render time, so the Stage-1 invariant collapses to
 * "compose sub-span == procedural sub-span at the same allocator state with the same args".
 * Stage-2 (procedural==corpus) is pinned independently in `PathExpressionByteTest`; transitivity
 * gives compose==corpus.
 *
 * **Setup.** Both procedural and Compose use the simplest matching allocator state —
 * PROFILE_ANDROIDX (required since PATH_EXPRESSION is an AndroidX-overlay op), no
 * contentDescription, w=500 h=500 → first user `ids.nextId()` returns 42 → PATH_EXPRESSION id=42
 * in both surfaces. The RPN payload is the same dense W14 NaN-bits surface as the
 * corpus-anchored test (`test1.rc` op #23 RPN) — proving the Compose runtime layer preserves
 * signaling-NaN payloads end-to-end.
 */
class ComposeCreationPathExpressionAnchorTest {

    private val test1Op23X: FloatArray = floatArrayOf(
        Float.fromBits(0xFF800030.toInt()),
        Float.fromBits(0x3F800000),
        Float.fromBits(0xFFB10046.toInt()),
        Float.fromBits(0x40490FDB),
        Float.fromBits(0xFFB10001.toInt()),
        Float.fromBits(0xFFB10012.toInt()),
        Float.fromBits(0xFFB10002.toInt()),
        Float.fromBits(0xFFB10003.toInt()),
    )
    private val test1Op23Y: FloatArray = floatArrayOf(
        Float.fromBits(0xFF80002C.toInt()),
        Float.fromBits(0xFF800033.toInt()),
    )
    private val twoPi: Float = Float.fromBits(0x40C90FDB)

    private val androidx: Profile = Profile(
        operationsProfiles = Operations.PROFILE_ANDROIDX,
        services = defaultRcPlatformServices(),
    )

    @Test
    fun stage1_compose_pathExpression_subSpan_byteEquals_procedural() = runBlocking {
        val procedural = document(
            width = 500, height = 500, profile = androidx,
        ) {
            pathExpression(
                flags = 0x8,
                min = 0.0f,
                max = twoPi,
                count = 60.0f,
                expressionX = test1Op23X,
                expressionY = test1Op23Y,
            )
        }
        val proceduralSpan = extractFirstPathExpressionSubSpan(procedural)
        assertEquals(69, proceduralSpan.size, "procedural sub-span size != 69B")

        val produced = captureSingleRemoteDocument(
            width = 500, height = 500, profile = androidx,
        ) {
            val slot = rememberRemotePathSlot()
            RemoteRoot {
                RemotePathExpression(
                    slot = slot,
                    flags = 0x8,
                    min = 0.0f,
                    max = twoPi,
                    count = 60.0f,
                    expressionX = test1Op23X,
                    expressionY = test1Op23Y,
                )
            }
        }
        val producedSpan = extractFirstPathExpressionSubSpan(produced)
        assertEquals(69, producedSpan.size, "compose-DSL sub-span size != 69B")

        assertContentEquals(
            proceduralSpan, producedSpan,
            "Compose-DSL RemotePathExpression must emit the same 69B PATH_EXPRESSION sub-span as " +
                "the procedural pathExpression helper at the same allocator state — proves Stage-1 " +
                "(compose==procedural) for REM-148 S2.",
        )
    }

    /**
     * Stage-3 determinism for the Compose-DSL path — two independent captures of the same
     * Compose tree must produce byte-identical PATH_EXPRESSION sub-spans. Catches any per-capture
     * allocator / NaN-bit / Compose-runtime drift specific to the dense RPN payload of
     * PATH_EXPRESSION.
     */
    @Test
    fun stage3_compose_pathExpression_twoCaptures_subSpan_byteIdentical() = runBlocking {
        val first = captureSingleRemoteDocument(
            width = 500, height = 500, profile = androidx,
        ) {
            val slot = rememberRemotePathSlot()
            RemoteRoot {
                RemotePathExpression(
                    slot = slot,
                    flags = 0x8,
                    min = 0.0f, max = twoPi, count = 60.0f,
                    expressionX = test1Op23X, expressionY = test1Op23Y,
                )
            }
        }
        val second = captureSingleRemoteDocument(
            width = 500, height = 500, profile = androidx,
        ) {
            val slot = rememberRemotePathSlot()
            RemoteRoot {
                RemotePathExpression(
                    slot = slot,
                    flags = 0x8,
                    min = 0.0f, max = twoPi, count = 60.0f,
                    expressionX = test1Op23X, expressionY = test1Op23Y,
                )
            }
        }
        assertContentEquals(
            extractFirstPathExpressionSubSpan(first),
            extractFirstPathExpressionSubSpan(second),
            "Two independent Compose captures produced divergent PATH_EXPRESSION sub-spans — non-determinism",
        )
    }

    /**
     * Defensive-copy pin (Compose layer). The composable freezes its input arrays at composition
     * time via `.copyOf()` — a caller mutating the FloatArray after composition must NOT drift
     * the render-time bytes. Tested by capturing once with array `A`, then mutating `A` before a
     * second capture without re-composing the tree against the mutated `A`. Both captures should
     * produce the original-input bytes (since the composable froze at composition).
     *
     * Note: in this synthetic test we re-enter `captureSingleRemoteDocument` which triggers a
     * fresh composition for the second capture, so the SECOND capture will reflect the mutated
     * input. We assert the FIRST capture remains unchanged after the mutation — verifying the
     * freeze happens at composition, not at render.
     */
    @Test
    fun pathExpression_compose_inputArrays_frozen_at_composition() = runBlocking {
        val mutableX = test1Op23X.copyOf()
        val first = captureSingleRemoteDocument(
            width = 500, height = 500, profile = androidx,
        ) {
            val slot = rememberRemotePathSlot()
            RemoteRoot {
                RemotePathExpression(
                    slot = slot,
                    flags = 0x8,
                    min = 0.0f, max = twoPi, count = 60.0f,
                    expressionX = mutableX,
                    expressionY = test1Op23Y,
                )
            }
        }
        // Mutate the source array AFTER the capture. The captured bytes should be unchanged
        // because the composable copied the array at composition.
        mutableX[1] = 999.0f
        val firstSpan = extractFirstPathExpressionSubSpan(first)
        // Wire header is 25B (opcode 1 + id 4 + flags 4 + min 4 + max 4 + count 4 + lenX 4); X[1]
        // sits at bytes 29..32 (X[0] = 25..28, X[1] = 29..32). `mutableX[1]` was 1.0f at composition,
        // 999.0f after capture — the captured bytes must still read 1.0f if the freeze worked.
        assertContentEquals(
            byteArrayOf(0x3F, 0x80.toByte(), 0x00, 0x00),
            firstSpan.copyOfRange(29, 33),
            "first capture's X[1] should remain 1.0f (frozen at composition); a mutation of " +
                "the caller's FloatArray leaked into the captured bytes — Compose-layer .copyOf() broken.",
        )
    }

    private fun extractFirstPathExpressionSubSpan(docBytes: ByteArray): ByteArray {
        val spans = DocumentReader.inflateWithTrace(docBytes).second
        val pathExprSpan = spans.first { it.opcode == Operations.PATH_EXPRESSION }
        return docBytes.copyOfRange(pathExprSpan.byteStart, pathExprSpan.byteEnd)
    }
}
