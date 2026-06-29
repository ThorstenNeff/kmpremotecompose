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
package com.tneff.kmpremotecompose.remote.player.core

/**
 * REM-137: the single shared per-document **static-time pin** map for deterministic render/golden
 * captures. Time-sensitive docs (analog/digital clocks) collapse to a degenerate frame at `t=0` (hands
 * stacked at 12:00), so a sweep that defaults to `t=0` would re-flag them as a false Δ against goldens
 * captured at the REM-69 cross-platform-safe time. Pinning them to one fixed time makes the capture
 * deterministic AND cross-target identical.
 *
 * This is the **one source** every sweep/capture consumes (DesktopRenderSweep, the web sweep, mobile
 * capture) — exactly analogous to the shared [seedHostPalette] seam: a single render-config map so the
 * targets cannot drift apart. Consumer wiring per target is the testers' lane; they look up
 * [RenderTimePins.timeFor] for the doc being captured and pass it as the player's `staticTimeSeconds`.
 *
 * Test/render-config only — never touches the `.rc` wire format (§2 irrelevant). The live app does NOT
 * consume this (its time is user/deep-link driven, [com.tneff.kmpremotecompose.RcRouter]); pinning is a
 * capture-determinism concern, like the density=1.0 / BASELINE-palette capture pins.
 */
object RenderTimePins {

    /**
     * REM-69 cross-platform-safe clock time, in seconds: 36630 = 10:10:30. A non-degenerate frame (hour,
     * minute and second hands all distinct), so a clock renders a stable, visually-meaningful face instead
     * of the `t=0` 12:00 collapse.
     */
    const val CLOCK_SAFE_TIME_SECONDS: Float = 36630f

    /**
     * doc name (without the `.rc` extension) → pinned static time in seconds. The docs test-3 confirmed as
     * time-sensitive (collapse to 12:00 at t=0): the REM-135 sweep found `clock`/`digital_clock1`; the
     * REM-138 fancy-clock census found 14 more. Extend here (the single source) if another doc proves
     * time-sensitive — do not re-introduce per-target `--t` hardcoding.
     */
    private val pins: Map<String, Float> = mapOf(
        "clock" to CLOCK_SAFE_TIME_SECONDS,
        "digital_clock1" to CLOCK_SAFE_TIME_SECONDS,
        // REM-138 — 14 further time-sensitive clock docs (test-3 fancy-clock census).
        "experimental_gmt" to CLOCK_SAFE_TIME_SECONDS,
        "experimental_solar_gmt" to CLOCK_SAFE_TIME_SECONDS,
        "clock_demo1_clock1" to CLOCK_SAFE_TIME_SECONDS,
        "clock_demo2_jancy_clock2" to CLOCK_SAFE_TIME_SECONDS,
        "clock_demo2_jclock2" to CLOCK_SAFE_TIME_SECONDS,
        "fancy_clock2" to CLOCK_SAFE_TIME_SECONDS,
        "fancy_clocks_fancy_clock1" to CLOCK_SAFE_TIME_SECONDS,
        "fancy_clocks_fancy_clock2" to CLOCK_SAFE_TIME_SECONDS,
        "fancy_clocks_fancy_clock3" to CLOCK_SAFE_TIME_SECONDS,
        "server_clock" to CLOCK_SAFE_TIME_SECONDS,
        "wake_demo_wake_clock" to CLOCK_SAFE_TIME_SECONDS,
        "texture_demo_texture_clock" to CLOCK_SAFE_TIME_SECONDS,
        "experimental_fancy_clock" to CLOCK_SAFE_TIME_SECONDS,
        "experimental_sweep_clock1" to CLOCK_SAFE_TIME_SECONDS,
    )

    /**
     * The pinned static time for [docName] (a corpus name with or without a trailing `.rc`), or [default]
     * (t=0) when the doc is not time-pinned. Safe to call for every doc in a sweep.
     */
    fun timeFor(docName: String, default: Float = 0f): Float =
        pins[docName.removeSuffix(".rc")] ?: default

    /** True if [docName] (with or without `.rc`) has an explicit time pin. */
    fun isPinned(docName: String): Boolean = pins.containsKey(docName.removeSuffix(".rc"))
}
