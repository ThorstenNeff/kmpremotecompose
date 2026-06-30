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

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * REM-159 Mobile-bbox leg (iOS / Skiko) — measures the 17 cross-target text fixtures through the **real
 * CMP [TextMeasurer]** on the iOS Skiko backend, **unconstrained** (`Constraints()` = no maxWidth → the
 * long-wrap fixture stays single-line so we compare advance-width drift, not a wrap-protocol divergence),
 * **Density(1f)**, **default [TextStyle]** — the exact protocol test-3 uses for the Desktop oracle.
 *
 * Emits a `REM159,<name>,<w>,<h>` CSV line per fixture (read from the test stdout). The PO consolidates the
 * Mobile leg with Desktop+Web into the cross-target drift matrix.
 */
class Rem159MobileBboxIosTest {

    @Test
    fun measureFixtures_unconstrained_density1_skiko() {
        val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
        println("REM159_BEGIN,ios")
        for ((name, text) in REM_159_FIXTURES) {
            val r = measurer.measure(text, style = TextStyle.Default, constraints = Constraints())
            println("REM159,$name,${r.size.width},${r.size.height}")
            assertTrue(r.size.width > 0 && r.size.height > 0, "$name measured non-positive")
        }
        println("REM159_END,ios")
    }

    companion object {
        val REM_159_FIXTURES = listOf(
            "ascii_latin" to "Hello World",
            "cjk_chinese" to "你好世界",
            "cjk_japanese" to "こんにちは世界",
            "cjk_korean" to "안녕하세요 세계",
            "devanagari" to "नमस्ते दुनिया",
            "thai" to "สวัสดีชาวโลก",
            "arabic_rtl" to "مرحبا بالعالم",
            "hebrew_rtl" to "שלום עולם",
            "mixed_bidi" to "Hello مرحبا World",
            "combining_diaeresis" to "n̈áôũ",
            "emoji_basic" to "🎉🍕❤",
            "emoji_zwj_family" to "👨‍👩‍👧",
            "emoji_skin_tone" to "👋🏽",
            "emoji_flag" to "🇺🇸",
            "long_wrap_latin" to "Lorem ipsum dolor sit amet, consectetur adipiscing elit, " +
                "sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.",
            "rem106_mixed_a_heart_b" to "A♥B",
            "rem45_hello_on_path" to "HELLO ON PATH",
        )
    }
}
