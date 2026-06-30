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

import android.util.Log
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * REM-159 Mobile-bbox leg (Android / **native** text stack) — measures the 17 cross-target fixtures through
 * the real CMP [TextMeasurer] on a **real Android device** (instrumented, so the font resolver is the native
 * Android stack, NOT host/Skiko — that's the whole point of the cross-target drift matrix). **Unconstrained**
 * (`Constraints()` → no maxWidth → long-wrap stays single-line), **Density(1f)**, **default [TextStyle]** —
 * the exact protocol test-3 uses for the Desktop oracle.
 *
 * Emits `REM159,<name>,<w>,<h>` per fixture to logcat (tag `REM159`) — read via `adb logcat -s REM159`.
 */
@RunWith(AndroidJUnit4::class)
class Rem159MobileBboxAndroidTest {

    @Test
    fun measureFixtures_unconstrained_density1_androidNative() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val measurer = TextMeasurer(createFontFamilyResolver(ctx), Density(1f), LayoutDirection.Ltr)
        Log.i("REM159", "REM159_BEGIN,android")
        for ((name, text) in FIXTURES) {
            val r = measurer.measure(text, style = TextStyle.Default, constraints = Constraints())
            Log.i("REM159", "REM159,$name,${r.size.width},${r.size.height}")
            assertTrue("$name measured non-positive", r.size.width > 0 && r.size.height > 0)
        }
        Log.i("REM159", "REM159_END,android")
    }

    companion object {
        val FIXTURES = listOf(
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
