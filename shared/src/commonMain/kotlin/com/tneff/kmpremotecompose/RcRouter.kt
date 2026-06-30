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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * REM-34 — the one-route doc selector. The default launch renders [DEFAULT_DOC]; a deep-link
 * `kmprc://render?rc=<name>` selects another bundled fixture by setting [docName]. Each platform parses
 * its own deep-link (Android `intent.data`, iOS `onOpenURL`) and calls [select]; `RemoteComposeApp`
 * observes [docName] (Compose state) and reloads. An unknown name still flows through — the asset
 * lookup fails → `rc-error` (contract §2B).
 */
object RcRouter {

    /** The default bundled fixture rendered without any deep-link (contract §2A). */
    const val DEFAULT_DOC: String = "procedure_simple1"

    /**
     * Path-safe sentinel for a deep-link that *carried* an `rc` value which was blank or contained
     * invalid characters. It deliberately matches no bundled asset → the load fails → deterministic
     * `rc-error` instead of a silent fallback (REM-34 rc-doc-anchor hardening, test-2). Charset-valid
     * (no `/` or `..`) so it can never escape the `rc/` dir.
     */
    const val UNKNOWN_DOC: String = "__unknown__"

    /** The currently selected bundled doc name (no extension). Observed by `RemoteComposeApp`. */
    var docName: String by mutableStateOf(DEFAULT_DOC)
        private set

    /**
     * Live-animation flag (REM-37 E-D1): set from a deep-link `&live=1` to opt the render loop into
     * advancing `frameTimeSeconds` per frame (clocks tick, cube3d spins). **Default `false` = static
     * `t=0`** so the golden sweep stays deterministic — the flag controls only the time-advance, never
     * the render path. Set directly by each platform's deep-link parser.
     */
    var live: Boolean by mutableStateOf(false)

    /**
     * Static-mode frame pin (REM-62): in static (non-live) capture the player seeds the wall-clock time
     * vars from this value (seconds) instead of `t=0`, so an analog clock renders a deterministic frame
     * with **spread** hands instead of the degenerate 12:00:00 collapse (all hands on one angle). Set from
     * a deep-link `&t=<sec>`. **Default `0f`, and `&t` absent / `&t=0` / invalid → `0f` = the exact
     * pre-REM-62 `t=0` path.** Static-only: the live loop never reads this (it advances `frameTimeSeconds`),
     * so the animation path is untouched. Parsed/clamped via [setStaticTime].
     */
    var staticTimeSeconds: Float by mutableStateOf(0f)
        private set

    /**
     * REM-178-S2 — explicit Unix-epoch-seconds override for the player (deep-link `&epoch=<sec>`).
     * **Default `0L`** = no override; the live App-Shell falls back to [epochNow] (real-now from
     * the platform wall clock). Maestro static captures of the 2 epoch-pinned docs
     * (`experimental_solar_gmt`, `moon_phases`, REM-176 / REM-177) deep-link with
     * `&epoch=1751529600` (= `EPOCH_SAFE_PIN`) so they render the deterministic anchor instead of
     * "now". Parsed/clamped via [setEpochSeconds].
     *
     * Test-config (`RenderTimePins.epochFor`) lives ONLY in the desktop static-render sweep harness;
     * it is deliberately NOT consulted by [RemoteComposeApp] (REM-178-S2 architecture cleanup —
     * the live app must not carry test-pin knowledge).
     */
    var epochSeconds: Long by mutableStateOf(0L)
        private set

    /**
     * Forced render density (REM-91): set from a deep-link `&density=<f>` to override the platform density
     * ([RemoteComposeApp] uses `forcedDensity ?: LocalDensity`). **Default `null` = use the real platform
     * density** (untouched behavior). Cross-target parity needs a uniform density: iOS-Sim is fixed @3x with
     * no OS lever, so `&density=1.0` forces a uniform 1.0 on every target → raw-px Desktop↔Mobile compare
     * without the platform density asymmetry. Parsed/validated via [setForcedDensity].
     */
    var forcedDensity: Float? by mutableStateOf(null)
        private set

    /**
     * Force the deterministic BASELINE host palette (REM-135): set from a deep-link `&palette=baseline` so a
     * golden/capture renders with `baselineHostPalette()` on **every** target — deliberately overriding
     * Android's live Material-You device accent, which is non-deterministic across emulators/devices/API.
     * **Default `false` = the live app keeps the real device accent** (untouched behavior); only an explicit
     * `&palette=baseline` flips it. This is the palette analogue of the `&density=1.0` capture-determinism
     * pin: the live app gets the device-true accent, captures get a fixed cross-target palette so goldens are
     * reproducible. Parsed via [setForceBaselinePalette]. [RemoteComposeApp] selects the palette on it.
     */
    var forceBaselinePalette: Boolean by mutableStateOf(false)
        private set

    /**
     * Parse a deep-link `&density=<f>` value into [forcedDensity]. **Fail-safe to `null`** (use platform
     * density): `null` / blank / non-numeric / non-positive / non-finite all map to `null`, so only a valid
     * positive number ever forces the density. Centralized so platform parsers and tests share one rule.
     */
    fun setForcedDensity(value: String?) {
        val parsed = value?.trim()?.toFloatOrNull()
        forcedDensity = if (parsed != null && parsed.isFinite() && parsed > 0f) parsed else null
    }

    /**
     * Parse a deep-link `&palette=<mode>` value into [forceBaselinePalette]. **Fail-safe to `false`** (the
     * live device accent): only the exact literal `baseline` flips it on; `null` / blank / anything else maps
     * to `false`, so a normal launch is never accidentally de-themed. Centralized so platform parsers and
     * tests share one rule (mirrors [setForcedDensity]).
     */
    fun setForceBaselinePalette(value: String?) {
        forceBaselinePalette = value?.trim() == "baseline"
    }

    /**
     * Parse a deep-link `&t=<sec>` value into [staticTimeSeconds]. **Fail-safe to `0f`** (the pre-REM-62
     * path): `null` / blank / non-numeric / negative / non-finite all map to `0f`, so only a valid
     * non-negative number ever changes the static frame. Centralized here so both platform deep-link
     * parsers and the tests share the one rule.
     */
    fun setStaticTime(value: String?) {
        val parsed = value?.trim()?.toFloatOrNull()
        staticTimeSeconds = if (parsed != null && parsed.isFinite() && parsed >= 0f) parsed else 0f
    }

    /**
     * REM-178-S2 — parse a deep-link `&epoch=<sec>` value into [epochSeconds]. **Fail-safe to
     * `0L`** (no override → live app falls back to [epochNow]): `null` / blank / non-numeric /
     * non-Long / negative all map to `0L`. Long range so values past 2038 work. Centralized so
     * platform parsers + tests share one rule (mirrors [setStaticTime]).
     */
    fun setEpochSeconds(value: String?) {
        val parsed = value?.trim()?.toLongOrNull()
        epochSeconds = if (parsed != null && parsed >= 0L) parsed else 0L
    }

    /**
     * Reset transient deep-link state to the **static default** (REM-62 hardening) — call on a *fresh app
     * launch*. [RcRouter] is a process-level singleton, so a `live`/pin set by an earlier capture can
     * survive into a new launch if the harness (Maestro `clearState`) does not force-stop the process →
     * a time-driven doc would then render a non-deterministic *live* frame even without `&live`. Resetting
     * at launch makes a fresh start unconditionally static (t=0); the launch intent re-applies any params
     * it actually carries. Does NOT touch [docName] (the default-launch render is preserved, contract §2A).
     */
    fun resetForLaunch() {
        live = false
        setStaticTime(null) // → staticTimeSeconds = 0f
        setForcedDensity(null) // → forcedDensity = null (use platform density)
        setForceBaselinePalette(null) // → forceBaselinePalette = false (use live device accent)
        setEpochSeconds(null) // REM-178-S2 → epochSeconds = 0L (live app falls back to epochNow)
    }

    /**
     * Select a bundled doc by name from a deep-link. **Deterministic** unknown-handling (test-2 fix):
     * - `null` (no `rc` query param — a normal launch, or a param-less deep-link) → keep the current
     *   selection, so the default-launch render (contract §2A) is preserved.
     * - a non-null name that is blank or outside the bundled-asset charset (`[A-Za-z0-9_-]`, which also
     *   blocks `rc/` path-traversal) → [UNKNOWN_DOC] → load fails → `rc-error` (never a silent fallback).
     * - a valid name (known or unknown) → selected; an unknown one surfaces as `rc-error` at load.
     *
     * So `rc-doc` only ever carries a real, actually-rendered doc; a bad deep-link is always `rc-error`.
     */
    fun select(name: String?) {
        if (name == null) return // no rc param → keep current (default-launch path)
        val n = name.trim()
        docName = if (n.isNotEmpty() && n.all { it.isLetterOrDigit() || it == '_' || it == '-' }) n else UNKNOWN_DOC
    }
}
