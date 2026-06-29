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
 * REM-148 S1 — Stage-2 corpus byte-anchor tests for the procedural `PATH_TWEEN` surface
 * (`RemoteComposeContext.pathTween(pathId1, pathId2, tween)`).
 *
 * **Anchor strategy.** The PATH_TWEEN op is **17 bytes fixed-width BE**:
 *   `[opcode 158 (1B)] + [outId 4B] + [pathId1 4B] + [pathId2 4B] + [tween 4B]`
 * — no varint, no table, no length prefix (assist §S1-§2-pre-review). Sub-spans are extracted by
 * opcode position; ID-decoupling at the op level (the 17B contain only op-owned refs, no path
 * geometry — that lives in the 3× `DATA_PATH` ops emitted upstream).
 *
 * **Corpus sources.** All 4 PATH_TWEEN occurrences from the 173-fixture corpus:
 *   - `path_demo_path_tween_demo.rc` op #22 — outId=52, pathId1=49, pathId2=50, tween=NaN(0xff80002b)
 *   - `path_demo_path_tween_demo.rc` op #23 — outId=53, pathId1=52, pathId2=51, tween=NaN(0xff80002c)
 *   - `path_demo_path2.rc`           op #32 — outId=58, pathId1=55, pathId2=56, tween=NaN(0xff80002b)
 *   - `path_demo_path2.rc`           op #33 — outId=59, pathId1=58, pathId2=57, tween=NaN(0xff80002c)
 *
 * Both corpus tweens encode NaN-encoded `ANIMATED_FLOAT` var-refs (id=43 → `0xff80002b`, id=44 →
 * `0xff80002c`) — **W14 NaN-raw-bits-pin**: the procedural helper takes `Float` (not `Number`) so
 * `WireBuffer.writeFloat`'s `toRawBits()` path preserves the payload verbatim. A `Number.toFloat()`
 * intermediate would coerce signaling-NaN payload to quiet-NaN canonical form.
 *
 * **Non-vacuity (assist §S1-pre-review #3, REM-147 vacuous-trap-lesson).** All 4 sub-spans differ
 * in field values (different outId / pathId1 / pathId2 / tween var-ref); the cross-occurrence
 * assertions prove fixed-17B-over-different-values, not memcpy.
 */
class PathTweenByteTest {

    // The two NaN-encoded tween bits — corpus refers to ANIMATED_FLOAT id=43 (0xff80002b = 0xff800000 + 43)
    // and id=44 (0xff80002c). These match `WireTypes.asNan(43)` / `WireTypes.asNan(44)`.
    private val tweenVar43Bits: Int = 0xFF80002B.toInt()
    private val tweenVar44Bits: Int = 0xFF80002C.toInt()
    private val tweenVar43: Float = Float.fromBits(tweenVar43Bits)
    private val tweenVar44: Float = Float.fromBits(tweenVar44Bits)

    /**
     * Reproduces both PATH_TWEEN occurrences from `path_demo_path_tween_demo.rc` (op#22 + op#23).
     * Burns 9 region-0 ids (43..51) before the first emit so outId=52 / 53 align with corpus.
     */
    @Test
    fun stage2_pathTween_pathTweenDemo_subSpans_matchCorpus() {
        val produced = produceBothPathTweens(burnIds = 9, p1First = 49, p2First = 50, p2Second = 51)
        val producedSpans = extractPathTweenSubSpans(produced)
        assertEquals(2, producedSpans.size, "expected exactly 2 PATH_TWEEN sub-spans")

        val corpus = RcCorpus.readFixture("corpus/path_demo_path_tween_demo.rc")
        val corpusSpans = extractPathTweenSubSpans(corpus)
        assertEquals(2, corpusSpans.size, "corpus expected exactly 2 PATH_TWEEN sub-spans")

        assertContentEquals(corpusSpans[0], producedSpans[0], "PATH_TWEEN #1 (op#22) sub-span byte-mismatch")
        assertContentEquals(corpusSpans[1], producedSpans[1], "PATH_TWEEN #2 (op#23) sub-span byte-mismatch")

        assertEquals(17, producedSpans[0].size)
        assertEquals(17, producedSpans[1].size)
    }

    /**
     * Reproduces both PATH_TWEEN occurrences from `path_demo_path2.rc` (op#32 + op#33). Burns 15
     * region-0 ids (43..57) so outId=58 / 59 align with corpus. Backup anchor proving the helper
     * tracks corpus pre-state correctly for a different fixture.
     */
    @Test
    fun stage2_pathTween_path2_subSpans_matchCorpus() {
        val produced = produceBothPathTweens(burnIds = 15, p1First = 55, p2First = 56, p2Second = 57)
        val producedSpans = extractPathTweenSubSpans(produced)
        assertEquals(2, producedSpans.size)

        val corpus = RcCorpus.readFixture("corpus/path_demo_path2.rc")
        val corpusSpans = extractPathTweenSubSpans(corpus)
        assertEquals(2, corpusSpans.size)

        assertContentEquals(corpusSpans[0], producedSpans[0], "PATH_TWEEN #1 (op#32) sub-span byte-mismatch")
        assertContentEquals(corpusSpans[1], producedSpans[1], "PATH_TWEEN #2 (op#33) sub-span byte-mismatch")
    }

    /**
     * Non-vacuity pin (assist §S1-pre-review #3, REM-147 vacuous-trap-lesson). All 4 corpus
     * PATH_TWEEN sub-spans must (a) all be 17B (size-invariant) and (b) differ pairwise in their
     * field-bytes (positions 1..16) — proving the byte-equality assertions above pass on
     * MEANINGFULLY DIFFERENT inputs, not on a memcpy/zero-fill happy-path.
     */
    @Test
    fun stage3_pathTween_all4_corpus_subSpans_differ_in_field_values() {
        val all4 = listOf(
            extractPathTweenSubSpans(RcCorpus.readFixture("corpus/path_demo_path_tween_demo.rc")),
            extractPathTweenSubSpans(RcCorpus.readFixture("corpus/path_demo_path2.rc")),
        ).flatten()
        assertEquals(4, all4.size, "expected 4 PATH_TWEEN sub-spans across the 2 path-tween fixtures")
        all4.forEachIndexed { idx, span ->
            assertEquals(17, span.size, "sub-span #$idx size != 17B (fixed-width violated)")
            assertEquals(0x9E.toByte(), span[0], "sub-span #$idx opcode byte != 0x9E (158)")
        }
        // Pairwise distinctness on the FIELD bytes (positions 1..16) — opcode byte is intentionally
        // shared. Any two equal sub-spans would mean the corpus baked a memcpy clone, weakening
        // the non-vacuity guarantee of the corpus-byte-anchor.
        for (i in 0 until 4) {
            for (j in i + 1 until 4) {
                val fi = all4[i].copyOfRange(1, 17)
                val fj = all4[j].copyOfRange(1, 17)
                assertFalse(
                    fi.contentEquals(fj),
                    "sub-span pair ($i, $j) have identical field bytes — corpus is memcpy-vacuous",
                )
            }
        }
    }

    /**
     * Stage-3 determinism — emit the same inputs 5× and assert all produced docs are byte-equal.
     * The PATH_TWEEN op carries a NaN-encoded tween var-ref (signaling-NaN payload `0xff80002b`);
     * a value-class boxing or `Float.toFloat()` round-trip that coerces NaN bits would surface as
     * cross-run drift. (Non-vacuity is bracketed by the cross-field-difference test above.)
     */
    @Test
    fun stage3_pathTween_determinism_5x_byteEqual() {
        val docs = (1..5).map {
            document(
                width = 300, height = 300,
                profile = Profile.Baseline,
                contentDescription = "graph",
            ) {
                repeat(9) { ids.nextId() }
                pathTween(pathId1 = 49, pathId2 = 50, tween = tweenVar43)
                pathTween(pathId1 = 52, pathId2 = 51, tween = tweenVar44)
            }
        }
        for (i in 1..4) {
            assertContentEquals(docs[0], docs[i], "doc #$i differs from doc #0 — pathTween non-deterministic")
        }
    }

    /** Allocator pin — `pathTween` must consume exactly one region-0 id and return it. */
    @Test
    fun pathTween_returns_allocated_outId() {
        document(width = 300, height = 300, profile = Profile.Baseline, contentDescription = "graph") {
            // Flat-form reserves id=42 for content-description. First user allocation = 43.
            repeat(9) { ids.nextId() } // burn 43..51
            val outId = pathTween(pathId1 = 49, pathId2 = 50, tween = tweenVar43)
            assertEquals(52, outId, "outId mismatch — id-allocator drift")
            val outId2 = pathTween(pathId1 = outId, pathId2 = 51, tween = tweenVar44)
            assertEquals(53, outId2)
            // Sanity: the burn count is deterministic across runs.
            assertTrue(outId2 == outId + 1)
        }
    }

    private fun produceBothPathTweens(
        burnIds: Int,
        p1First: Int,
        p2First: Int,
        p2Second: Int,
    ): ByteArray = document(
        width = 300, height = 300,
        profile = Profile.Baseline,
        contentDescription = "graph",
    ) {
        repeat(burnIds) { ids.nextId() }
        val first = pathTween(pathId1 = p1First, pathId2 = p2First, tween = tweenVar43)
        pathTween(pathId1 = first, pathId2 = p2Second, tween = tweenVar44)
    }

    private fun extractPathTweenSubSpans(docBytes: ByteArray): List<ByteArray> {
        val spans = DocumentReader.inflateWithTrace(docBytes).second
        return spans
            .filter { it.opcode == Operations.PATH_TWEEN }
            .map { docBytes.copyOfRange(it.byteStart, it.byteEnd) }
    }
}
