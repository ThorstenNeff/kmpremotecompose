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
package com.tneff.kmpremotecompose.server

import com.tneff.kmpremotecompose.remote.creation.ROOT_ALIGNMENT_CENTER
import com.tneff.kmpremotecompose.remote.creation.ROOT_SCALE_FIT
import com.tneff.kmpremotecompose.remote.creation.ROOT_SCROLL_NONE
import com.tneff.kmpremotecompose.remote.creation.ROOT_SIZING_SCALE
import com.tneff.kmpremotecompose.remote.creation.document
import com.tneff.kmpremotecompose.remote.creation.drawOval
import com.tneff.kmpremotecompose.remote.creation.server.RcDiskWriter
import com.tneff.kmpremotecompose.remote.creation.setRootContentBehavior
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import okio.Path.Companion.toPath

/**
 * REM-126 §0 — JVM-headless server-creation runner.
 *
 * Hardcoded MVP per TechSpec §8 S2 / §5.1: one doc (the simple2 replica, profile bound in code →
 * fences W2 form-mismatch), one CLI arg (`args[0] = outPath`). No CLI framework
 * (Picocli/Clikt) and no `--fixture` flag — multi-fixture-by-name would decouple `profile` from
 * the author code and risk byte divergence; deferred per TechSpec §5.1.
 *
 * Body is the exact `simple2_bytesMatchOracle` composition from
 * `CreationByteConformanceTest.kt:103-119` (= the `procedure_simple2` corpus oracle, 82 B).
 */
fun main(args: Array<String>) {
    require(args.isNotEmpty()) {
        "Usage: server <outPath>   — writes the procedure_simple2 replica to <outPath>."
    }
    val bytes = buildSimple2()
    RcDiskWriter.write(args[0].toPath(), bytes)
}

/** Visible-for-test: the exact simple2 DSL body, kept colocated with `main` so the smoke can call it. */
internal fun buildSimple2(): ByteArray = document(
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
