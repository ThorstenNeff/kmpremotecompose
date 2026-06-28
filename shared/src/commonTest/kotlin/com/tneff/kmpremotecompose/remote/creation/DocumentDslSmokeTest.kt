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
import com.tneff.kmpremotecompose.remote.core.operations.RootContentDescription
import com.tneff.kmpremotecompose.remote.core.operations.TextData
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawCircle
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-73 (FC-E1) — creation-DSL smoke. Proves the three load-bearing E1 properties end-to-end on
 * every shared-module target:
 *
 *  1. `document { … }` produces parseable `.rc` bytes (header lifecycle correct).
 *  2. The bytes are *byte-faithful*: `decode → re-encode` equals the original. This is the
 *     round-trip basis for E5 conformance (writer ↔ oracle byte-equality).
 *  3. The recording path goes through `RemoteComposeContext.add(op)` — i.e. the external-drive
 *     seam the E6 Compose-DSL applier will use is the same path the receiver-lambda uses.
 *
 * The DSL is an op-emitter, not an encoder (§2): if (2) holds for a doc we built, the same proof
 * extends to every op we later wire into helpers, because `Operation.write()` is already byte-proven
 * by the L1 conformance harness (173/173). E1 ships no draw helpers yet; the smoke uses the raw
 * `add(DrawRect(…))` path that E2's `drawRect()` helper will route through.
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
     * Post-header replay proof: the op stream the DSL emits after the header matches the upstream
     * `procedure_simple1.rc` oracle byte-for-byte. This pins what E1 actually owns — op identity,
     * id allocation from `START_ID = 42`, emission order, operand encoding — independently of the
     * header form the writer stamps.
     *
     * Why not full byte-equality vs the fixture: `procedure_simple1.rc` carries a flat-form (API-6)
     * header (`major=0, minor=1, patch=0`, no property map) while our `RemoteComposeWriter` always
     * stamps the current map-form (API-7, `major=1, minor=1`). Both are valid `.rc` headers, but
     * they cannot be byte-equal. Adding a flat-emit mode (or an api-version param on `document`)
     * is the lever to lift this to full byte-equality in a follow-up — flagged to PO; out of E1
     * scope to avoid changing L1 writer semantics here.
     *
     * `procedure_simple1.rc` (61 B): HEADER (29 B) · DATA_TEXT(id=42, "Clock") (14 B) ·
     * ROOT_CONTENT_DESCRIPTION(id=42) (5 B) · DRAW_CIRCLE(150, 150, 150) (13 B). The 32-byte
     * post-header tail is what this test asserts.
     */
    @Test
    fun procedureSimple1_postHeaderTail_matchesOracleByteForByte() {
        val oracle = RcCorpus.readFixture("corpus/procedure_simple1.rc")
        val oracleHeaderLen = 29 // see Header.write: opcode(1) + major(4) + minor(4) + patch(4) + width(4) + height(4) + caps(8)
        val oracleTail = oracle.copyOfRange(oracleHeaderLen, oracle.size)

        val bytes = document(width = 600, height = 600) {
            val textId = ids.nextId()
            add(TextData(textId, "Clock"))
            add(RootContentDescription(textId))
            add(DrawCircle(centerX = 150f, centerY = 150f, radius = 150f))
        }
        val dslTail = bytes.copyOfRange(bytes.size - oracleTail.size, bytes.size)
        if (!oracleTail.contentEquals(dslTail)) {
            throw AssertionError(
                "post-header tail diverged from procedure_simple1 oracle " +
                    "(${oracleTail.size}B each).\n" +
                    "oracle: ${hex(oracleTail)}\n" +
                    "dsl:    ${hex(dslTail)}"
            )
        }
    }

    private fun hex(b: ByteArray): String =
        b.joinToString(" ") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

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

    private fun assertRoundtripIdentical(bytes: ByteArray) {
        val reEncoded = RcDocumentCodec.decode(bytes).reEncode()
        if (!bytes.contentEquals(reEncoded)) {
            throw AssertionError(
                "decode→re-encode diverged from DSL output " +
                    "(orig=${bytes.size}B, roundtrip=${reEncoded.size}B). " +
                    "First diff at byte ${firstDiffIndex(bytes, reEncoded)}."
            )
        }
    }

    private fun firstDiffIndex(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) if (a[i] != b[i]) return i
        return n
    }
}
