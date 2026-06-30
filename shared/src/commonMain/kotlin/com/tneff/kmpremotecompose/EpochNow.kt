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
 * REM-178-S2 — current Unix-epoch seconds from the platform wall clock. Used by the live App-Shell
 * ([RemoteComposeApp]) as the default seed for `ID_EPOCH_SECOND` (REM-176) when the deep-link
 * `&epoch=<sec>` override ([RcRouter.epochSeconds]) is absent — so a live launch of an epoch-driven
 * doc (solar_gmt, moon_phases, REM-177 sub-clocks) shows TODAY's calendar values, not 1970.
 *
 * Per-platform wall-clock seam (`expect` keeps the App-Shell platform-agnostic):
 *  - Android/JVM: `System.currentTimeMillis() / 1000L`
 *  - iOS: `NSDate().timeIntervalSince1970.toLong()`
 *  - wasmJs: `(Date.now() / 1000.0).toLong()`
 *
 * Returns whole seconds (truncated). Sub-second precision is irrelevant: the REM-177 calendar
 * derivations (year / month / day-of-year / day-of-week) all operate at day-of-epoch granularity,
 * and the solar / sunrise / moon-phase expressions in the affected docs sample the day's mean,
 * not an instantaneous tick.
 *
 * **Determinism boundary (DO NOT seed a golden capture from this).** The desktop static-render
 * sweep uses [com.tneff.kmpremotecompose.remote.sweep.RenderTimePins.epochFor] for the 2
 * epoch-pinned docs; Maestro static captures use the `&epoch=<sec>` deep-link override; only the
 * **live** App-Shell falls through to [epochNow]. Test-config (epochFor) is deliberately kept out
 * of the Library so the live path has no test-pin coupling (REM-178-S2 architecture cleanup,
 * PO Option-a 2026-06-30).
 */
internal expect fun epochNow(): Long
