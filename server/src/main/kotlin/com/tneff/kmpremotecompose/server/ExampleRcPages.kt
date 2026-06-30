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

import com.tneff.kmpremotecompose.remote.creation.document
import com.tneff.kmpremotecompose.remote.creation.drawCircle

/** Stable classloader anchor for reading the bundled corpus resources. */
private object ExampleRcResources

/**
 * REM-173 — the example local server's page **allowlist**.
 *
 * **ID contract with dev-1's example-app catalog (TechSpec):** the first 10 `pageId`s are the Area-B
 * catalog ids **verbatim, in list order** (`rc_box … rc_compass`), each served as the **bundled lib-corpus
 * `.rc` bytes** (the same `.rc` dev-1 copied, served verbatim — `BundledRc`). So the app's server-toggle
 * `ServerPage(rc_*)` maps 1:1 to a bundled entry. After them, a few procedural `document{}` extras
 * demonstrate the byte-true procedural path.
 *
 * Scope (PO): procedural + corpus only — **no Compose-DSL docs** (Area-A authoring is built in-app, so
 * `:server` stays free of a `:creation-compose` dependency). `pageId` is a registry KEY, never an FS path.
 */
fun defaultExampleRegistry(): RcPageRegistry = RcPageRegistry().apply {
    // --- Area-B catalog (id == pageId, order == dev-1's catalog list) → bundled lib-corpus .rc verbatim ---
    register("rc_box") { corpusRc("c_box") }
    register("rc_text") { corpusRc("text_baseline") }
    register("rc_gradient") { corpusRc("procedure_gradient1") }
    register("rc_moon") { corpusRc("moon_phases") }
    register("rc_clock") { corpusRc("clock_demo1_clock1") }
    register("rc_confetti") { corpusRc("impulse_demo_confetti_demo") }
    register("rc_scroll") { corpusRc("c_modifier_vertical_scroll") }
    register("rc_click") { corpusRc("c_modifier_on_click") }
    register("rc_pie") { corpusRc("good_pie_chart") }
    register("rc_compass") { corpusRc("sensor_demo_compass") }
    // --- procedural document{} extras (byte-true; demonstrate the procedural serving path) ---
    register("simple2") { buildSimple2() } // the procedure_simple2 byte-true oracle (REM-126)
    register("oval") { buildOval(300) }
    register("circle") { buildExampleCircle() }
}

/** The Area-B catalog pageIds in contract order (id == dev-1's catalog id), for callers/tests. */
val AREA_B_PAGE_IDS: List<String> = listOf(
    "rc_box", "rc_text", "rc_gradient", "rc_moon", "rc_clock",
    "rc_confetti", "rc_scroll", "rc_click", "rc_pie", "rc_compass",
)

/** A simple byte-true procedural example: a centered filled circle in a 400×400 doc. */
internal fun buildExampleCircle(): ByteArray = document(width = 400, height = 400) {
    drawCircle(centerX = 200, centerY = 200, radius = 160)
}

/**
 * Serve a curated corpus `.rc` bundled at `:server` resource `/rc/{name}.rc`. [name] is **fixed per
 * registration** (never request-derived) → no path traversal; the `pageId` stays an allowlist key. The
 * bytes are served **verbatim** (the same lib-corpus `.rc`).
 */
internal fun corpusRc(name: String): ByteArray {
    val path = "/rc/$name.rc"
    val stream = ExampleRcResources::class.java.getResourceAsStream(path)
        ?: error("missing bundled corpus rc: $path")
    return stream.use { it.readBytes() }
}
