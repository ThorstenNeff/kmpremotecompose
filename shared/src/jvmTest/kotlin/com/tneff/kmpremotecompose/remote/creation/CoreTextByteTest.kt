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
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.layout.CoreText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * REM-149 — Stage-2 corpus byte-anchor tests for the procedural `CORE_TEXT` surface
 * (`RemoteComposeContext.coreText(textId, params)`).
 *
 * **Anchor strategy.** CORE_TEXT is a **variable-length op** — fixed 7-byte header
 * (`opcode 0xEF (1B) + textId 4B + paramCount 2B`) + per-param `paramId 1B + value Nbits` where N is
 * fixed by TextStyle's PARAM_TYPE map (4B for INT/FLOAT, 2B for SHORT, 1B for BYTE/BOOLEAN, variable
 * for PA_INT / PA_FLOAT / PA_STRING). The op is **not id-bearing** — it references an existing
 * region-0 text id (from a preceding `addText`-family op); the byte-anchor passes [textId] explicitly
 * so the allocator state does not need to be aligned for the ref itself.
 *
 * **Primary corpus source.** `c_modifier_align_by_baseline.rc` — its first CORE_TEXT sub-span is
 * the smallest in the 173-fixture corpus (17B = 7B header + 2× 5B styled params). Empirical hex
 * (offset 167..184): `ef 00 00 00 2a 00 02 01 ff ff ff fb 05 42 70 00 00`
 *   - opcode `ef` = 239 (CORE_TEXT)
 *   - textId `00 00 00 2a` = 42 (corpus's first user text id)
 *   - paramCount `00 02` = 2
 *   - param#0: id `01` (P_INT, TextStyle id) value `ff ff ff fb` = -5 (signed BE Int)
 *   - param#1: id `05` (P_FLOAT, fontSize) value `42 70 00 00` = 60.0f
 *
 * **Backup corpus source.** `text_baseline.rc` has 21× CORE_TEXT sub-spans (3-row × 7-col text grid)
 * with diverse textIds — IDEAL for the non-vacuity pairwise-distinct pin (REM-147-trap-lesson).
 *
 * **W14 NaN-bits pin.** P_FLOAT params (TextStyle ids 5, 7, 12, 13, 14, 25, 26) constructed via
 * [coreTextFloatParam] go through `Float.toRawBits()` — a NaN-encoded variable ref
 * (`Float.fromBits(0xFF800030.toInt())` = system id 0x30) round-trips byte-exact to the wire.
 * `Number.toFloat()` coercion or value-class boxing would canonicalise signaling-NaN payloads.
 */
class CoreTextByteTest {

    private val androidx: Profile = Profile(
        operationsProfiles = Operations.PROFILE_ANDROIDX,
        services = defaultRcPlatformServices(),
    )

    /**
     * Re-verify the documented probe data (Resume-step #3 of REM-149) — the corpus fixture's first
     * CORE_TEXT sub-span MUST still match the 17B hex captured during the scoping probe. A drift
     * here would mean the corpus binary itself changed between sessions; escalate before touching
     * any byte-equality assertions downstream.
     */
    @Test
    fun probe_corpus_baseline_first_subSpan_matches_documented_17B_hex() {
        val corpus = RcCorpus.readFixture("corpus/c_modifier_align_by_baseline.rc")
        val spans = extractCoreTextSubSpans(corpus)
        assertTrue(spans.isNotEmpty(), "c_modifier_align_by_baseline.rc must contain ≥1 CORE_TEXT op")
        val first = spans[0]
        assertEquals(17, first.size, "first CORE_TEXT sub-span size != 17B — corpus probe drifted")
        val expectedHex = byteArrayOf(
            0xEF.toByte(),                                            // opcode 239
            0x00, 0x00, 0x00, 0x2A,                                   // textId = 42
            0x00, 0x02,                                               // paramCount = 2
            0x01, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFB.toByte(), // param#0: id=1 (P_INT) value=-5
            0x05, 0x42, 0x70, 0x00, 0x00,                             // param#1: id=5 (P_FLOAT) value=60.0f
        )
        assertContentEquals(
            expectedHex, first,
            "documented probe hex no longer matches corpus first CORE_TEXT sub-span — escalate before " +
                "proceeding with byte-anchor; corpus may have changed.",
        )
    }

    /**
     * Primary Stage-2 corpus byte-anchor. Reproduces `c_modifier_align_by_baseline.rc`'s first
     * CORE_TEXT sub-span (17B) byte-for-byte via the procedural helper. Allocator state does NOT
     * need to be burned since CORE_TEXT is not id-bearing — `textId=42` is passed explicitly.
     */
    @Test
    fun stage2_coreText_baseline_subSpan_matchesCorpus() {
        val produced = document(
            width = 500, height = 500,
            profile = androidx,
            contentDescription = "ct",
        ) {
            coreText(
                textId = 42,
                params = listOf(
                    coreTextIntParam(id = 1, value = -5),
                    coreTextFloatParam(id = 5, value = 60.0f),
                ),
            )
        }
        val producedSpans = extractCoreTextSubSpans(produced)
        assertEquals(1, producedSpans.size, "expected exactly 1 CORE_TEXT sub-span in produced doc")
        assertEquals(17, producedSpans[0].size, "primary anchor sub-span must be 17B")

        val corpus = RcCorpus.readFixture("corpus/c_modifier_align_by_baseline.rc")
        val corpusSpans = extractCoreTextSubSpans(corpus)
        assertContentEquals(
            corpusSpans[0], producedSpans[0],
            "CORE_TEXT first sub-span (17B) byte-mismatch — corpus vs DSL output diverged.",
        )
    }

    /**
     * Non-vacuity pin (assist-lesson REM-147). The 21 corpus CORE_TEXT sub-spans in
     * `text_baseline.rc` must (a) all start with opcode 0xEF, (b) be ≥7B (header), and (c) differ
     * pairwise on at least one field-byte. Proves the primary anchor's byte-equality assertion
     * passes on MEANINGFULLY DIFFERENT inputs (different textIds, different P_INT param#0 values),
     * not on a memcpy/zero-fill happy-path.
     */
    @Test
    fun stage3_coreText_textBaseline_subSpans_diverse_and_pairwise_distinct() {
        val corpus = RcCorpus.readFixture("corpus/text_baseline.rc")
        val corpusSpans = extractCoreTextSubSpans(corpus)
        assertTrue(
            corpusSpans.size >= 10,
            "text_baseline.rc must have ≥10 CORE_TEXT ops (empirical probe = 21); got ${corpusSpans.size}",
        )
        corpusSpans.forEachIndexed { idx, span ->
            assertEquals(
                0xEF.toByte(), span[0],
                "sub-span #$idx opcode != 0xEF (239)",
            )
            assertTrue(span.size >= 7, "sub-span #$idx size < 7B (smaller than header)")
        }
        // Pairwise field-byte distinctness — at least one non-shared-opcode byte must differ.
        for (i in corpusSpans.indices) {
            for (j in i + 1 until corpusSpans.size) {
                val fi = corpusSpans[i].copyOfRange(1, corpusSpans[i].size)
                val fj = corpusSpans[j].copyOfRange(1, corpusSpans[j].size)
                assertFalse(
                    fi.contentEquals(fj),
                    "sub-span pair ($i, $j) in text_baseline.rc have identical field bytes — corpus is memcpy-vacuous",
                )
            }
        }
    }

    /**
     * Stage-3 determinism — emit the same inputs 5× and assert all produced docs are byte-equal.
     * Includes a NaN-encoded variable ref in a P_FLOAT param (signaling-NaN payload `0xFF800030`);
     * any boxing or coercion through `Number.toFloat()` would canonicalise NaN bits and surface as
     * cross-run drift here.
     */
    @Test
    fun stage3_coreText_determinism_5x_byteEqual() {
        val nanVarRef = Float.fromBits(0xFF800030.toInt())  // system var id 0x30
        val docs = (1..5).map {
            document(
                width = 500, height = 500,
                profile = androidx,
                contentDescription = "ct",
            ) {
                coreText(
                    textId = 42,
                    params = listOf(
                        coreTextIntParam(id = 1, value = -5),
                        coreTextFloatParam(id = 5, value = 60.0f),
                        coreTextFloatParam(id = 7, value = nanVarRef),  // fontWeight as var-ref
                        coreTextBoolParam(id = 18, value = true),       // underline
                    ),
                )
            }
        }
        for (i in 1..4) {
            assertContentEquals(
                docs[0], docs[i],
                "doc #$i differs from doc #0 — coreText non-deterministic",
            )
        }
    }

    /**
     * W14 NaN-raw-bits-preservation pin. A NaN-encoded variable ref in a P_FLOAT param
     * (`Float.fromBits(0xFF800030.toInt())`) must serialize to its EXACT 4-byte BE pattern
     * `FF 80 00 30` — proving the typed-param factory's `toRawBits()` path preserves signaling-NaN
     * payloads end-to-end. A `Number.toFloat()` intermediate would canonicalise the bits to a
     * quiet-NaN like `7F C0 00 00`.
     */
    @Test
    fun coreText_W14_floatParam_preserves_nanRawBits() {
        val nanBits = 0xFF800030.toInt()
        val nanVarRef = Float.fromBits(nanBits)
        val produced = document(
            width = 500, height = 500,
            profile = androidx,
            contentDescription = "ct",
        ) {
            coreText(
                textId = 42,
                params = listOf(coreTextFloatParam(id = 5, value = nanVarRef)),
            )
        }
        val spans = extractCoreTextSubSpans(produced)
        assertEquals(1, spans.size)
        // Header is 7B (opcode 1 + textId 4 + paramCount 2); param starts at byte 7:
        //   byte 7      = paramId (= 0x05)
        //   bytes 8..11 = 4B value (the NaN bits)
        val valueBytes = spans[0].copyOfRange(8, 12)
        assertContentEquals(
            byteArrayOf(0xFF.toByte(), 0x80.toByte(), 0x00, 0x30), valueBytes,
            "P_FLOAT NaN raw-bits not preserved — typed-param factory or wire-write coerced the bits",
        )
    }

    /**
     * Defensive-copy pin (Q4 lock). A caller mutating the inputs AFTER calling `coreText` must NOT
     * change the queued op's wire bytes. The procedural helper `.copyOf()`s each param's value
     * ByteArray into a freshly-allocated `CoreText.Param` at emission time; tested by emitting
     * twice — once mutating the source ByteArray between emits — and asserting both CORE_TEXT
     * sub-spans have identical param-value bytes (i.e. the first emit was frozen, the mutation
     * only landed in the second).
     */
    @Test
    fun coreText_inputParams_defensiveCopy_freezes_queuedOp() {
        val sharedValueBytes = byteArrayOf(0x42, 0x70, 0x00, 0x00) // 60.0f
        val sharedParam = CoreText.Param(id = 5, value = sharedValueBytes)
        val produced = document(
            width = 500, height = 500, profile = androidx, contentDescription = "ct",
        ) {
            coreText(textId = 42, params = listOf(sharedParam))
            sharedValueBytes[0] = 0x00 // mutate the original ByteArray AFTER the first emit
            coreText(textId = 42, params = listOf(sharedParam))
        }
        val spans = extractCoreTextSubSpans(produced)
        assertEquals(2, spans.size)
        // Same layout as above: param value sits at bytes 8..11.
        val firstValue = spans[0].copyOfRange(8, 12)
        val secondValue = spans[1].copyOfRange(8, 12)
        assertContentEquals(
            byteArrayOf(0x42, 0x70, 0x00, 0x00), firstValue,
            "first emit param value was not frozen by .copyOf() — defensive-copy guard broken",
        )
        assertContentEquals(
            byteArrayOf(0x00, 0x70, 0x00, 0x00), secondValue,
            "second emit should reflect the post-emit mutation, but did not",
        )
    }

    private fun extractCoreTextSubSpans(docBytes: ByteArray): List<ByteArray> {
        val spans = DocumentReader.inflateWithTrace(docBytes).second
        return spans
            .filter { it.opcode == Operations.CORE_TEXT }
            .map { docBytes.copyOfRange(it.byteStart, it.byteEnd) }
    }
}
