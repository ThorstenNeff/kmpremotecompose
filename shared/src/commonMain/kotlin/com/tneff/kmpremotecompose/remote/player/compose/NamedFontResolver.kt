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
package com.tneff.kmpremotecompose.remote.player.compose

import androidx.compose.ui.text.font.FontFamily

/**
 * REM-158 — the **commonMain, cross-target single-source** font resolver for the `TYPEFACE` paint op.
 *
 * Upstream `PaintBundle` encodes a typeface as either an **enum** `font_type` (0 = default, 1 = sans,
 * 2 = serif, 3 = monospace) or, when `font_type > 10` and the `ttf` bit is unset, a **name id** that
 * dereferences a `DATA_TEXT` string (`getText(font_type)`). The wire carries the names as `DATA_TEXT`; the
 * player needs the missing string → [FontFamily] lookup. This is that lookup, shared by every target so
 * Android/iOS/Desktop/Web all resolve a name identically.
 *
 * [named] is the injected, composition-built table of **bundled** families keyed by a lowercased name
 * (e.g. `"cursive"`/`"dancingscript"` → the bundled DancingScript, `"robotoflex"` → RobotoFlex). It is
 * supplied by `RemoteComposeApp` from `composeResources/font` (the `@Composable Font` is resolved at
 * composition scope, like the REM-110 symbol fallback). System-sans needs no binary — it maps to the
 * built-in [FontFamily.SansSerif]. A `null` result means "leave the renderer default" (golden-safe for the
 * default/unknown case).
 */
class NamedFontResolver(
    private val named: Map<String, FontFamily> = emptyMap(),
) {

    /** Resolve an enum `font_type` (0 default / 1 sans / 2 serif / 3 mono) → a generic family, or null. */
    fun resolveEnum(fontType: Int): FontFamily? = when (fontType) {
        FONT_SANS -> FontFamily.SansSerif
        FONT_SERIF -> FontFamily.Serif
        FONT_MONOSPACE -> FontFamily.Monospace
        else -> null // 0 = default, or any unknown enum → renderer default (no swap)
    }

    /**
     * Resolve a font **name** → a [FontFamily]: an exact (case-insensitive) hit in the bundled [named]
     * table wins; otherwise a keyword fallback maps the name to a generic CMP family (always available on
     * every target) so an unknown but descriptive name still renders sensibly instead of silently
     * defaulting. A name we can't classify returns null (renderer default). Cursive/script names prefer the
     * bundled cursive family ([KEY_CURSIVE], e.g. DancingScript) and fall back to the generic
     * [FontFamily.Cursive] when nothing is bundled.
     */
    fun resolveName(name: String?): FontFamily? {
        if (name.isNullOrBlank()) return null
        val key = name.trim().lowercase()
        named[key]?.let { return it }
        return when {
            "cursive" in key || "script" in key || "dancing" in key -> named[KEY_CURSIVE] ?: FontFamily.Cursive
            "mono" in key -> FontFamily.Monospace
            "serif" in key && "sans" !in key -> FontFamily.Serif
            "sans" in key -> FontFamily.SansSerif
            else -> null
        }
    }

    companion object {
        const val FONT_DEFAULT = 0
        const val FONT_SANS = 1
        const val FONT_SERIF = 2
        const val FONT_MONOSPACE = 3

        /** Bundled-family key for the cursive/script slot (DancingScript when present). */
        const val KEY_CURSIVE = "cursive"

        /**
         * Upstream rule: a `TYPEFACE` `font_type` is a `DATA_TEXT` **name id** (not an enum) only when it is
         * `> 10` and the `ttf` bit is unset. Below/equal is always the small generic enum set.
         */
        const val NAME_ID_THRESHOLD = 10
    }
}
