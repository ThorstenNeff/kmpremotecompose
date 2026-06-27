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

import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawBitmapFontText
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-42 — bitmap-font run position. (1) the op resolves NaN var-coords (REM-54-class). The glyph
 * top-down y-blit fix (`y + marginTop`, was `y - bitmapHeight + marginTop` → off-screen) lives in
 * [com.tneff.kmpremotecompose.remote.player.compose.ComposeTextRenderer] and is source-grounded against
 * upstream `DrawBitmapFontText.paint` + visually verified by the render sweep.
 */
class BitmapFontPositionTest {

    @Test
    fun drawBitmapFontText_resolvesNaNPositionFromStore() {
        val ctx = RemoteContext()
        ctx.loadFloat(92, 100f)
        ctx.loadFloat(93, 50f)
        var cx = Float.NaN
        var cy = Float.NaN
        val rec = object : NoOpPaintContext(ctx) {
            override fun drawBitmapFontText(textId: Int, bitmapFontId: Int, start: Int, end: Int, x: Float, y: Float, glyphSpacing: Float) {
                cx = x; cy = y
            }
        }
        ctx.paintContext = rec
        DrawBitmapFontText(textId = 5, bitmapFontId = 149, start = 0, end = -1, x = WireTypes.asNan(92), y = WireTypes.asNan(93), glyphSpacing = 0f)
            .paint(ctx, rec)
        assertEquals(100f, cx, "bitmap-font run x resolved from the store")
        assertEquals(50f, cy, "bitmap-font run y resolved from the store")
    }
}
