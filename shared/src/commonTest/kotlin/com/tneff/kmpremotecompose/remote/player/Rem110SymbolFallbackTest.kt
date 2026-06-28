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

import com.tneff.kmpremotecompose.remote.player.compose.ComposeTextRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * REM-110 (♥-glyph font fallback) — corpus-grounding + drift guard for the symbol-fallback trigger set.
 *
 * The set was derived by decoding **every** `DATA_TEXT` op in the 173-doc corpus and collecting the
 * non-ASCII codepoints the CMP default web font lacks (→ tofu on wasm; Android/iOS fall back via the
 * system font). This test pins that census so the trigger set can't silently drift away from the docs
 * it must cover, and confirms default-covered glyphs (Latin-1 + bullet) are intentionally excluded so
 * the per-run family swap never touches a Latin-bearing run. Headless (constant only — no Skiko).
 */
class Rem110SymbolFallbackTest {

    @Test
    fun fallbackSetCoversCorpusSymbols() {
        val s = ComposeTextRenderer.SYMBOL_FALLBACK_CODEPOINTS
        // Both heart codepoints really present in the corpus must be covered — the bug report named only
        // ♥ U+2665, but the decode also found ❤ U+2764 (a different heart) in two more docs.
        assertTrue(0x2665 in s, "♥ U+2665 (heart_rate_timeline) must be covered")
        assertTrue(0x2764 in s, "❤ U+2764 (impulse_demo_hearts_demo, spline_demo) must be covered")
        // The remainder of the corpus symbol census the default web font lacks.
        assertTrue(0x26A1 in s, "⚡ U+26A1 (battery_radial_gauge) must be covered")
        assertTrue(0x2B29 in s, "⬩ U+2B29 (hydration_wave) must be covered")
        assertTrue(0x25B2 in s, "▲ U+25B2 (stock_sparkline) must be covered")
        assertTrue(0x2191 in s, "↑ U+2191 (pressure_gauge) must be covered")
        assertTrue(0x2193 in s, "↓ U+2193 (pressure_gauge, stock) must be covered")
        assertEquals(7, s.size, "trigger set is EXACTLY the corpus symbol census (no more, no less)")
    }

    @Test
    fun fallbackSetExcludesDefaultCoveredGlyphs() {
        val s = ComposeTextRenderer.SYMBOL_FALLBACK_CODEPOINTS
        // Latin-1 degree/superscript-2/middle-dot and the bullet are in the default font → they must NOT
        // trigger the swap (the only bullet run, "BTC • 13:3", is mixed; swapping it would risk Latin).
        assertFalse(0x00B0 in s, "° U+00B0 is default-covered; must not trigger fallback")
        assertFalse(0x00B2 in s, "² U+00B2 is default-covered; must not trigger fallback")
        assertFalse(0x00B7 in s, "· U+00B7 is default-covered; must not trigger fallback")
        assertFalse(0x2022 in s, "• U+2022 is default-covered; must not trigger fallback")
        assertFalse('A'.code in s, "plain ASCII never triggers the fallback")
    }
}
