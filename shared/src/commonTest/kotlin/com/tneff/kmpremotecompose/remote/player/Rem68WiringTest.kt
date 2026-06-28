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

import com.tneff.kmpremotecompose.conformance.RcCorpus
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.Builtins
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.systemAccentPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * REM-68 combined unit (Core + Palette + Wiring) — applying the host palette before paint makes a
 * `NamedVariable`-bound theme color resolve to the real Material-You value, overriding the doc's debug
 * `ColorConstant` fallback. clock.rc: id 59 = `NamedVariable("color.system_accent2_50")` + `ColorConstant
 * 0xff113311` (debug). With the palette wired, `getColor(59)` = the palette tone, not the debug green.
 */
class Rem68WiringTest {

    @Test
    fun palette_overrides_namedVariable_colorConstant_fallback() {
        Builtins.register()
        val palette = systemAccentPalette()
        val doc = DocumentReader.inflate(RcCorpus.readFixture("corpus/clock.rc"))
        val ctx = RemoteContext().apply { animationEnabled = false }
        // The wiring: host palette seeded BEFORE paint (as RemoteComposeApp does on the per-render ctx).
        ctx.setThemePaletteByName(palette)
        RemoteComposePlayer(ctx).paint(doc, NoOpPaintContext(ctx), surfaceWidth = 500f, surfaceHeight = 500f)

        val accent2_50 = palette["color.system_accent2_50"]!!
        assertEquals(accent2_50, ctx.getColor(59), "id59 (NamedVariable color.system_accent2_50) → palette tone")
        assertNotEquals(0xFF113311.toInt(), ctx.getColor(59), "must NOT be the debug ColorConstant fallback")
        assertTrue((ctx.getColor(59) ushr 24) and 0xFF == 0xFF, "resolved theme color is opaque (visible)")
    }

    @Test
    fun withoutPalette_fallsBackToColorConstant() {
        // Leg-2: with NO palette wired, behaviour is the pre-REM-68 fallback (the debug ColorConstant),
        // proving the palette is the only thing that changes the resolution — nothing else regresses.
        Builtins.register()
        val doc = DocumentReader.inflate(RcCorpus.readFixture("corpus/clock.rc"))
        val ctx = RemoteContext().apply { animationEnabled = false }
        RemoteComposePlayer(ctx).paint(doc, NoOpPaintContext(ctx), surfaceWidth = 500f, surfaceHeight = 500f)
        assertEquals(0xFF113311.toInt(), ctx.getColor(59), "no palette → ColorConstant fallback (unchanged)")
    }
}
