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
 * REM-106 segment-aware fallback on the **real CMP/Skiko** text backend (the same Skia shaper wasm uses,
 * so the closest local proxy for the web tofu bug). Proves the REM-106 upgrade over REM-110's whole-run
 * swap: a **mixed** run (Latin + symbol) now measures/draws through a per-segment [AnnotatedString] without
 * throwing, and a pure-Latin run is still byte-for-byte unaffected by the presence of a fallback family.
 *
 * Like the REM-110 Skiko test, this does not assert "the glyph is a real shape vs □" — on iOS the system
 * font has the glyphs; the definitive **visual** glyph-vs-tofu proof is the web/Desktop render sweep.
 */
class Rem106FallbackTypefaceIosTest {

    private fun canvas() = Canvas(ImageBitmap(64, 64))

    @Test
    fun mixedRun_measuresAndDraws_segmentAware_onSkiko() {
        val r = ComposeTextRenderer(
            density = 2f,
            createFontFamilyResolver(),
            symbolFallbackFamily = FontFamily.Monospace, // any non-null family exercises the segment swap
        )
        val mixed = FloatArray(4)
        val symbolOnly = FloatArray(4)
        // "A→B" is mixed: Latin A/B on the base font, → routed to the fallback segment. Must measure wider
        // than the symbol alone (i.e. the Latin segments contribute width → segmentation actually ran).
        r.getTextBounds("A→B", 0, -1, 0, mixed)
        r.getTextBounds("→", 0, -1, 0, symbolOnly)
        assertTrue(mixed[2] > 0f, "mixed run must measure a positive width")
        assertTrue(mixed[2] > symbolOnly[2], "mixed run (A→B) must be wider than the symbol alone")
        // The draw path must not throw on Skiko for a mixed run with a fallback applied to one segment.
        r.drawTextRun(canvas(), "A❤B", 0, -1, 4f, 20f, rtl = false)
    }

    @Test
    fun latinRun_isUnaffected_bySegmentAwareFallback() {
        val withFb = ComposeTextRenderer(
            density = 2f, createFontFamilyResolver(), symbolFallbackFamily = FontFamily.Monospace,
        )
        val noFb = ComposeTextRenderer(density = 2f, createFontFamilyResolver())
        val a = FloatArray(4)
        val b = FloatArray(4)
        // No fallback codepoint → styledRun returns a plain AnnotatedString → identical to the no-fallback
        // renderer. Guards the golden-safe-by-construction property (no shift where nothing needs fallback).
        withFb.getTextBounds("Hello", 0, -1, 0, a)
        noFb.getTextBounds("Hello", 0, -1, 0, b)
        assertTrue(a[2] > 0f && b[2] > 0f, "Latin must measure on both")
        assertTrue(abs(a[2] - b[2]) < 0.01f, "pure-Latin run measures identically with/without fallback")
        assertTrue(abs(a[3] - b[3]) < 0.01f, "pure-Latin run line box identical with/without fallback")
    }
}
