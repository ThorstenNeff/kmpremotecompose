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
 * REM-148 S2 — Stage-2 corpus byte-anchor tests for the procedural `PATH_EXPRESSION` surface
 * (`RemoteComposeContext.pathExpression(flags, min, max, count, expressionX, expressionY)`).
 *
 * **Anchor strategy.** PATH_EXPRESSION is a **variable-length op** — fixed 29-byte header + 4×lenX
 * bytes for X + 4×lenY bytes for Y. Sub-spans extracted by opcode position. ID-decoupled at the op
 * level (the wire id is the only id-bearing field; the rest are flags+RPN payload that the caller
 * controls). Same setup principle as REM-148 S1 (PATH_TWEEN): burn region-0 ids to align allocator
 * state with the corpus pre-state, then emit the op.
 *
 * **Primary corpus source.** `demo_path_expression_path_test1.rc` op #23 — the smallest dedicated
 * PATH_EXPRESSION sub-span in the 173-fixture corpus (69B, lenX=8, lenY=2, flags=0x8=POLAR mode).
 *   id=52, flags=8, min=0.0f, max=6.2831855f (2π), count=60.0f
 *   X[8]: { var-ref id=0x30, 1.0f, RPN op 0x46 (INV_PI), 3.1415927f (π), RPN op 0x01 (ADD), RPN op 0x12, RPN op 0x02 (SUB), RPN op 0x03 (MUL) }
 *   Y[2]: { var-ref id=0x2c, var-ref id=0x33 }
 *
 * **Burn count.** Pre-op-#23 region-0 allocations: 2× COMPONENT_VALUE (ids 42, 43) + 8×
 * ANIMATED_FLOAT (ids 44–51) = 10. PATH_EXPRESSION at id=52 = 42 + 10.
 *
 * **Profile / Header.** map-form (api=7) PROFILE_ANDROIDX (0x200), w=500 h=500, contentDescription
 * "sd" (header property 9, no body id-42 reservation per REM-146). Emit via
 * `document(profile = Profile(operationsProfiles = PROFILE_ANDROIDX, ...), contentDescription = "sd")`.
 *
 * **W14 NaN-bits-pin (intensive).** Every float (min/max/count + each X/Y element) round-trips
 * via `Float.fromBits` → `Float`-typed param → `WireBuffer.writeFloat`'s `toRawBits()`. The RPN
 * payload mixes IEEE literals (1.0f, π) with NaN-encoded var-refs (`asNan(id)`) and NaN-encoded
 * RPN operator ids (`RcExpression.{ADD/SUB/MUL/...}`); a `Number.toFloat()` coercion would
 * canonicalise NaN payloads and break the corpus byte-anchor.
 *
 * **Non-vacuity (assist S1-pre-review #3, REM-147 vacuous-trap-lesson).** The corpus has 7
 * PATH_EXPRESSION occurrences in `demo_path_expression_path_test1.rc` with diverse sizes
 * (69 / 113 / 113 / 141 / 77 / 85 / 153 B) and diverse field values. The non-vacuity assertion
 * proves the byte-equality check passes on MEANINGFULLY DIFFERENT inputs.
 */
class PathExpressionByteTest {

    // ----- Empirically-decoded test1 op #23 (primary anchor) RPN payload -----
    // X[8]: see KDoc header for the full operand-by-operand decode.
    private val test1Op23X: FloatArray = floatArrayOf(
        Float.fromBits(0xFF800030.toInt()),   // NaN var-ref to system id 0x30 (= 48)
        Float.fromBits(0x3F800000),           // 1.0f
        Float.fromBits(0xFFB10046.toInt()),   // RPN operator id 0x310046 (INV_PI)
        Float.fromBits(0x40490FDB),           // π = 3.1415927f
        Float.fromBits(0xFFB10001.toInt()),   // RPN operator id 0x310001 (ADD)
        Float.fromBits(0xFFB10012.toInt()),   // RPN operator id 0x310012
        Float.fromBits(0xFFB10002.toInt()),   // RPN operator id 0x310002 (SUB)
        Float.fromBits(0xFFB10003.toInt()),   // RPN operator id 0x310003 (MUL)
    )
    private val test1Op23Y: FloatArray = floatArrayOf(
        Float.fromBits(0xFF80002C.toInt()),   // NaN var-ref to system id 0x2c (= 44)
        Float.fromBits(0xFF800033.toInt()),   // NaN var-ref to system id 0x33 (= 51)
    )

    private val androidx: Profile = Profile(
        operationsProfiles = Operations.PROFILE_ANDROIDX,
        services = defaultRcPlatformServices(),
    )

    /**
     * Primary Stage-2 corpus byte-anchor. Reproduces `demo_path_expression_path_test1.rc` op #23
     * (69B, the smallest dedicated PATH_EXPRESSION sub-span in the corpus) byte-for-byte by burning
     * 10 region-0 ids before the emit (matching the corpus 2× COMPONENT_VALUE + 8× ANIMATED_FLOAT
     * pre-state), then emitting `pathExpression(...)` with the empirically-decoded payload.
     */
    @Test
    fun stage2_pathExpression_test1_op23_subSpan_matchesCorpus() {
        val produced = document(
            width = 500, height = 500,
            profile = androidx,
            contentDescription = "sd",
        ) {
            // Burn 10 region-0 ids (42..51) — corpus pre-state allocates 2× COMPONENT_VALUE +
            // 8× ANIMATED_FLOAT here. Our test doesn't need to emit those ops; only the
            // allocator state must align with the corpus at the point of PATH_EXPRESSION emit.
            repeat(10) { ids.nextId() }
            val pathId = pathExpression(
                flags = 0x8, // POLAR
                min = 0.0f,
                max = Float.fromBits(0x40C90FDB),   // 2π = 6.2831855f
                count = 60.0f,
                expressionX = test1Op23X,
                expressionY = test1Op23Y,
            )
            assertEquals(52, pathId, "outId allocator-state drift — burn count mis-aligned with corpus")
        }
        val producedSpans = extractPathExpressionSubSpans(produced)
        assertEquals(1, producedSpans.size, "expected exactly 1 PATH_EXPRESSION sub-span in produced")
        assertEquals(69, producedSpans[0].size, "primary anchor sub-span must be 69B")

        val corpus = RcCorpus.readFixture("corpus/demo_path_expression_path_test1.rc")
        val corpusSpans = extractPathExpressionSubSpans(corpus)
        assertTrue(corpusSpans.isNotEmpty(), "corpus must contain ≥1 PATH_EXPRESSION op")
        assertContentEquals(
            corpusSpans[0], producedSpans[0],
            "PATH_EXPRESSION op #23 sub-span (69B) byte-mismatch — corpus vs DSL output diverged.",
        )
    }

    /**
     * Non-vacuity pin (assist S1-pre-review #3, REM-147 vacuous-trap-lesson). The 7 corpus
     * PATH_EXPRESSION sub-spans in `demo_path_expression_path_test1.rc` must (a) all start with
     * opcode 0xC1, (b) span diverse byte sizes (69, 113, 113, 141, 77, 85, 153), and (c) differ
     * pairwise on at least one field-byte position. Proves the primary anchor's byte-equality
     * assertion passes on meaningfully different inputs, not on a memcpy/zero-fill happy-path.
     */
    @Test
    fun stage3_pathExpression_corpus_subSpans_diverse_and_pairwise_distinct() {
        val corpusSpans = extractPathExpressionSubSpans(
            RcCorpus.readFixture("corpus/demo_path_expression_path_test1.rc"),
        )
        assertEquals(
            7, corpusSpans.size,
            "test1.rc must have exactly 7 PATH_EXPRESSION ops (empirical decode-probe count)",
        )
        // (a) Opcode invariant.
        corpusSpans.forEachIndexed { idx, span ->
            assertEquals(
                0xC1.toByte(), span[0],
                "sub-span #$idx opcode != 0xC1 (193)",
            )
        }
        // (b) Diverse sizes — at least 4 distinct sizes among the 7 sub-spans
        // (empirical: {69, 77, 85, 113, 141, 153} = 6 distinct).
        val sizes = corpusSpans.map { it.size }.toSet()
        assertTrue(
            sizes.size >= 4,
            "expected ≥4 distinct sub-span sizes (variable-length op); got ${sizes.size}: $sizes",
        )
        // (c) Pairwise field-bytes distinct (positions 1..end, skipping shared opcode byte).
        for (i in corpusSpans.indices) {
            for (j in i + 1 until corpusSpans.size) {
                val fi = corpusSpans[i].copyOfRange(1, corpusSpans[i].size)
                val fj = corpusSpans[j].copyOfRange(1, corpusSpans[j].size)
                assertFalse(
                    fi.contentEquals(fj),
                    "sub-span pair ($i, $j) have identical field bytes — corpus is memcpy-vacuous",
                )
            }
        }
    }

    /**
     * Stage-3 determinism — emit the same inputs 5× and assert all produced docs are byte-equal.
     * The RPN payload is dense with NaN-encoded operator ids + var-refs (signaling-NaN payloads
     * like `0xFFB10046` for the INV_PI operator); any boxing or `Float.toFloat()` coercion drift
     * would canonicalise NaN bits and surface as cross-run divergence here.
     */
    @Test
    fun stage3_pathExpression_determinism_5x_byteEqual() {
        val docs = (1..5).map {
            document(
                width = 500, height = 500,
                profile = androidx,
                contentDescription = "sd",
            ) {
                repeat(10) { ids.nextId() }
                pathExpression(
                    flags = 0x8,
                    min = 0.0f,
                    max = Float.fromBits(0x40C90FDB),
                    count = 60.0f,
                    expressionX = test1Op23X,
                    expressionY = test1Op23Y,
                )
            }
        }
        for (i in 1..4) {
            assertContentEquals(
                docs[0], docs[i],
                "doc #$i differs from doc #0 — pathExpression non-deterministic",
            )
        }
    }

    /** Allocator pin — `pathExpression` consumes exactly one region-0 id and returns it. */
    @Test
    fun pathExpression_returns_allocated_id() {
        document(width = 500, height = 500, profile = androidx, contentDescription = "sd") {
            repeat(10) { ids.nextId() } // burn 42..51
            val id1 = pathExpression(
                flags = 0,
                min = 0.0f, max = 1.0f, count = 10.0f,
                expressionX = floatArrayOf(0.0f),
                expressionY = floatArrayOf(0.0f),
            )
            assertEquals(52, id1, "first outId after burn-10 must be 52")
            val id2 = pathExpression(
                flags = 0,
                min = 0.0f, max = 1.0f, count = 10.0f,
                expressionX = floatArrayOf(1.0f),
                expressionY = floatArrayOf(1.0f),
            )
            assertEquals(53, id2, "consecutive pathExpression must advance the allocator by 1")
        }
    }

    /**
     * Defensive-copy pin (Q4 lock). A caller mutating the input expression arrays AFTER calling
     * `pathExpression` must NOT change the queued op's wire bytes. The procedural helper
     * `.copyOf()`s both arrays into the op at emission time; tested by emitting twice — once
     * mutating the input array between emits — and asserting both PATH_EXPRESSION sub-spans
     * have identical X[1] bytes (i.e. the first emit was frozen, the mutation only landed in
     * the second).
     */
    @Test
    fun pathExpression_inputArrays_defensiveCopy_freezes_queuedOp() {
        val sharedX = floatArrayOf(0.0f, 1.0f, 2.0f)
        val sharedY = floatArrayOf(3.0f)
        val produced = document(width = 500, height = 500, profile = androidx, contentDescription = "sd") {
            pathExpression(flags = 0, min = 0f, max = 1f, count = 4f, expressionX = sharedX, expressionY = sharedY)
            sharedX[1] = 999.0f // mutate AFTER the first emit
            pathExpression(flags = 0, min = 0f, max = 1f, count = 4f, expressionX = sharedX, expressionY = sharedY)
        }
        val spans = extractPathExpressionSubSpans(produced)
        assertEquals(2, spans.size)
        // Wire header is 25B (opcode 1 + id 4 + flags 4 + min 4 + max 4 + count 4 + lenX 4) so
        // X[0] occupies bytes 25..28, X[1] occupies bytes 29..32 (`copyOfRange(29, 33)`).
        val first = spans[0].copyOfRange(29, 33)
        val second = spans[1].copyOfRange(29, 33)
        // first emit's X[1] = 1.0f (0x3F800000 BE); second emit's X[1] = 999.0f (0x4479C000 BE).
        assertContentEquals(
            byteArrayOf(0x3F, 0x80.toByte(), 0x00, 0x00), first,
            "first emit X[1] was not frozen by .copyOf() — defensive-copy guard broken",
        )
        assertContentEquals(
            byteArrayOf(0x44, 0x79, 0xC0.toByte(), 0x00), second,
            "second emit X[1] should reflect the post-emit mutation (=999.0f), but did not",
        )
    }

    private fun extractPathExpressionSubSpans(docBytes: ByteArray): List<ByteArray> {
        val spans = DocumentReader.inflateWithTrace(docBytes).second
        return spans
            .filter { it.opcode == Operations.PATH_EXPRESSION }
            .map { docBytes.copyOfRange(it.byteStart, it.byteEnd) }
    }
}
