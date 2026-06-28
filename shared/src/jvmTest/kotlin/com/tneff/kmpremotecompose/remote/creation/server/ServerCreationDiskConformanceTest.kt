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
package com.tneff.kmpremotecompose.remote.creation.server

import com.tneff.kmpremotecompose.conformance.RcCorpus
import com.tneff.kmpremotecompose.conformance.RcDocumentCodec
import com.tneff.kmpremotecompose.remote.creation.ROOT_ALIGNMENT_CENTER
import com.tneff.kmpremotecompose.remote.creation.ROOT_SCALE_FIT
import com.tneff.kmpremotecompose.remote.creation.ROOT_SCROLL_NONE
import com.tneff.kmpremotecompose.remote.creation.ROOT_SIZING_SCALE
import com.tneff.kmpremotecompose.remote.creation.document
import com.tneff.kmpremotecompose.remote.creation.drawOval
import com.tneff.kmpremotecompose.remote.creation.setRootContentBehavior
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import okio.FileSystem
import okio.Path.Companion.toPath
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * REM-126 §0 — Server-Creation disk-path acceptance anchor.
 *
 * **The TechSpec §3 gate (load-bearing decision):** `CreationByteConformanceTest` already proves
 * `document(simple2){} == oracle` for the **in-memory `ByteArray`** — so comparing the in-memory
 * array here would re-prove what's already proven (a *vacuous* pin of the server path). The only
 * thing REM-126 adds on top is the okio disk write→read round-trip. Therefore stage-3 below builds
 * the bytes, **writes them to disk through [RcDiskWriter], reads them back, and compares the
 * read-back bytes against the corpus oracle.** That is the non-vacuous server-side anchor.
 *
 * Stages 1 (2×-run determinism, W3) and 2 (decode→reEncode L1-codec consistency) are kept as
 * supporting evidence per §3 — necessary-not-sufficient. Stage-3 is the gate.
 *
 * Body is the *exact* `simple2_bytesMatchOracle` composition from
 * `CreationByteConformanceTest.kt:103-119` — TechSpec §5 lock: reuse the known-good DSL body, no
 * new repro work.
 */
class ServerCreationDiskConformanceTest {

    private val fs: FileSystem = FileSystem.SYSTEM
    private val tempDir = System.getProperty("java.io.tmpdir").toPath()
    private val tempPaths = mutableListOf<okio.Path>()

    private fun newTempPath(): okio.Path {
        // Unique per-call to keep parallel tests from clobbering each other.
        val path = tempDir / "rem126-${UUID.randomUUID()}.rc"
        tempPaths += path
        return path
    }

    @AfterTest
    fun cleanup() {
        for (p in tempPaths) if (fs.exists(p)) fs.delete(p)
    }

    /** The simple2 DSL body, mirror of `CreationByteConformanceTest.simple2_bytesMatchOracle`. */
    private fun buildSimple2Bytes(): ByteArray = document(
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

    // ---- Stage 1: determinism (self-referential — supporting evidence, NOT the gate) ----

    /**
     * Two independent server-creation runs must produce byte-identical outputs. Pins W3
     * (`ids.nextId()` + TextData pool are per-document deterministic, REM-90). Self-referential —
     * does not prove byte-truth, just that the run is reproducible.
     */
    @Test
    fun stage1_serverCreation_isDeterministicAcrossRuns() {
        val bytes1 = buildSimple2Bytes()
        val bytes2 = buildSimple2Bytes()
        assertContentEquals(
            bytes1,
            bytes2,
            "Server-creation must be deterministic — two independent document() runs of the same body must produce identical bytes",
        )
    }

    // ---- Stage 2: L1-codec round-trip (self-referential — supporting evidence) ----

    /**
     * `decode(produced) → reEncode()` must equal the original produced bytes. Pins L1-codec
     * consistency (reader/writer agree on byte layout). Self-referential — does not prove byte
     * truth against an external oracle.
     */
    @Test
    fun stage2_serverCreation_roundTripsThroughL1Codec() {
        val bytes = buildSimple2Bytes()
        val reEncoded = RcDocumentCodec.decode(bytes).reEncode()
        assertContentEquals(
            bytes,
            reEncoded,
            "Server-creation output must round-trip byte-stable through the L1 codec",
        )
    }

    // ---- 🔑 Stage 3: the gate — disk read-back vs corpus oracle ----

    /**
     * **The REM-126 §0 acceptance gate (TechSpec §3, non-vacuous).** Builds the simple2 bytes,
     * writes them through [RcDiskWriter] to a temp file, **reads them back from that file**, and
     * compares the read-back bytes against the upstream-produced `procedure_simple2.rc` oracle.
     *
     * Critically the in-memory `bytes` value is **discarded** before the assertion — only the disk
     * round-trip is allowed to satisfy the gate. Comparing in-memory bytes would re-prove
     * `CreationByteConformanceTest.simple2_bytesMatchOracle` and pin nothing about the new okio
     * disk surface (TechSpec §3 vacuous-pin warning).
     */
    @Test
    fun stage3_diskReadBack_matchesProcedureSimple2Oracle() {
        val path = newTempPath()
        val bytes = buildSimple2Bytes()
        RcDiskWriter.write(path, bytes, fs)
        // Discard the in-memory array — the gate is what comes back FROM DISK.
        @Suppress("UNUSED_VALUE")
        var produced: ByteArray? = bytes
        produced = null
        val readBack = RcDiskWriter.read(path, fs)
        val oracle = RcCorpus.readFixture("corpus/procedure_simple2.rc")
        assertContentEquals(
            oracle,
            readBack,
            "Disk read-back must byte-match procedure_simple2 oracle — proves the okio write→read surface end-to-end",
        )
    }
}
