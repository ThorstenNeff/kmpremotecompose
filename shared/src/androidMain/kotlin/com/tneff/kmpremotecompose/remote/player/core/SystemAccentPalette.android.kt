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

import android.content.res.Resources
import android.os.Build

/**
 * REM-68 — Android Material-You palette. **API 31+ (S):** read the **real device** colors via
 * `Resources.getSystem().getColor(android.R.color.system_*)`, resolved by name through `getIdentifier`
 * (no hard-coded `R.color` refs, no Android `Color` in `commonMain`). A name that isn't a public
 * framework color (e.g. the explicit `…_light`/`…_dark` tones) or any failure falls back to the static
 * [BASELINE_SYSTEM_PALETTE] value. **Pre-31:** no system accent → the baseline (mirrors iOS).
 */
actual fun systemAccentPalette(): Map<String, Int> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return withLegacyAliases(BASELINE_SYSTEM_PALETTE)
    val res = Resources.getSystem()
    val resolved = LinkedHashMap<String, Int>(BASELINE_SYSTEM_PALETTE.size)
    for ((docName, fallback) in BASELINE_SYSTEM_PALETTE) {
        val bare = docName.removePrefix("color.") // e.g. system_accent1_500 → android.R.color.system_accent1_500
        val id = res.getIdentifier(bare, "color", "android")
        resolved[docName] = if (id != 0) {
            try {
                @Suppress("DEPRECATION")
                res.getColor(id, null)
            } catch (t: Throwable) {
                fallback
            }
        } else {
            fallback
        }
    }
    return withLegacyAliases(resolved)
}
