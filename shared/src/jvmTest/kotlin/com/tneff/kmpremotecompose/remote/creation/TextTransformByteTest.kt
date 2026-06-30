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
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * REM-149-S2 — Stage-2 corpus byte-anchor tests for the procedural `TEXT_TRANSFORM` surface
 * (`RemoteComposeContext.textTransform(srcId1, start, len, operation)`).
 *
 * **Anchor strategy.** TEXT_TRANSFORM is a **21-byte fixed-width BE op**:
 *   `opcode 0xC7 (1B) + textId 4B + srcId1 4B + start 4B float + len 4B float + operation 4B`
 * — no varint, no length prefix. Sub-spans extracted by opcode position. The wire id (textId) is
 * the only id-bearing field; srcId1 / start / len / operation are caller-controlled and don't
 * depend on allocator state.
 *
 * **Corpus source.** `demo_text_transform.rc` op #0..#4 — all 5 TEXT_TRANSFORM occurrences in the
 * 173-fixture corpus. Empirical decode (probe-verified):
 *   - #0 textId=57 srcId1=55 start=0.0f len=-1.0f operation=2
 *   - #1 textId=60 srcId1=55 start=0.0f len=-1.0f operation=1
 *   - #2 textId=63 srcId1=55 start=0.0f len=-1.0f operation=3
 *   - #3 textId=66 srcId1=55 start=0.0f len=-1.0f operation=4
 *   - #4 textId=69 srcId1=55 start=0.0f len=-1.0f operation=5
 *
 * **Allocator-pin.** textIds jump by 3 between emits (57→60→63→66→69) → 2 unrelated region-0
 * allocations sit between each TEXT_TRANSFORM in the corpus. Reproduced by `burn-15 before first
 * emit + burn-2 between each subsequent emit`. Map-form PROFILE_ANDROIDX does NOT reserve id=42
 * (REM-146 fix); first `nextId()` returns 42, so burn-15 (42..56) leaves `nextId() == 57` = corpus
 * textId for op #0.
 *
 * **W14 NaN-bits pin.** [start] / [len] are `Float`-typed; `Float.fromBits(...)` var-refs round-trip
 * via `WireBuffer.writeFloat`'s `toRawBits()` write path byte-exact. Coercion through
 * `Number.toFloat()` would canonicalise NaN payloads and break the corpus byte-anchor.
 *
 * **Non-vacuity (REM-147 trap-lesson).** The 5 sub-spans differ on textId (5 distinct values) AND
 * on operation (5 distinct values 1..5) → ANY two corpus sub-spans are field-byte-distinct on
 * BOTH a positional and a semantic axis. Cross-pair distinctness proves byte-equality passes on
 * meaningfully different inputs, not on memcpy/zero-fill.
 */
class TextTransformByteTest {

    private val androidx: Profile = Profile(
        operationsProfiles = Operations.PROFILE_ANDROIDX,
        services = defaultRcPlatformServices(),
    )

    /** Re-verify the documented probe data — drift-detector for Session-zu-Session-Resume. */
    @Test
    fun probe_corpus_first_subSpan_matches_documented_21B_hex() {
        val corpus = RcCorpus.readFixture("corpus/demo_text_transform.rc")
        val spans = extractTextTransformSubSpans(corpus)
        assertEquals(5, spans.size, "demo_text_transform.rc must contain exactly 5 TEXT_TRANSFORM ops")
        spans.forEach { assertEquals(21, it.size, "every TEXT_TRANSFORM sub-span must be 21B fixed-width") }
        val expectedHex0 = byteArrayOf(
            0xC7.toByte(),                                            // opcode 199
            0x00, 0x00, 0x00, 0x39,                                   // textId = 57
            0x00, 0x00, 0x00, 0x37,                                   // srcId1 = 55
            0x00, 0x00, 0x00, 0x00,                                   // start = 0.0f
            0xBF.toByte(), 0x80.toByte(), 0x00, 0x00,                 // len = -1.0f
            0x00, 0x00, 0x00, 0x02,                                   // operation = 2
        )
        assertContentEquals(
            expectedHex0, spans[0],
            "documented probe hex no longer matches corpus first TEXT_TRANSFORM sub-span — escalate before " +
                "proceeding with byte-anchor; corpus may have changed.",
        )
    }

    /**
     * Primary Stage-2 corpus byte-anchor. Reproduces all 5 TEXT_TRANSFORM occurrences from
     * `demo_text_transform.rc` byte-for-byte. Burns 14 region-0 ids before the first emit (so
     * `nextId() == 57` = corpus op-#0 textId), then 2 ids between each subsequent emit (so the
     * +3 gap pattern matches).
     */
    @Test
    fun stage2_textTransform_demoTextTransform_all5_subSpans_matchCorpus() {
        val produced = document(
            width = 400, height = 400,
            profile = androidx,
            contentDescription = "tt",
        ) {
            // Burn 15 region-0 ids (42..56) — corpus pre-state has 15 region-0 allocations before
            // the first TEXT_TRANSFORM emit. The next nextId() returns 57. (Map-form
            // PROFILE_ANDROIDX does not reserve id=42 — REM-146 fix.)
            repeat(15) { ids.nextId() }
            val outIds = mutableListOf<Int>()
            for ((idx, operation) in listOf(2, 1, 3, 4, 5).withIndex()) {
                if (idx > 0) repeat(2) { ids.nextId() } // 2-id gap between emits (corpus pattern)
                val id = textTransform(
                    srcId1 = 55,
                    start = 0.0f,
                    len = -1.0f,
                    operation = operation,
                )
                outIds += id
            }
            assertEquals(listOf(57, 60, 63, 66, 69), outIds, "allocator drift — burn pattern misaligned with corpus")
        }
        val producedSpans = extractTextTransformSubSpans(produced)
        assertEquals(5, producedSpans.size)

        val corpus = RcCorpus.readFixture("corpus/demo_text_transform.rc")
        val corpusSpans = extractTextTransformSubSpans(corpus)
        assertEquals(5, corpusSpans.size)

        for (i in 0 until 5) {
            assertContentEquals(
                corpusSpans[i], producedSpans[i],
                "TEXT_TRANSFORM #$i (21B) sub-span byte-mismatch — corpus vs DSL output diverged.",
            )
        }
    }

    /**
     * Non-vacuity pin (REM-147 trap-lesson). The 5 corpus TEXT_TRANSFORM sub-spans must (a) all be
     * 21B, (b) all start with opcode 0xC7, and (c) differ pairwise on at least one field-byte
     * (positions 1..20). The textId axis alone (5 distinct values) gives pairwise distinctness;
     * the operation axis (1..5, 5 distinct values) gives a second independent axis — proves the
     * primary anchor's byte-equality passes on meaningfully different inputs.
     */
    @Test
    fun stage3_textTransform_corpus_subSpans_pairwise_distinct() {
        val corpusSpans = extractTextTransformSubSpans(
            RcCorpus.readFixture("corpus/demo_text_transform.rc"),
        )
        assertEquals(5, corpusSpans.size)
        corpusSpans.forEachIndexed { idx, span ->
            assertEquals(21, span.size, "sub-span #$idx size != 21B (fixed-width violated)")
            assertEquals(0xC7.toByte(), span[0], "sub-span #$idx opcode != 0xC7 (199)")
        }
        // Pairwise field-byte distinct on positions 1..20.
        for (i in 0 until 5) {
            for (j in i + 1 until 5) {
                val fi = corpusSpans[i].copyOfRange(1, 21)
                val fj = corpusSpans[j].copyOfRange(1, 21)
                assertFalse(
                    fi.contentEquals(fj),
                    "sub-span pair ($i, $j) have identical field bytes — corpus is memcpy-vacuous",
                )
            }
        }
        // Stronger semantic axis: the 5 textIds are all distinct AND the 5 operation values are
        // all distinct (5 distinct integers 1..5 in the corpus). Pin both axes here so a future
        // corpus regeneration would be caught.
        val textIds = corpusSpans.map { beInt(it, 1) }.toSet()
        assertEquals(5, textIds.size, "expected 5 distinct corpus textIds, got: ${textIds.sorted()}")
        val operations = corpusSpans.map { beInt(it, 17) }.toSet()
        assertEquals(setOf(1, 2, 3, 4, 5), operations, "expected operation set {1..5}, got: $operations")
    }

    /**
     * Stage-3 determinism — emit the same inputs 5× and assert all produced docs are byte-equal.
     * Includes a NaN-encoded variable ref in `start` (signaling-NaN payload `0xFF800030`);
     * any boxing or `Number.toFloat()` coercion drift would canonicalise NaN bits and surface as
     * cross-run divergence here.
     */
    @Test
    fun stage3_textTransform_determinism_5x_byteEqual() {
        val nanVarRef = Float.fromBits(0xFF800030.toInt())
        val docs = (1..5).map {
            document(
                width = 400, height = 400, profile = androidx, contentDescription = "tt",
            ) {
                repeat(15) { ids.nextId() }
                textTransform(srcId1 = 55, start = nanVarRef, len = -1.0f, operation = 2)
            }
        }
        for (i in 1..4) {
            assertContentEquals(
                docs[0], docs[i],
                "doc #$i differs from doc #0 — textTransform non-deterministic",
            )
        }
    }

    /**
     * W14 NaN-raw-bits-preservation pin. A NaN-encoded variable ref for `start`
     * (`Float.fromBits(0xFF800030.toInt())`) must serialize to its EXACT 4-byte BE pattern
     * `FF 80 00 30` at the on-wire `start` slot (bytes 9..12 of the sub-span). A
     * `Number.toFloat()` intermediate would canonicalise the bits to a quiet-NaN.
     */
    @Test
    fun textTransform_W14_floatParams_preserve_nanRawBits() {
        val nanStart = Float.fromBits(0xFF800030.toInt())
        val nanLen = Float.fromBits(0xFF800031.toInt())
        val produced = document(
            width = 400, height = 400, profile = androidx, contentDescription = "tt",
        ) {
            textTransform(srcId1 = 55, start = nanStart, len = nanLen, operation = 2)
        }
        val spans = extractTextTransformSubSpans(produced)
        assertEquals(1, spans.size)
        // 21B layout: opcode(1) + textId(4) + srcId1(4) + start(4) + len(4) + operation(4)
        //   start = bytes 9..12
        //   len   = bytes 13..16
        val startBytes = spans[0].copyOfRange(9, 13)
        val lenBytes = spans[0].copyOfRange(13, 17)
        assertContentEquals(
            byteArrayOf(0xFF.toByte(), 0x80.toByte(), 0x00, 0x30), startBytes,
            "start NaN raw-bits not preserved — wire-write coerced the bits",
        )
        assertContentEquals(
            byteArrayOf(0xFF.toByte(), 0x80.toByte(), 0x00, 0x31), lenBytes,
            "len NaN raw-bits not preserved — wire-write coerced the bits",
        )
    }

    /** Allocator pin — `textTransform` consumes exactly one region-0 id and returns it. */
    @Test
    fun textTransform_returns_allocated_textId() {
        document(width = 400, height = 400, profile = androidx, contentDescription = "tt") {
            repeat(15) { ids.nextId() } // burn 42..56 (map-form: no id=42 reservation)
            val id1 = textTransform(srcId1 = 55, start = 0.0f, len = -1.0f, operation = 2)
            assertEquals(57, id1, "first textId after burn-15 must be 57")
            val id2 = textTransform(srcId1 = id1, start = 0.0f, len = -1.0f, operation = 3)
            assertEquals(58, id2, "consecutive textTransform must advance the allocator by 1")
            assertTrue(id2 == id1 + 1)
        }
    }

    private fun extractTextTransformSubSpans(docBytes: ByteArray): List<ByteArray> {
        val spans = DocumentReader.inflateWithTrace(docBytes).second
        return spans
            .filter { it.opcode == Operations.TEXT_TRANSFORM }
            .map { docBytes.copyOfRange(it.byteStart, it.byteEnd) }
    }

    private fun beInt(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or
            ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or
            (b[off + 3].toInt() and 0xFF)
}
