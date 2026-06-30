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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * REM-106 (FALLBACK_TYPEFACE / glyph-fallback, Epic REM-154) — headless drift-guard for the **generalized**
 * fallback trigger [ComposeTextRenderer.needsFallbackCodepoint], the successor to REM-110's hardcoded
 * 7-codepoint allowlist. Pins three properties: (1) it still covers the whole REM-110 corpus census, so the
 * generalization is a strict superset (no regression); (2) it now fires for **arbitrary** symbol-block
 * glyphs the corpus never contained (the actual REM-106 value — foreign content no longer tofus on web);
 * (3) it still excludes the default-font-covered glyphs (Latin-1, bullet, ASCII) so a Latin-bearing segment
 * is never pulled onto the symbol font. The *visual* glyph-vs-tofu proof is the web/Desktop render sweep;
 * the segment-aware rendering path is exercised on Skiko in `Rem106FallbackTypefaceIosTest`.
 */
class Rem106FallbackTypefaceTest {

    @Test
    fun generalizedTrigger_coversEntireRem110Census() {
        // Every codepoint REM-110 hardcoded must still trigger — the generalization is a superset.
        for (cp in ComposeTextRenderer.SYMBOL_FALLBACK_CODEPOINTS) {
            assertTrue(
                ComposeTextRenderer.needsFallbackCodepoint(cp),
                "REM-110 census U+${cp.toString(16).uppercase()} must still trigger the fallback",
            )
        }
    }

    @Test
    fun generalizedTrigger_firesForArbitrarySymbolGlyphsNotInCorpus() {
        // The point of REM-106: symbol-block glyphs the 173-doc corpus never contained must ALSO fall back,
        // so arbitrary foreign content renders instead of □. None of these are in SYMBOL_FALLBACK_CODEPOINTS.
        val arbitrary = mapOf(
            0x2192 to "→ rightwards arrow (Arrows)",
            0x2605 to "★ black star (Misc Symbols)",
            0x2600 to "☀ sun (Misc Symbols)",
            0x25C6 to "◆ black diamond (Geometric Shapes)",
            0x2714 to "✔ check mark (Dingbats)",
            0x2B50 to "⭐ white medium star (Misc Symbols & Arrows)",
        )
        for ((cp, desc) in arbitrary) {
            assertFalse(cp in ComposeTextRenderer.SYMBOL_FALLBACK_CODEPOINTS, "$desc is beyond the census")
            assertTrue(ComposeTextRenderer.needsFallbackCodepoint(cp), "$desc must trigger the generalized fallback")
        }
    }

    @Test
    fun generalizedTrigger_excludesDefaultCoveredAndOutOfScope() {
        // Default-font-covered glyphs must never trigger (would risk pulling Latin onto the symbol font).
        assertFalse(ComposeTextRenderer.needsFallbackCodepoint(0x0041), "ASCII 'A' must not trigger")
        assertFalse(ComposeTextRenderer.needsFallbackCodepoint(0x00B0), "° U+00B0 (default-covered) must not trigger")
        assertFalse(ComposeTextRenderer.needsFallbackCodepoint(0x00B2), "² U+00B2 (default-covered) must not trigger")
        assertFalse(ComposeTextRenderer.needsFallbackCodepoint(0x00B7), "· U+00B7 (default-covered) must not trigger")
        assertFalse(ComposeTextRenderer.needsFallbackCodepoint(0x2022), "• U+2022 (default-covered) must not trigger")
        // BMP-only by design: the bundled font has no CJK/astral coverage, so those stay on the base font
        // (honest base-scope limit — broadening the bundled font is a tracked follow-up). A surrogate unit
        // must never match (it would split a pair); all ranges are < U+D800.
        assertFalse(ComposeTextRenderer.needsFallbackCodepoint(0x4E00), "CJK U+4E00 is out of base scope (no bundled glyph)")
        assertFalse(ComposeTextRenderer.needsFallbackCodepoint(0xD83D), "a surrogate code unit must never trigger")
    }
}
