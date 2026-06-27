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

import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawText
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawTextAnchored
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-54 — text-draw ops must resolve NaN-encoded variable refs for their anchor/run position (like the
 * geometry ops). Clock/chart number text anchors at computed var-coords (e.g. jclock2 x→id92=250);
 * without resolution the raw NaN reaches the canvas → NaN position → invisible text.
 */
class TextPositionResolveTest {

    private fun captureXY(op: com.tneff.kmpremotecompose.remote.core.operations.Operation, ctx: RemoteContext): Pair<Float, Float> {
        var cx = Float.NaN
        var cy = Float.NaN
        val rec = object : NoOpPaintContext(ctx) {
            override fun drawTextRun(textId: Int, start: Int, end: Int, contextStart: Int, contextEnd: Int, x: Float, y: Float, rtl: Boolean) {
                cx = x; cy = y
            }
        }
        ctx.paintContext = rec
        (op as com.tneff.kmpremotecompose.remote.player.core.PaintOperation).paint(ctx, rec)
        return cx to cy
    }

    @Test
    fun drawTextAnchored_resolvesNaNPositionFromStore() {
        val ctx = RemoteContext()
        ctx.loadFloat(92, 250f)
        ctx.loadFloat(93, 400f)
        // x/y are NaN refs to ids 92/93; panY = NaN sentinel → py keeps ry; panX = 0 (no h-offset with 0 bounds).
        val op = DrawTextAnchored(textId = 5, x = WireTypes.asNan(92), y = WireTypes.asNan(93), panX = 0f, panY = Float.NaN, flags = 0)
        val (px, py) = captureXY(op, ctx)
        assertEquals(250f, px, "anchor x resolved from the store (not raw NaN)")
        assertEquals(400f, py, "anchor y resolved from the store")
    }

    @Test
    fun drawText_resolvesNaNPositionFromStore() {
        val ctx = RemoteContext()
        ctx.loadFloat(92, 120f)
        ctx.loadFloat(93, 60f)
        val op = DrawText(textId = 5, start = 0, end = -1, contextStart = 0, contextEnd = 1, x = WireTypes.asNan(92), y = WireTypes.asNan(93), rtl = false)
        val (px, py) = captureXY(op, ctx)
        assertEquals(120f, px)
        assertEquals(60f, py)
    }
}
