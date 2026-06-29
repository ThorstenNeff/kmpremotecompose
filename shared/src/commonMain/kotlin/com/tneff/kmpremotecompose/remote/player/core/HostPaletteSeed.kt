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
 * REM-135: the single shared seam that seeds the host system-accent palette into a render [RemoteContext]
 * before a paint pass. It exists because the seed was previously inlined in `RemoteComposeApp` ONLY — so
 * every headless render/golden harness (DesktopRenderSweep, the web sweep, mobile capture) built a bare
 * context and skipped it, which silently degraded every `NamedVariable`-bound theme color to the doc's
 * debug `ColorConstant` fallback in goldens (proven: digital_clock1 / color_table render the debug
 * cyan/green unless the palette is seeded). Routing both the live app AND every harness through this one
 * function keeps them from drifting again.
 *
 * Mirrors the host-input nature of [DensityProvider] (PROJECT_CONTEXT §5): the palette is a
 * platform-sourced render input (live device Material-You on Android, a fixed baseline elsewhere for
 * cross-platform parity), supplied via the [systemAccentPalette] expect/actual — not hardcoded in
 * `commonMain`.
 *
 * @param palette the name→ARGB palette to seed; defaults to [systemAccentPalette]. The live app passes
 *   a value it already resolved once per composition (Android reads device colors), so it does not
 *   re-resolve every frame; one-shot harnesses can use the default.
 */
fun RemoteContext.seedHostPalette(palette: Map<String, Int> = systemAccentPalette()) {
    setThemePaletteByName(palette)
}
