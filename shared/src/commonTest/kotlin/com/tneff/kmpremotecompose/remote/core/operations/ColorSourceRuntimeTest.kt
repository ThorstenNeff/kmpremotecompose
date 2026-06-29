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
package com.tneff.kmpremotecompose.remote.core.operations

import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeDocument
import com.tneff.kmpremotecompose.remote.player.NoOpPaintContext
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-131 — additive color-source runtime: [Theme.apply], [ColorTheme.apply] and [ColorAttribute.paint].
 * These ops were wire-only containers (skipped by the player's variable phase / paint phase) so their
 * computed value never reached the store and consumers read the fail-soft default. The tests drive the
 * ops directly against a real [RemoteContext] and assert the produced color / channel float, plus the
 * two ordering properties the gate calls out. §2 byte-conformance (wire/equals/hashCode untouched) is
 * covered by the breadth/conformance suite.
 */
class ColorSourceRuntimeTest {

    // Channel type codes (verbatim from ColorAttribute.* — private there, restated for the test).
    private val HUE = 0
    private val SATURATION = 1
    private val BRIGHTNESS = 2
    private val RED = 3
    private val GREEN = 4
    private val BLUE = 5
    private val ALPHA = 6

    private fun colorTheme() = ColorTheme(
        id = 10, groupId = 0,
        lightMode = 0, darkMode = 0, // upstream group *indices* — unused without a host color group
        lightModeFallback = 0xFF112233.toInt(),
        darkModeFallback = 0xFF445566.toInt(),
    )

    // --- Theme op -------------------------------------------------------------------------------

    @Test fun theme_apply_publishesDocTheme() {
        val ctx = RemoteContext()
        assertEquals(Theme.UNSPECIFIED, ctx.getTheme(), "default doc theme")
        Theme(Theme.DARK).apply(ctx)
        assertEquals(Theme.DARK, ctx.getTheme())
    }

    // --- ColorTheme op --------------------------------------------------------------------------

    @Test fun colorTheme_apply_defaultThemeIsLight_loadsLightFallback() {
        val ctx = RemoteContext()
        assertEquals(Theme.LIGHT, ctx.getPaintTheme(), "default paint theme is LIGHT")
        colorTheme().apply(ctx)
        assertEquals(0xFF112233.toInt(), ctx.getColor(10))
    }

    @Test fun colorTheme_apply_darkPaintTheme_loadsDarkFallback() {
        val ctx = RemoteContext()
        ctx.setPaintTheme(Theme.DARK)
        colorTheme().apply(ctx)
        assertEquals(0xFF445566.toInt(), ctx.getColor(10))
    }

    @Test fun colorTheme_isDrivenByPaintThemeNotTheThemeOp() {
        // Corrected scoping finding: ColorTheme reads getPaintTheme(), NOT the in-doc Theme op (getTheme).
        // color_theme.rc carries COLOR_THEME with no THEME op, so a Theme op is NOT a prerequisite.
        val ctx = RemoteContext()
        Theme(Theme.DARK).apply(ctx) // sets docTheme = DARK, but paint theme stays the default LIGHT
        colorTheme().apply(ctx)
        assertEquals(0xFF112233.toInt(), ctx.getColor(10), "still the LIGHT fallback — Theme op does not gate it")
    }

    // --- ColorAttribute op ----------------------------------------------------------------------

    @Test fun colorAttribute_paint_extractsRgbaChannels_afterSourceLoaded() {
        val ctx = RemoteContext()
        val paint = NoOpPaintContext(ctx)
        ctx.loadColor(5, 0xFF8040C0.toInt()) // A=255 R=128 G=64 B=192 — the source color must exist first
        ColorAttribute(id = 100, colorId = 5, type = RED).paint(ctx, paint)
        ColorAttribute(id = 101, colorId = 5, type = GREEN).paint(ctx, paint)
        ColorAttribute(id = 102, colorId = 5, type = BLUE).paint(ctx, paint)
        ColorAttribute(id = 103, colorId = 5, type = ALPHA).paint(ctx, paint)
        assertEquals(128f / 255f, ctx.getFloat(100))
        assertEquals(64f / 255f, ctx.getFloat(101))
        assertEquals(192f / 255f, ctx.getFloat(102))
        assertEquals(1f, ctx.getFloat(103))
    }

    @Test fun colorAttribute_paint_extractsHsvChannels() {
        val ctx = RemoteContext()
        val paint = NoOpPaintContext(ctx)
        ctx.loadColor(5, 0xFFFF0000.toInt()) // pure red → hue 0, saturation 1, brightness 1
        ColorAttribute(id = 100, colorId = 5, type = HUE).paint(ctx, paint)
        ColorAttribute(id = 101, colorId = 5, type = SATURATION).paint(ctx, paint)
        ColorAttribute(id = 102, colorId = 5, type = BRIGHTNESS).paint(ctx, paint)
        assertEquals(0f, ctx.getFloat(100))
        assertEquals(1f, ctx.getFloat(101))
        assertEquals(1f, ctx.getFloat(102))
    }

    @Test fun colorAttribute_paint_unsetSource_failsSoftToZeroChannels() {
        // No source color loaded → getColor returns 0 (transparent) → every channel is 0. Fail-soft, no throw.
        val ctx = RemoteContext()
        val paint = NoOpPaintContext(ctx)
        ColorAttribute(id = 100, colorId = 5, type = RED).paint(ctx, paint)
        ColorAttribute(id = 101, colorId = 5, type = ALPHA).paint(ctx, paint)
        assertEquals(0f, ctx.getFloat(100))
        assertEquals(0f, ctx.getFloat(101))
    }

    // --- end-to-end: the ops must actually fire in the real player walk (not just direct calls) -----

    @Test fun colorTheme_firesInPlayerVariablePhase() {
        // Proves COLOR_THEME is reached by the player's variable phase (op is VariableSupport) — the exact
        // gap that left the store unset before REM-131. No Theme op present (mirrors color_theme.rc).
        val ctx = RemoteContext()
        val doc = RemoteComposeDocument(listOf(colorTheme()))
        RemoteComposePlayer(ctx).paint(doc, NoOpPaintContext(ctx))
        assertEquals(0xFF112233.toInt(), ctx.getColor(10), "ColorTheme.apply ran in the player walk")
    }

    @Test fun colorAttribute_firesInPlayerPaintPhase_afterSourceColorInVariablePhase() {
        // Full ordering through the real pipeline: ColorConstant (variable phase) loads the source color,
        // ColorAttribute (paint phase) reads it and publishes the channel. Variable-phase-before-paint-phase
        // is exactly the source-load-before-extract ordering the gate calls out.
        val ctx = RemoteContext()
        val doc = RemoteComposeDocument(
            listOf(
                ColorConstant(colorId = 5, color = 0xFF8040C0.toInt()),
                ColorAttribute(id = 100, colorId = 5, type = RED),
            ),
        )
        RemoteComposePlayer(ctx).paint(doc, NoOpPaintContext(ctx))
        assertEquals(128f / 255f, ctx.getFloat(100), "ColorAttribute extracted RED from the source color")
    }
}
