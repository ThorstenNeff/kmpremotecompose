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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.tneff.kmpremotecompose.remote.player.compose.ComposeTextRenderer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-32 paint-state seam (proposal A) **read-side**, headless: `deriveTextStyle` is pure (no font
 * backend), so it runs on `jvm()` — unlike the full renderer (Skiko). Verifies a [com.tneff.kmpremotecompose.remote.player.compose.PlayerPaintState]'s
 * color + pixel size map into the [TextStyle] the renderer will measure with, once dev-2's real state
 * populates it. The renderer↔state wiring on the real backend is covered in the iOS gate.
 */
class TextPaintStateReadSideTest {

    @Test
    fun deriveTextStyle_mapsColorAndPxSizeIntoStyle() {
        val base = TextStyle(color = Color.Black, fontSize = 16.sp)
        // textSizePx 24 at density 2.0 → 12sp (px / density). fontStyle/fontWeight default (normal).
        val style = ComposeTextRenderer.deriveTextStyle(Color.Red, 24f, fontStyle = 0, fontWeight = 0, density = 2f, base = base)
        assertEquals(Color.Red, style.color)
        assertEquals(with(Density(2f)) { 24f.toSp() }, style.fontSize)
        assertEquals(12.sp, style.fontSize)
        assertEquals(androidx.compose.ui.text.font.FontStyle.Normal, style.fontStyle)
    }

    @Test
    fun deriveTextStyle_mapsItalicAndWeight() {
        val base = TextStyle(color = Color.Black, fontSize = 16.sp)
        // "Italic Blue": fontStyle=1 (italic), fontWeight=700 (bold).
        val style = ComposeTextRenderer.deriveTextStyle(Color.Blue, 0f, fontStyle = 1, fontWeight = 700, density = 2f, base = base)
        assertEquals(androidx.compose.ui.text.font.FontStyle.Italic, style.fontStyle)
        assertEquals(androidx.compose.ui.text.font.FontWeight(700), style.fontWeight)
        assertEquals(Color.Blue, style.color)
    }

    @Test
    fun deriveTextStyle_keepsBaseSizeWhenPxUnset() {
        val base = TextStyle(color = Color.Black, fontSize = 16.sp)
        val style = ComposeTextRenderer.deriveTextStyle(Color.Blue, 0f, fontStyle = 0, fontWeight = 0, density = 3f, base = base)
        assertEquals(Color.Blue, style.color)
        assertEquals(16.sp, style.fontSize, "textSizePx<=0 keeps the base size")
    }
}
