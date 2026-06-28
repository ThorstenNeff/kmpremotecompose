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

import kotlinx.coroutines.delay

/**
 * REM-114: wasmJs live-loop frame driver. A pure `withFrameNanos` loop deadlocks against the idle wasm
 * `ComposeViewport` frame clock (see [awaitAnimationFrameNanos] doc), so we drive the tick from an
 * independent ~60 fps timer instead. `delay` is backed by the JS event loop (setTimeout), which runs
 * regardless of the Compose frame clock, so the loop always advances `frameTime`; the resulting snapshot
 * write invalidates composition and the recomposer redraws on the next browser frame as usual.
 */
internal actual suspend fun awaitAnimationFrameNanos(): Long {
    delay(FRAME_MILLIS)
    return (performanceNowMs() * NANOS_PER_MILLI).toLong()
}

private const val FRAME_MILLIS = 16L // ≈ 60 fps
private const val NANOS_PER_MILLI = 1_000_000.0

/** `performance.now()` — a monotonic high-resolution timestamp in milliseconds. */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun performanceNowMs(): Double = js("performance.now()")
