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
 * REM-135: the **deterministic** baseline host palette — `withLegacyAliases(BASELINE_SYSTEM_PALETTE)`,
 * target-independent (no live device colors). Goldens/captures seed THIS on *every* target — including
 * Android, deliberately overriding its live Material-You accent — so captures are deterministic and
 * cross-target identical (analog to the density=1.0 capture pin). Includes the REM-133 legacy fixed
 * framework colors once that lands (they are merged in by [withLegacyAliases]).
 */
fun baselineHostPalette(): Map<String, Int> = withLegacyAliases(BASELINE_SYSTEM_PALETTE)

/**
 * REM-135: the single shared seam that seeds the host system-accent palette into a render [RemoteContext]
 * before a paint pass. It exists because the seed was previously inlined in `RemoteComposeApp` ONLY — so
 * every headless render/golden harness (DesktopRenderSweep, the web sweep, mobile capture) built a bare
 * context and skipped it, which silently degraded every `NamedVariable`-bound theme color to the doc's
 * debug `ColorConstant` fallback in goldens (proven: digital_clock1 4/4, color_table 192/195 colors flip
 * to real tones only when seeded). Routing the live app AND every harness through this one function keeps
 * them from drifting again.
 *
 * Mirrors the host-input nature of [DensityProvider] (PROJECT_CONTEXT §5): the palette is a
 * platform-sourced render input, supplied behind an abstraction — not hardcoded in `commonMain`.
 *
 * @param palette the name→ARGB palette to seed. **Defaults to the deterministic [baselineHostPalette]** —
 *   what every golden/capture harness wants on all targets (incl. Android, overriding its live accent for
 *   capture determinism). The **live app** passes [systemAccentPalette] explicitly so it keeps the real
 *   device Material-You accent on Android (and avoids re-resolving it every frame by passing a cached map).
 */
fun RemoteContext.seedHostPalette(palette: Map<String, Int> = baselineHostPalette()) {
    setThemePaletteByName(palette)
}
