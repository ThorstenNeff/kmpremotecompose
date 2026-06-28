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

import com.tneff.kmpremotecompose.remote.creation.ROOT_ALIGNMENT_CENTER
import com.tneff.kmpremotecompose.remote.creation.ROOT_SCALE_FIT
import com.tneff.kmpremotecompose.remote.creation.ROOT_SCROLL_NONE
import com.tneff.kmpremotecompose.remote.creation.ROOT_SIZING_SCALE
import com.tneff.kmpremotecompose.remote.creation.document
import com.tneff.kmpremotecompose.remote.creation.drawOval
import com.tneff.kmpremotecompose.remote.creation.setRootContentBehavior
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * E5 — Creation-Byte-Conformance (REM-84, test-2). The §2-WRITE-side gate: a `.rc` produced by our
 * KMP DSL must be **byte-identical** to the upstream-procedural oracle (`procedure_*` corpus fixtures).
 * This is the canonical E5 conformance gate (dev-3 also smoke-tests fixtures as they build the ops;
 * this suite is the holistic byte-equality tracker test-2 owns).
 *
 * Three-stage strategy (TechSpec-REM-E §4): (1) round-trip self-consistency, (2) decode-and-inspect,
 * (3) byte-equality vs oracle. **Green now:** stage 1 (round-trip/determinism), the stage-3 prolog
 * checkpoint (REM-85), and `procedure_simple2` full byte-equality (REM-86 E2 draw/path + RCB surface).
 * The four richer fixtures stay `@Ignore`d until E3–E4 deliver text/gradient/color-expr/ID_MAP ops;
 * un-ignore each as its ops land. Replication target: `docs/e5-creation-byte-conformance-prep.md`
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

    // ---- Stage 3: byte-equality vs the oracle fixtures ----
    // Targets (op-sequence + id-order) documented in docs/e5-creation-byte-conformance-prep.md §2.
    // Each asserts: document(300,300,contentDescription="Clock"){ <replicated ops> } == oracle bytes.

    /**
     * E2 milestone (REM-86, ACTIVE/green): full byte-equality vs procedure_simple2 (82 B).
     * Body = setRootContentBehavior(NONE, CENTER, SCALE, SCALE_FIT) + drawOval(0,0,WIN_W,WIN_H), where
     * the right/bottom coords are the region-0 system-variable ids 5/6 (ID_WINDOW_WIDTH/HEIGHT),
     * NaN-boxed into the float slots (decoded raw: 0xff800005 / 0xff800006 — see prep §3a). Exercises
     * the E2 draw + RCB surface on top of REM-85's prolog.
     */
    @Test
    fun simple2_bytesMatchOracle() {
        val produced = document(width = 300, height = 300, contentDescription = "Clock") {
            setRootContentBehavior(
                scroll = ROOT_SCROLL_NONE,
                alignment = ROOT_ALIGNMENT_CENTER,
                sizing = ROOT_SIZING_SCALE,
                mode = ROOT_SCALE_FIT,
            )
            drawOval(
                left = 0f,
                top = 0f,
                right = WireTypes.asNan(RemoteContext.ID_WINDOW_WIDTH),
                bottom = WireTypes.asNan(RemoteContext.ID_WINDOW_HEIGHT),
            )
        }
        assertContentEquals(oracle("procedure_simple2"), produced, "DSL must byte-match procedure_simple2 oracle")
    }

    @Ignore // blocked on E3 (DATA_TEXT body "gradient" + gradient paint/shader + DRAW_TEXT_ANCHOR).
    @Test
    fun gradient1_bytesMatchOracle() {
        // Target: HEADER(v1.0.0 flat) + DATA_TEXT(42 "Clock") + ROOT_CONTENT_DESCRIPTION(42) +
        // ROOT_CONTENT_BEHAVIOR(0,34,2,6) + PAINT_VALUES(12) + 3×ANIMATED_FLOAT(43,44,45) +
        // MATRIX_SAVE + MATRIX_SCALE + DRAW_OVAL + MATRIX_RESTORE + DATA_TEXT(46 "gradient") + DRAW_TEXT_ANCHOR(46).
        val produced = document(width = 300, height = 300, contentDescription = "Clock") { /* E2/E3 ops */ }
        assertContentEquals(oracle("procedure_gradient1"), produced)
    }

    @Ignore // blocked on E3 (TEXT_FROM_FLOAT, TEXT_MEASURE, DRAW_RECT, text body).
    @Test
    fun centerText1_bytesMatchOracle() {
        val produced = document(width = 300, height = 300, contentDescription = "Clock") { /* E3 text ops */ }
        assertContentEquals(oracle("procedure_center_text1"), produced)
    }

    @Ignore // blocked on E3/E4 (text body, ID_MAP@2097194 collection-range, DATA_MAP_LOOKUP, TEXT_MEASURE).
    @Test
    fun lookUp1_bytesMatchOracle() {
        val produced = document(width = 300, height = 300, contentDescription = "Clock") { /* E4 map/lookup ops */ }
        assertContentEquals(oracle("procedure_look_up1"), produced)
    }

    @Ignore // blocked on E3 (COLOR_EXPRESSIONS, DATA_TEXT body, DRAW_TEXT_ANCHOR); DATA_PATH/DRAW_PATH are E2.
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
