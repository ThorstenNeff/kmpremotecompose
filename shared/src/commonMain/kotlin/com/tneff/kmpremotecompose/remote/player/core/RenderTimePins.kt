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

    // -------------------------------------------------------------------------------------------
    // REM-176 — per-doc EPOCH pin (Unix-epoch-seconds → `paint(epochSeconds=…)`)
    // -------------------------------------------------------------------------------------------

    /**
     * **REM-176 EPOCH_SAFE_PIN**: 1751529600 = **2025-07-03 00:00:00 UTC** (Thursday). Chosen because:
     *  - **Past-fixed-date** (Jul-2025): the goldens are pinned to a specific historical instant so
     *    real-time advancement never drifts them. (REM-177-Korrektur 2026-06-30: an earlier KDoc
     *    miscalculated this as "2026-07-03"; the actual Unix-epoch-to-civil conversion — verified
     *    independently via the [CivilFromDays] port AND any standard epoch converter — is
     *    2025-07-03. Goldens are unaffected: both 2025-07-03 and 2026-07-03 are non-leap July 3s
     *    with day-of-year 184 and effectively identical solar parameters for Anchorage.)
     *  - **Polar-safe** at Anchorage (61°N): the `acos(cos_hour_angle)` formula in
     *    `experimental_solar_gmt.rc`'s RPN stays in [-1, 1] (no polar-day math break).
     *  - **CLOCK_SAFE-aligned** philosophy: a single shared deterministic timestamp matches the
     *    REM-69 pattern for sweep determinism. Live-mode docs (app) consume real-now epoch.
     */
    const val EPOCH_SAFE_PIN: Long = 1751529600L

    /**
     * Per-doc Unix-epoch-seconds pin. Defaults to `EPOCH_SAFE_PIN` for **6** confirmed date-
     * driven docs (REM-176 + REM-177 follow-up after test-3's broader-impact sweep 2026-06-30).
     *
     * The full set, decoded via `Rem177CalendarVarConsumerProbe`-style FloatExpression /
     * IntegerExpression / TextFromFloat NaN-var-ref scans:
     *
     *  | Doc                       | Consumed ids (system-var)                                | Lane    |
     *  |---------------------------|----------------------------------------------------------|---------|
     *  | `experimental_solar_gmt`  | 32 (EPOCH), 11 (WEEK_DAY), 12 (DAY_OF_MONTH)             | REM-176 |
     *  | `moon_phases`             | 32 (EPOCH)                                               | REM-176 |
     *  | `clock_demo2_jclock2`     | 34 (DAY_OF_YEAR) — 8 city sub-clocks                     | REM-177 |
     *  | `clock`                   | 11 (WEEK_DAY), 12 (DAY_OF_MONTH) — Mon..Sun day-name     | REM-177 |
     *  | `experimental_gmt`        | 11 (WEEK_DAY), 12 (DAY_OF_MONTH) — MON..SUN day-name     | REM-177 |
     *  | `player_info`             | 9 (MONTH), 11 (WEEK_DAY), 34 (DAY_OF_YEAR), 35 (YEAR)    | REM-177 |
     *
     * Without the pin those docs would render at `epoch=0` = 1970 = the very poisoned-golden the
     * REM-176/177 seeding fix is undoing:
     *  - day-name docs (`clock`, `experimental_gmt`) would index `Mon..Sun[week_day=0]` = empty
     *  - `experimental_solar_gmt` was already pinned for epoch but also now reads WEEK_DAY+
     *    DAY_OF_MONTH (PO test-3 surprise — confirmed correct shift, not spurious)
     *  - `clock_demo2_jclock2` collapses 8 cities to Jan-1 sunrise/sunset (assist REM-147 catch:
     *    ~1min shift per city, geometry hash identical → only text-value-capture detects)
     *  - `player_info` is a system-var debug-dashboard explicitly showing YEAR/MONTH/DAY_OF_YEAR/
     *    WEEK_DAY values — would print "0" for all without the seed
     *
     * Other docs that don't read any date var (167/173 per the §2-Befund-Inventar +
     * REM-177-Decode-Triage) are unaffected — `epochFor` returns `0L` for them, identical to the
     * byte-faithful legacy path. Extend here (the **single source**) if another doc proves
     * date-sensitive — no per-target `--epoch` hardcoding (mirror the `--t` discipline above).
     */
    private val epochPins: Map<String, Long> = mapOf(
        // REM-176
        "experimental_solar_gmt" to EPOCH_SAFE_PIN,
        "moon_phases" to EPOCH_SAFE_PIN,
        // REM-177 — 8-city sub-clock sunrise/sunset reads ID_DAY_OF_YEAR (id=34).
        "clock_demo2_jclock2" to EPOCH_SAFE_PIN,
        // REM-177-Decode-Triage 2026-06-30 — date-driven docs surfaced by test-3 broader-impact
        // sweep (decode-proven calendar-var consumers — see KDoc table above).
        "clock" to EPOCH_SAFE_PIN,
        "experimental_gmt" to EPOCH_SAFE_PIN,
        "player_info" to EPOCH_SAFE_PIN,
    )

    /**
     * The pinned Unix-epoch-seconds for [docName], or [default] (0L = "1970 legacy") when the doc
     * has no epoch pin. Sweep harnesses pass this as `RemoteComposePlayer.paint(epochSeconds=…)`.
     */
    fun epochFor(docName: String, default: Long = 0L): Long =
        epochPins[docName.removeSuffix(".rc")] ?: default

    /** True if [docName] has an explicit epoch pin (= sweep MUST pass it through). */
    fun isEpochPinned(docName: String): Boolean = epochPins.containsKey(docName.removeSuffix(".rc"))
}
