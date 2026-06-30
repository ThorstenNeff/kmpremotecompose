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

/** REM-178-S2: wasmJs wall-clock seam — `Date.now()` returns the current Unix time in milliseconds
 * (Number, the JS-canonical wall-clock source); divide by 1000 + truncate to whole seconds. */
internal actual fun epochNow(): Long = (dateNowMs() / 1000.0).toLong()

/** `Date.now()` — Unix-epoch milliseconds, the JS wall-clock primitive. */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun dateNowMs(): Double = js("Date.now()")
