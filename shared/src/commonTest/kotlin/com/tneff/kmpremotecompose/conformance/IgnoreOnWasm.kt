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

/**
 * REM-166 — marks a test (class or method) that requires the on-disk `.rc` corpus and is therefore
 * **JVM/Native-only**: on wasm-browser there is no `FileSystem.SYSTEM` ([corpusSystemFileSystem] returns
 * null), so [RcCorpus] throws by design (REM-122). Such a test cannot run in the browser, so it must be
 * excluded from `wasmJsBrowserTest` rather than throw there.
 *
 * Implemented as `expect`/`actual`: on **wasmJs** it aliases `kotlin.test.Ignore` (the test is skipped);
 * on every other target it is a **no-op** annotation (the test runs normally — full `jvmTest` coverage is
 * unchanged). This is the same per-platform-seam pattern as [corpusSystemFileSystem].
 *
 * Do NOT make [RcCorpus] silently return empty on wasm instead — a corpus test that "passes" against an
 * empty corpus is a vacuous false-green. Skipping (this annotation) keeps the corpus contract honest.
 */
expect annotation class IgnoreOnWasm()
