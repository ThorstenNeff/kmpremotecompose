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
package com.tneff.kmpremotecompose.remote.player

import com.tneff.kmpremotecompose.remote.player.core.systemAccentPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-68 — the platform palette (commonTest runs on the jvm/baseline actual). Verifies the Material-You
 * baseline values that the corpus theme docs reference (the values that REM-61/67 surfaced as wrong when
 * only the debug-placeholder `ColorConstant` fallback was loaded). Names carry the real `color.` prefix.
 */
class SystemAccentPaletteTest {

    private val pal = systemAccentPalette()

    @Test
    fun baselineValuesAreMaterialYou_notDebugFallbacks() {
        // The smoking gun from REM-61: system_accent1_100 is a near-white blue tint, NOT the doc's
        // #00FF00 debug ColorConstant fallback.
        assertEquals(0xFFD9E2FF.toInt(), pal["color.system_accent1_100"], "system_accent1_100 = near-white #D9E2FF")
        assertEquals(0xFFFFFFFF.toInt(), pal["color.system_accent1_0"], "tone 0 = white")
        assertEquals(0xFF000000.toInt(), pal["color.system_accent1_1000"], "tone 1000 = black")
        assertEquals(0xFF6476A5.toInt(), pal["color.system_accent1_500"], "accent1_500 = baseline blue")
    }

    @Test
    fun coversCorpusCriticalNames() {
        // clock / digital_clock1 / color_table / stock reference these families.
        for (n in listOf(
            "color.system_accent1_500", "color.system_accent2_500", "color.system_accent3_500",
            "color.system_neutral1_500", "color.system_neutral2_500", "color.system_error_500",
            "color.system_on_surface_light", "color.system_on_surface_dark",
        )) {
            assertTrue(pal.containsKey(n), "palette must cover $n")
            assertEquals(0xFF, (pal[n]!! ushr 24) and 0xFF, "$n is opaque")
        }
        // all 3 accents × 13 tones present.
        for (a in 1..3) for (m in listOf(0, 10, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000)) {
            assertTrue(pal.containsKey("color.system_accent${a}_$m"), "missing color.system_accent${a}_$m")
        }
    }

    @Test
    fun legacyAndroidAliasesResolveToSystemValues() {
        // android.* legacy names (approx, Phase-2b) resolve to a baseline system color.
        assertEquals(pal["color.system_accent1_500"], pal["android.colorAccent"], "colorAccent → accent1_500")
        assertEquals(pal["color.system_background_light"], pal["android.colorBackground"], "colorBackground → background_light")
        assertEquals(pal["color.system_on_surface_light"], pal["android.textColor"], "textColor → on_surface_light")
    }
}
