package com.tneff.kmpremotecompose.conformance

import okio.FileSystem

/**
 * wasmJs in the browser has no `FileSystem.SYSTEM` (no real filesystem) → null. Corpus-backed tests fail
 * closed here ([RcCorpus] throws); platform-pure unit tests still compile and run on wasm (REM-122).
 */
internal actual fun corpusSystemFileSystem(): FileSystem? = null
