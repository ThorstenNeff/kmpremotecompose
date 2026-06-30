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
package com.tneff.kmpremotecompose

import com.tneff.kmpremotecompose.conformance.IgnoreOnWasm
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.Builtins
import kmpremotecompose.shared.generated.resources.Res
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * REM-82/C5 — proves the unified `RemoteComposeApp` default loader path actually serves the corpus from
 * Compose-Multiplatform resources (`composeResources/files/rc/`). This is the on-JVM check of the
 * loader the browser will use async; the browser render itself is REM-80 (test-2). Decode-only, no
 * graphics backend — runs on the plain `jvm()` test target.
 *
 * Fail-closed proof: a missing name must THROW (not return empty bytes) so `RemoteComposeApp`'s catch
 * surfaces `rc-error` (spec §2) rather than a silent "rendered empty".
 *
 * REM-166 (Blocker-1 fix, method-level): the two **valid-doc loaders** below need a real resource served
 * via the **Compose Resources** mechanism (`Res.readBytes`) — a SECOND resource path distinct from the
 * [RcCorpus] system-filesystem path the rest of REM-166's `@IgnoreOnWasm` sweep targeted (the call-graph
 * blind-spot). composeResources are not served by the `wasmJsBrowserTest` karma runner, so they throw
 * "resource not in test env" there. Option (b) — bundling composeResources into the karma/webpack test
 * server — needs a path-mapping only verifiable in a real browser (not runnable headlessly), so per the
 * PO's escalation guidance these take fallback (a): skip on wasm. **No real wasm bug is hidden** — REM-161
 * already proves this exact load path end-to-end in a real browser (the web app loads `.rc` via
 * `Res.readBytes` on wasm, test-2-verified); we lose only the unit-regression guard on wasm, not the proof.
 *
 * `missingDoc_throws_failClosed` is **deliberately left running on wasm**: it needs no real resource (a
 * missing name throws regardless of the served set), so it keeps the fail-closed security-path coverage on
 * all four targets (consistent with REM-166's method-level principle — don't over-skip).
 */
class Rem82ResourceLoaderTest {

    @Test
    @IgnoreOnWasm
    fun defaultDoc_loadsViaComposeResources_andDecodes() = runTest {
        Builtins.register()
        val bytes = Res.readBytes("files/rc/${RcRouter.DEFAULT_DOC}.rc")
        assertTrue(bytes.isNotEmpty(), "default doc bytes from composeResources must be non-empty")
        val doc = DocumentReader.inflate(bytes)
        assertTrue(doc.width > 0 && doc.height > 0, "decoded doc has real dims (${doc.width}x${doc.height})")
    }

    @Test
    @IgnoreOnWasm
    fun severalCorpusDocs_resolveFromComposeResources() = runTest {
        Builtins.register()
        // A spread of bundled docs (geometry, text, clock) — all must resolve + decode via Res.
        for (name in listOf("procedure_simple1", "clock", "c_text", "color_table")) {
            val bytes = Res.readBytes("files/rc/$name.rc")
            assertTrue(bytes.isNotEmpty(), "$name must resolve from composeResources")
            DocumentReader.inflate(bytes) // must not throw
        }
    }

    @Test
    fun missingDoc_throws_failClosed() = runTest {
        var threw = false
        try {
            Res.readBytes("files/rc/${RcRouter.UNKNOWN_DOC}.rc")
        } catch (t: Throwable) {
            threw = true
        }
        assertTrue(threw, "a missing resource MUST throw (fail-closed → rc-error), not return empty bytes")
    }
}
