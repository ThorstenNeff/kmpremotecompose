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

import androidx.compose.ui.text.font.FontFamily
import com.tneff.kmpremotecompose.remote.player.compose.NamedFontResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * REM-158 — headless logic for the commonMain [NamedFontResolver] (the cross-target single-source
 * name/enum → [FontFamily] table for the `TYPEFACE` op). Pins the enum mapping, the bundled-table-wins
 * rule, the keyword fallback to generic CMP families, and that default/unknown → null (renderer default,
 * golden-safe). The *visual* DancingScript render is the test-3 sweep.
 */
class Rem158NamedFontResolverTest {

    @Test
    fun enumMapping_genericFamilies() {
        val r = NamedFontResolver()
        assertSame(FontFamily.SansSerif, r.resolveEnum(NamedFontResolver.FONT_SANS))
        assertSame(FontFamily.Serif, r.resolveEnum(NamedFontResolver.FONT_SERIF))
        assertSame(FontFamily.Monospace, r.resolveEnum(NamedFontResolver.FONT_MONOSPACE))
        assertNull(r.resolveEnum(NamedFontResolver.FONT_DEFAULT), "0=default → renderer default (no swap)")
        assertNull(r.resolveEnum(99), "unknown enum → null")
    }

    @Test
    fun nameKeywordFallback_toGenericFamilies_whenNothingBundled() {
        val r = NamedFontResolver() // empty bundle → keyword fallback only
        assertSame(FontFamily.Cursive, r.resolveName("cursive"))
        assertSame(FontFamily.Cursive, r.resolveName("DancingScript"), "script-ish name → cursive fallback")
        assertSame(FontFamily.Monospace, r.resolveName("Roboto Mono"))
        assertSame(FontFamily.Serif, r.resolveName("Noto Serif"))
        assertSame(FontFamily.SansSerif, r.resolveName("sans-serif"))
        assertNull(r.resolveName("Wingdings"), "unclassifiable name → null (renderer default)")
        assertNull(r.resolveName(null))
        assertNull(r.resolveName("   "))
    }

    @Test
    fun bundledTableWins_overKeywordFallback_caseInsensitive() {
        // A bundled cursive family (use Monospace as a distinct proxy) must win over the generic fallback.
        val bundledCursive = FontFamily.Monospace
        val r = NamedFontResolver(named = mapOf(NamedFontResolver.KEY_CURSIVE to bundledCursive))
        assertSame(bundledCursive, r.resolveName("cursive"), "exact bundled key wins")
        assertSame(bundledCursive, r.resolveName("CURSIVE"), "lookup is case-insensitive")
        assertSame(bundledCursive, r.resolveName("DancingScript"), "script keyword routes to bundled cursive")
    }

    @Test
    fun bundledExactName_wins() {
        val robotoFlex = FontFamily.Serif // proxy for the bundled RobotoFlex
        val r = NamedFontResolver(named = mapOf("robotoflex" to robotoFlex))
        assertSame(robotoFlex, r.resolveName("RobotoFlex"))
        assertSame(robotoFlex, r.resolveName(" robotoflex "), "trimmed + lowercased")
    }

    @Test
    fun nameIdThreshold_matchesUpstreamRule() {
        // Documented contract: font_type is a name id only when > 10 (and ttf bit unset).
        assertEquals(10, NamedFontResolver.NAME_ID_THRESHOLD)
    }
}
