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

/**
 * REM-114: frame-pacing seam for the live render loop ([RemoteComposeApp]).
 *
 * On Android/Desktop/iOS the driver is [androidx.compose.runtime.withFrameNanos] (vsync-aligned, current
 * behaviour). On **wasmJs `ComposeViewport`** a pure `withFrameNanos` loop can park forever when the live
 * loop is the *only* frame driver: `withFrameNanos` awaits a frame from the `MonotonicFrameClock`, but the
 * clock only schedules a frame on invalidation, and the loop can't invalidate `frameTime` until
 * `withFrameNanos` returns — a deadlock that freezes every time-driven doc at t=0 (clock, cube3d spin).
 * Static render is unaffected (it pins t=0 and never calls `withFrameNanos`), which is why the freeze went
 * unnoticed until the web live sweep (REM-114, isolated by test-2).
 *
 * The wasm actual breaks the deadlock with an independent timer tick (it does not depend on the idle-prone
 * frame clock): each tick advances `frameTime`, whose snapshot write invalidates composition and the
 * recomposer redraws on the next browser frame as usual.
 *
 * @return a monotonically increasing timestamp in nanoseconds (origin is arbitrary; only deltas matter).
 */
internal expect suspend fun awaitAnimationFrameNanos(): Long
