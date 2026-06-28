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

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import com.tneff.kmpremotecompose.remote.player.compose.ComposeTextRenderer
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * REM-110 symbol fallback on the **real CMP/Skiko** text backend — the same Skia shaper wasm uses, so
 * this is the closest local proxy for the web-only tofu bug. It proves the per-run family-swap path
 * executes and measures on Skiko and that a pure-Latin run is byte-for-byte unaffected (no regression).
 *
 * It does **not** assert "the heart pixels are a real glyph vs tofu": on iOS the system font already
 * has the heart, so any path renders *something*; the bug is web-only. The definitive **visual** proof
 * (♥/❤ render on wasm instead of □) is test-2's Chromium sweep — no glyph-vs-tofu proof is possible
 * from the headless/Skiko unit gate, and that is honestly out of dev-1's reach here.
 */
class Rem110SymbolFallbackIosTest {

    private fun canvas() = Canvas(ImageBitmap(64, 64))

    @Test
    fun symbolRun_withFallbackFamily_measuresAndDraws_onSkiko() {
        val r = ComposeTextRenderer(
            density = 2f,
            createFontFamilyResolver(),
            symbolFallbackFamily = FontFamily.Monospace, // any non-null family exercises the swap path
        )
        val bounds = FloatArray(4)
        r.getTextBounds("♥", 0, -1, 0, bounds) // ♥ → triggers the symbol-fallback swap
        assertTrue(bounds[2] > 0f, "heart run must measure a positive width through the fallback path")
        assertTrue(bounds[3] > bounds[1], "heart run must have a real (top<bottom) line box")
        // The draw path must not throw on Skiko with the fallback applied (❤ is the other corpus heart).
        r.drawTextRun(canvas(), "❤", 0, -1, 4f, 20f, rtl = false)
    }

    @Test
    fun latinRun_isByteForByteUnaffected_byFallback() {
        val withFb = ComposeTextRenderer(
            density = 2f, createFontFamilyResolver(), symbolFallbackFamily = FontFamily.Monospace,
        )
        val noFb = ComposeTextRenderer(density = 2f, createFontFamilyResolver())
        val a = FloatArray(4)
        val b = FloatArray(4)
        withFb.getTextBounds("Hello", 0, -1, 0, a) // no symbol codepoint → must NOT swap family
        noFb.getTextBounds("Hello", 0, -1, 0, b)
        assertTrue(a[2] > 0f && b[2] > 0f, "Latin must measure on both")
        // Identical measurements prove the per-run conditional leaves Latin runs on the default font.
        // (A global fallback would change this width to Monospace → this test guards the per-run design.)
        assertTrue(abs(a[2] - b[2]) < 0.01f, "pure-Latin run measures identically with/without fallback")
        assertTrue(abs(a[3] - b[3]) < 0.01f, "pure-Latin run line box identical with/without fallback")
    }
}
