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

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * REM-158 — proves on the **real CMP/Skiko** backend that a resolved `TYPEFACE` family is actually applied
 * to the text run (not just decoded): measuring the same string with a distinct resolved family yields a
 * different width than the default. Uses [FontFamily.Monospace] as a deterministic distinct proxy (a
 * proportional string measures differently under monospace) — the *bundled DancingScript visual* proof is
 * the test-3 render sweep. Also pins that an unresolved/default typeface leaves the measurement unchanged
 * (the golden-safe path for non-font-setting docs).
 */
class Rem158TypefaceApplyIosTest {

    private val text = "Wagmil proportional"

    private fun measuredWidth(resolver: NamedFontResolver?, name: String?, enumId: Int): Float {
        val ctx = RemoteContext().also { it.setDensity(2f); it.putText(TEXT_ID, text) }
        val ppc = ComposePaintContext(
            ctx, Canvas(ImageBitmap(256, 64)),
            fontFamilyResolver = createFontFamilyResolver(),
            namedFontResolver = resolver,
        )
        ppc.paintState.typefaceName = name
        ppc.paintState.typefaceId = enumId
        val b = FloatArray(4)
        ppc.getTextBounds(TEXT_ID, 0, -1, 0, b)
        return b[2] - b[0]
    }

    @Test
    fun resolvedNamedFamily_changesMeasuredWidth() {
        val resolver = NamedFontResolver(named = mapOf(NamedFontResolver.KEY_CURSIVE to FontFamily.Monospace))
        val mono = measuredWidth(resolver, name = "cursive", enumId = 0)
        val default = measuredWidth(resolver = null, name = null, enumId = 0)
        assertTrue(
            abs(mono - default) > 0.5f,
            "applying a resolved named family (monospace proxy) must change the measured width (was mono=$mono default=$default)",
        )
    }

    @Test
    fun resolvedEnumFamily_changesMeasuredWidth() {
        val resolver = NamedFontResolver()
        val mono = measuredWidth(resolver, name = null, enumId = NamedFontResolver.FONT_MONOSPACE)
        val default = measuredWidth(resolver, name = null, enumId = NamedFontResolver.FONT_DEFAULT)
        assertTrue(
            abs(mono - default) > 0.5f,
            "the monospace enum must change the measured width vs default (was mono=$mono default=$default)",
        )
    }

    @Test
    fun noResolver_leavesMeasurementUnchanged() {
        // Two measures with no resolver must be identical regardless of typeface fields (golden-safe path).
        val a = measuredWidth(resolver = null, name = "cursive", enumId = NamedFontResolver.FONT_MONOSPACE)
        val b = measuredWidth(resolver = null, name = null, enumId = NamedFontResolver.FONT_DEFAULT)
        assertTrue(abs(a - b) < 0.01f, "without a resolver the typeface fields are inert (no shift)")
    }

    private companion object {
        const val TEXT_ID = 7
    }
}
