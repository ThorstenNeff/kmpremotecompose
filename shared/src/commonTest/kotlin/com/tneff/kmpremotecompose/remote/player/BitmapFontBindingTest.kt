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

import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeDocument
import com.tneff.kmpremotecompose.remote.core.operations.BitmapFontData
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.TextData
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawBitmapFontText
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * REM-35 Inc2 bitmap-font op binding — headless render-evidence: `DATA_BITMAP_FONT` registers its font
 * into the context during the walk (the DATA-op-must-dispatch rule), and `DrawBitmapFontText` dispatches
 * the `drawBitmapFontText` primitive. The real glyph rendering runs on the iOS gate (Skiko).
 */
class BitmapFontBindingTest {

    private class RecordingBmfContext(context: RemoteContext) : NoOpPaintContext(context) {
        var call: String? = null
        override fun drawBitmapFontText(textId: Int, bitmapFontId: Int, start: Int, end: Int, x: Float, y: Float, glyphSpacing: Float) {
            call = "bmf(t=$textId,f=$bitmapFontId,[$start,$end],x=$x,y=$y,sp=$glyphSpacing)"
        }
    }

    @Test
    fun dataBitmapFont_registers_andDrawBitmapFontText_dispatches() {
        val ctx = RemoteContext()
        val font = BitmapFontData(id = 9, glyphs = listOf(BitmapFontData.Glyph("A", 7, 0, 0, 0, 0, 8, 10)))
        val doc = RemoteComposeDocument(listOf<Operation>(
            TextData(3, "AB"),
            font,
            DrawBitmapFontText(textId = 3, bitmapFontId = 9, start = 0, end = -1, x = 1f, y = 2f, glyphSpacing = 0.5f),
        ))

        val rec = RecordingBmfContext(ctx)
        RemoteComposePlayer(ctx).paint(doc, rec)

        // DATA_BITMAP_FONT registered the font (so the draw can resolve it).
        assertSame(font, ctx.getFromId(9), "DATA_BITMAP_FONT must register its font in the walk")
        // DrawBitmapFontText dispatched the primitive with its fields.
        assertEquals("bmf(t=3,f=9,[0,-1],x=1.0,y=2.0,sp=0.5)", rec.call)
    }
}
