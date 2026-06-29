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

    @Test
    fun rem133_legacyFixedFrameworkColorsResolveToAospValues() {
        // REM-133: the static AOSP Holo R.color constants color_table referenced (previously unmapped →
        // green/cyan debug fallback). Exact public-framework day-tones, present on every target.
        val expected = mapOf(
            "color.holo_blue_bright" to 0xFF00DDFF.toInt(),
            "color.holo_blue_light" to 0xFF33B5E5.toInt(),
            "color.holo_blue_dark" to 0xFF0099CC.toInt(),
            "color.holo_green_light" to 0xFF99CC00.toInt(),
            "color.holo_green_dark" to 0xFF669900.toInt(),
            "color.holo_red_light" to 0xFFFF4444.toInt(),
            "color.holo_red_dark" to 0xFFCC0000.toInt(),
            "color.holo_orange_light" to 0xFFFFBB33.toInt(),
            "color.holo_orange_dark" to 0xFFFF8800.toInt(),
            "color.holo_purple" to 0xFFAA66CC.toInt(),
            "color.background_dark" to 0xFF000000.toInt(),
            "color.background_light" to 0xFFFFFFFF.toInt(),
            "color.black" to 0xFF000000.toInt(),
            "color.darker_gray" to 0xFFAAAAAA.toInt(),
        )
        for ((name, argb) in expected) {
            assertEquals(argb, pal[name], "$name must resolve to its AOSP value, not the debug fallback")
            assertEquals(0xFF, (pal[name]!! ushr 24) and 0xFF, "$name is opaque")
        }
    }

    @Test
    fun rem133_disabledTokensAreEnabledToneAtMaterialDisabledAlpha() {
        // Fast-follow: the *_disabled tokens have no public AOSP R.color (theme-derived). Documented §5
        // approximation (PO option b): the enabled tone's RGB at the Material disabled alpha (0x61 = 38%).
        // Derived off the resolved base, so semi-transparent (alpha 0x61), NOT the debug fallback.
        val expected = mapOf(
            "color.system_on_surface_disabled" to 0x6130323A,
            "color.system_outline_disabled" to 0x61787A84,
            "color.system_surface_disabled" to 0x61FAF8FE,
        )
        for ((name, argb) in expected) {
            assertEquals(argb, pal[name], "$name = enabled tone @ disabled alpha 0x61")
            assertEquals(0x61, (pal[name]!! ushr 24) and 0xFF, "$name carries the 38% disabled alpha")
        }
    }
}
