package com.tneff.kmpremotecompose.conformance

import okio.FileSystem

/**
 * REM-122 — the platform default filesystem used to read the on-disk test corpus, or `null` where the
 * platform has none (wasm in the browser). `okio.FileSystem.SYSTEM` exists on JVM / Native / Android but
 * NOT on wasmJs-browser, so referencing it directly in `commonTest` broke `compileTestKotlinWasmJs`
 * (Unresolved reference 'SYSTEM') — which blocked ALL wasm unit coverage. Behind this `expect`, commonTest
 * compiles for wasm; corpus-backed tests fail closed there ([RcCorpus] throws a clear message), while
 * platform-pure unit tests (e.g. the `MonotonicSpline.getPos` regression) compile and run on wasm.
 */
internal expect fun corpusSystemFileSystem(): FileSystem?
