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

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import com.tneff.kmpremotecompose.remote.player.compose.NamedFontResolver
import kmpremotecompose.shared.generated.resources.Res
import kmpremotecompose.shared.generated.resources.dancing_script
import kmpremotecompose.shared.generated.resources.roboto_flex
import org.jetbrains.compose.resources.Font

/**
 * REM-158 — build the [NamedFontResolver] with the **bundled** default font families, resolved at
 * composition scope (the `@Composable Font` from `composeResources/font` needs composition, like the
 * REM-110 symbol fallback). The same bundled binaries are shipped on **every** target (Android/iOS/
 * Desktop/Web), so the resolver is one cross-target single-source — no `expect`/`actual` per platform.
 *
 * Bundled (OFL, from the official `google/fonts` repo, like `rc_symbol_fallback.ttf`):
 *  - **DancingScript** (variable) → the cursive/script slot, so `cursive`/`script` names render a real
 *    cursive on every target (web has no reliable generic cursive — the REM-158 gate's positive proof).
 *  - **RobotoFlex** (variable) → a named variable-font default.
 *
 * System-sans needs no binary — the resolver maps the sans enum/keyword to the built-in
 * [FontFamily.SansSerif]. Unmapped names fall through to generic CMP families (see [NamedFontResolver]).
 */
@Composable
fun rememberNamedFontResolver(): NamedFontResolver {
    val dancingScript = FontFamily(Font(Res.font.dancing_script))
    val robotoFlex = FontFamily(Font(Res.font.roboto_flex))
    return NamedFontResolver(
        named = mapOf(
            NamedFontResolver.KEY_CURSIVE to dancingScript,
            "dancingscript" to dancingScript,
            "dancing script" to dancingScript,
            "robotoflex" to robotoFlex,
            "roboto flex" to robotoFlex,
        ),
    )
}
