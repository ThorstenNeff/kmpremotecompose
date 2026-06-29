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

import com.tneff.kmpremotecompose.conformance.RcCorpus
import com.tneff.kmpremotecompose.remote.creation.ROOT_ALIGNMENT_CENTER
import com.tneff.kmpremotecompose.remote.creation.ROOT_SCALE_FIT
import com.tneff.kmpremotecompose.remote.creation.ROOT_SCROLL_NONE
import com.tneff.kmpremotecompose.remote.creation.ROOT_SIZING_SCALE
import com.tneff.kmpremotecompose.remote.creation.document
import com.tneff.kmpremotecompose.remote.creation.drawOval
import com.tneff.kmpremotecompose.remote.creation.setRootContentBehavior
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * REM-128 §3 — the acceptance anchor for the Compose-creation surface (Triple-Pin, mirror
 * REM-119/126 form). Three sources of bytes for the same input, each comparison pins a different
 * failure mode:
 *
 *   1. **Stage 1 — Compose-DSL == procedural-DSL.** Cross-surface equality isolates applier /
 *      render-walk-order divergence (W2). If the Compose path emits the same ops in a different
 *      order than the procedural path, bytes diverge here.
 *   2. **Stage 2 — Compose-DSL == corpus oracle (THE gate).** Source-of-truth equality vs the
 *      upstream-produced `procedure_simple2.rc`. This is the non-vacuous pin — the analogue of
 *      REM-126 §3, with the Compose-applier as the new path being proven. If this is green, the
 *      Compose surface produces byte-true `.rc`.
 *   3. **Stage 3 — capture determinism.** Two captures of the same content → byte-identical
 *      (W3 / `nextId()` per-document determinism + render-walk-order stability).
 *
 * Body is the exact `simple2_bytesMatchOracle` composition from
 * `CreationByteConformanceTest.kt:103-119`, expressed through both paths so cross-surface equality
 * is meaningful (TechSpec §5 lock: reuse the known-good DSL body, no new repro).
 */
class ComposeCreationTripleAnchorTest {

    /** The procedural reference build — identical to `CreationByteConformanceTest.simple2_bytesMatchOracle`. */
    private fun buildProceduralSimple2(): ByteArray = document(
        width = 300,
        height = 300,
        contentDescription = "Clock",
    ) {
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

    /** The Compose-DSL build — same shape, expressed through `RemoteRootContentBehavior` + `RemoteCanvas`. */
    private suspend fun buildComposeSimple2(): ByteArray = captureSingleRemoteDocument(
        width = 300,
        height = 300,
        contentDescription = "Clock",
    ) {
        RemoteRootContentBehavior(
            scroll = ROOT_SCROLL_NONE,
            alignment = ROOT_ALIGNMENT_CENTER,
            sizing = ROOT_SIZING_SCALE,
            mode = ROOT_SCALE_FIT,
        )
        RemoteCanvas {
            drawOval(
                left = 0f,
                top = 0f,
                right = WireTypes.asNan(RemoteContext.ID_WINDOW_WIDTH),
                bottom = WireTypes.asNan(RemoteContext.ID_WINDOW_HEIGHT),
            )
        }
    }

    // ---- Stage 1: cross-surface (Compose-DSL == procedural-DSL) ----

    /**
     * Same input, two surfaces, byte-equal bytes. Pins W2: render-walk order matches procedural
     * emission order. Self-referential against the procedural reference; not the source-of-truth
     * pin (Stage 2 owns that).
     */
    @Test
    fun stage1_composeOutput_equalsProceduralOutput() = runBlocking {
        val procedural = buildProceduralSimple2()
        val compose = buildComposeSimple2()
        assertContentEquals(
            procedural,
            compose,
            "Compose-DSL output must byte-equal procedural-DSL output for the same simple2 body — proves applier render-walk order matches procedural emission order (W2)",
        )
    }

    // ---- 🔑 Stage 2: source-of-truth (Compose-DSL == corpus oracle) ----

    /**
     * **The REM-128 §0 acceptance gate (TechSpec §3, non-vacuous).** Compose-DSL output equals the
     * upstream-produced `procedure_simple2.rc` byte-for-byte. The applier + render-walk + procedural
     * shell shortcut all combine to produce bytes from the byte-proven path; this is the proof.
     */
    @Test
    fun stage2_composeOutput_matchesProcedureSimple2Oracle() = runBlocking {
        val compose = buildComposeSimple2()
        val oracle = RcCorpus.readFixture("corpus/procedure_simple2.rc")
        assertContentEquals(
            oracle,
            compose,
            "Compose-DSL output must byte-match procedure_simple2 oracle — proves the applier path produces byte-true .rc via the byte-proven procedural surface",
        )
    }

    // ---- Stage 3: capture determinism ----

    /**
     * Two captures of the same content → byte-identical. Pins W3 / `nextId()` determinism + render-
     * walk-order stability between runs. Self-referential — supporting evidence, not the gate.
     */
    @Test
    fun stage3_capture_isDeterministicAcrossRuns() = runBlocking {
        val first = buildComposeSimple2()
        val second = buildComposeSimple2()
        assertContentEquals(
            first,
            second,
            "Two captures of the same content must be byte-identical — proves ids.nextId() determinism + render-walk-order stability across runs",
        )
    }
}
