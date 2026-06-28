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

import com.tneff.kmpremotecompose.conformance.RcCorpus
import com.tneff.kmpremotecompose.conformance.RcDocumentCodec
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawCircle
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-73 (E1 scaffold) + REM-85 (byte-faithful lifecycle prolog) — creation-DSL smoke.
 *
 * Pins the load-bearing properties the rest of Epic E builds on:
 *  1. `document { … }` produces parseable `.rc` bytes (header lifecycle correct).
 *  2. The bytes are *byte-faithful*: `decode → re-encode` equals the original (round-trip basis
 *     for E5 conformance — writer ↔ oracle byte-equality on the read/write path).
 *  3. The recording path goes through `RemoteComposeContext.add(op)` — the same external-drive seam
 *     the E6 Compose-DSL applier will use is what the receiver-lambda walks.
 *  4. **REM-85 prolog (this story):** the DSL auto-selects flat-form (api 6, v1.0.0) for baseline
 *     profiles and auto-emits `DATA_TEXT(42, contentDescription)` + `ROOT_CONTENT_DESCRIPTION(42)`
 *     so a freshly authored document is byte-equal to the upstream `procedure_simple1.rc` oracle.
 */
class DocumentDslSmokeTest {

    @Test
    fun emptyDocument_roundtripsByteForByte() {
        val bytes = document(width = 100, height = 200, contentDescription = "smoke-empty") { }

        assertTrue(bytes.isNotEmpty(), "encoded document must not be empty")
        assertRoundtripIdentical(bytes)
    }

    @Test
    fun documentWithDrawRect_roundtripsByteForByte() {
        val bytes = document(width = 100, height = 100, contentDescription = "smoke-rect") {
            add(DrawRect(left = 0f, top = 0f, right = 100f, bottom = 100f))
        }

        assertRoundtripIdentical(bytes)
    }

    @Test
    fun document_isDeterministicForSameScript() {
        val a = document(width = 50, height = 50) {
            add(DrawRect(0f, 0f, 50f, 50f))
        }
        val b = document(width = 50, height = 50) {
            add(DrawRect(0f, 0f, 50f, 50f))
        }
        assertEquals(a.size, b.size)
        assertTrue(a.contentEquals(b), "identical scripts must produce identical bytes")
    }

    /**
     * REM-85 gate: full-document byte-equality against the upstream `procedure_simple1.rc` oracle.
     * The oracle (61 B) is `RemoteComposeWriter(600,600,"Clock",6,0); drawCircle(150,150,150)` —
     * HEADER (flat v1.0.0, 29 B) · DATA_TEXT(id=42,"Clock") (14 B) · ROOT_CONTENT_DESCRIPTION(42)
     * (5 B) · DRAW_CIRCLE(150,150,150) (13 B). The DSL invocation below must encode to those exact
     * bytes — proves the lifecycle prolog + writer auto-form land the corpus byte-contract.
     */
    @Test
    fun procedureSimple1_fullDocument_matchesOracleByteForByte() {
        val oracle = RcCorpus.readFixture("corpus/procedure_simple1.rc")

        val bytes = document(width = 600, height = 600, contentDescription = "Clock") {
            add(DrawCircle(centerX = 150f, centerY = 150f, radius = 150f))
        }

        if (!oracle.contentEquals(bytes)) {
            throw AssertionError(
                "creation-DSL diverged from procedure_simple1 oracle " +
                    "(oracle=${oracle.size}B, dsl=${bytes.size}B). " +
                    "First diff at byte ${firstDiffIndex(oracle, bytes)}.\n" +
                    "oracle: ${hex(oracle)}\n" +
                    "dsl:    ${hex(bytes)}",
            )
        }
    }

    /**
     * Without an explicit `contentDescription`, the lifecycle reserves no id and emits no body
     * prolog — so the first user op gets id 42 (still the upstream `START_ID`). Pins the rule that
     * id 42 is reserved *iff* there is a content-description to bind it to.
     */
    @Test
    fun document_withoutContentDescription_idAllocatorStartsAt42() {
        val ctx = RemoteComposeContext(
            writer = com.tneff.kmpremotecompose.remote.core.document.RemoteComposeWriter(
                width = 100,
                height = 100,
                apiLevel = 6,
            ),
            profile = Profile.Baseline,
        )
        // No content-description ⇒ id 42 is free for the first user-allocated id.
        assertEquals(42, ctx.ids.nextId())
    }

    @Test
    fun idAllocator_startsAtUpstreamStartId_andIncrementsMonotonically() {
        val ids = IdAllocator()
        assertEquals(IdAllocator.START_ID, ids.peek())
        assertEquals(42, ids.nextId())
        assertEquals(43, ids.nextId())
        assertEquals(44, ids.peek())
        ids.setNextId(100)
        assertEquals(100, ids.nextId())
    }

    private fun hex(b: ByteArray): String =
        b.joinToString(" ") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private fun assertRoundtripIdentical(bytes: ByteArray) {
        val reEncoded = RcDocumentCodec.decode(bytes).reEncode()
        if (!bytes.contentEquals(reEncoded)) {
            throw AssertionError(
                "decode→re-encode diverged from DSL output " +
                    "(orig=${bytes.size}B, roundtrip=${reEncoded.size}B). " +
                    "First diff at byte ${firstDiffIndex(bytes, reEncoded)}.",
            )
        }
    }

    private fun firstDiffIndex(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) if (a[i] != b[i]) return i
        return n
    }
}
