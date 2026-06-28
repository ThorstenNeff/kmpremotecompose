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
package com.tneff.kmpremotecompose.conformance

import com.tneff.kmpremotecompose.remote.creation.document
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * E5 — Creation-Byte-Conformance (REM-84, test-2). The §2-WRITE-side gate: a `.rc` produced by our
 * KMP DSL must be **byte-identical** to the upstream-procedural oracle (`procedure_*` corpus fixtures).
 *
 * Three-stage strategy (TechSpec-REM-E §4): (1) round-trip self-consistency, (2) decode-and-inspect,
 * (3) byte-equality vs oracle. Active now: stage 1 (round-trip/determinism) **and** the stage-3 prolog
 * checkpoint (`e1Prolog_byteMatchesOracleHeaderBlock`) — green since REM-85 made `document{}` byte-faithful.
 * The four full-fixture targets stay `@Ignore`d until E2–E4 deliver their body ops (draw/text/path/map);
 * un-ignore each as its ops land. Op-sequence + id-order replication target: `docs/e5-creation-byte-conformance-prep.md`
 * + the canonical `docs/TECHSPEC-E5-id-order-reference.md`.
 */
class CreationByteConformanceTest {

    private fun oracle(name: String): ByteArray = RcCorpus.readFixture("corpus/$name.rc")

    // ---- Stage 1: round-trip self-consistency (ACTIVE — proves the harness + E1 encode are consistent) ----

    /** E1 emits a header-only doc; decode→reEncode must be byte-stable (the byte-bewiesene L1 codec). */
    @Test
    fun e1_document_roundTrips_byteStable() {
        val bytes = document(width = 300, height = 300, contentDescription = "Clock") { }
        val reEncoded = RcDocumentCodec.decode(bytes).reEncode()
        assertContentEquals(bytes, reEncoded, "E1 document{} output must round-trip byte-stable through the L1 codec")
    }

    /** Deterministic byte output: the same script must encode identically every call (no global state). */
    @Test
    fun e1_document_isDeterministic() {
        val a = document(width = 300, height = 300, contentDescription = "Clock") { }
        val b = document(width = 300, height = 300, contentDescription = "Clock") { }
        assertContentEquals(a, b, "same DSL script must produce identical bytes (id-allocation per-document)")
    }

    // ---- Stage 3: byte-equality vs the four named oracle fixtures ----
    // Targets (op-sequence + id-order) documented in docs/e5-creation-byte-conformance-prep.md §2.
    // Each asserts: document(300,300,contentDescription="Clock"){ <replicated ops> } == oracle bytes.

    @Ignore // blocked: E1 prolog not byte-faithful yet (flat/map-API §3) + draw/text ops are E2/E3.
    @Test
    fun gradient1_bytesMatchOracle() {
        // Target: HEADER(v1.0.0 flat) + DATA_TEXT(42 "Clock") + ROOT_CONTENT_DESCRIPTION(42) +
        // ROOT_CONTENT_BEHAVIOR(0,34,2,6) + PAINT_VALUES(12) + 3×ANIMATED_FLOAT(43,44,45) +
        // MATRIX_SAVE + MATRIX_SCALE + DRAW_OVAL + MATRIX_RESTORE + DATA_TEXT(46 "gradient") + DRAW_TEXT_ANCHOR(46).
        val produced = document(width = 300, height = 300, contentDescription = "Clock") { /* E2/E3 ops */ }
        assertContentEquals(oracle("procedure_gradient1"), produced)
    }

    @Ignore // blocked on E2/E3 (TEXT_FROM_FLOAT, TEXT_MEASURE, DRAW_RECT) + E1 prolog.
    @Test
    fun centerText1_bytesMatchOracle() {
        val produced = document(width = 300, height = 300, contentDescription = "Clock") { /* E3 text ops */ }
        assertContentEquals(oracle("procedure_center_text1"), produced)
    }

    @Ignore // blocked on E4 (ID_MAP@2097194 collection-range, DATA_MAP_LOOKUP) + E2/E3 + E1 prolog.
    @Test
    fun lookUp1_bytesMatchOracle() {
        val produced = document(width = 300, height = 300, contentDescription = "Clock") { /* E4 map/lookup ops */ }
        assertContentEquals(oracle("procedure_look_up1"), produced)
    }

    @Ignore // blocked on E2/E3 (DATA_PATH 2141-pt, COLOR_EXPRESSIONS, DRAW_PATH) + E1 prolog.
    @Test
    fun textPathEffects1_bytesMatchOracle() {
        val produced = document(width = 300, height = 300, contentDescription = "Clock") { /* E2/E3 path ops */ }
        assertContentEquals(oracle("procedure_text_path_effects"), produced)
    }

    // ---- Stage-3 early checkpoint: E1 prolog byte-faithfulness (the smallest end-to-end byte proof) ----

    /**
     * ACTIVE since REM-85 (byte-faithful prolog, develop fad13e2): an empty `document{}` emits exactly
     * the 48-byte prolog — flat-API v1.0.0 HEADER(29) + DATA_TEXT(id42 "Clock")(14) +
     * ROOT_CONTENT_DESCRIPTION(42)(5), byte-identical to every procedure_* oracle's first 48 bytes.
     * ROOT_CONTENT_BEHAVIOR is intentionally NOT here — it arrives in E2 via setRootContentBehavior.
     * This is the gating E1 byte-checkpoint; the four fixture targets build their bodies on top of it.
     */
    @Test
    fun e1Prolog_byteMatchesOracleHeaderBlock() {
        val produced = document(width = 300, height = 300, contentDescription = "Clock") { }
        val oracleProlog = oracle("procedure_gradient1").copyOfRange(0, 48)
        assertContentEquals(oracleProlog, produced, "empty document{} must equal the 48-byte flat-API prolog")
    }
}
