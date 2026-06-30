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

import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawTextAnchored
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-160 — headless proof of the `DRAW_TEXT_ANCHOR` overflow policy in **all** directions (REM-156 only
 * handled right-overflow with px ≥ 0; centered/left px < 0 silently skipped the ellipsis). A recording
 * fake with a stubbed text size + seeded `ID_WINDOW_WIDTH`/`ID_WINDOW_HEIGHT` pins which branch fires:
 * right-only (unchanged from REM-156, trailing), left-only (new, leading-ellipsis, right edge kept),
 * both-sides/centered (fill window, trailing), and the fully-off-screen vertical baseline clamp.
 */
class Rem160AnchorOverflowDirectionsTest {

    private class RecordingPaint(context: RemoteContext, private val textWidth: Float, private val height: Float) :
        NoOpPaintContext(context) {
        var plainCalls = 0
        var clippedCalls = 0
        var lastX = Float.NaN
        var lastY = Float.NaN
        var lastMaxWidth = Float.NaN
        var lastLeading = false

        override fun getTextBounds(textId: Int, start: Int, end: Int, flags: Int, bounds: FloatArray) {
            bounds[0] = 0f; bounds[1] = -height; bounds[2] = textWidth; bounds[3] = 0f
        }

        override fun drawTextRun(
            textId: Int, start: Int, end: Int, contextStart: Int, contextEnd: Int,
            x: Float, y: Float, rtl: Boolean,
        ) {
            plainCalls++; lastX = x; lastY = y
        }

        override fun drawTextRunClipped(
            textId: Int, start: Int, end: Int, x: Float, y: Float, rtl: Boolean, maxWidth: Float,
            leadingEllipsis: Boolean,
        ) {
            clippedCalls++; lastX = x; lastY = y; lastMaxWidth = maxWidth; lastLeading = leadingEllipsis
        }
    }

    private fun ctx(windowWidth: Float, windowHeight: Float = 1000f): RemoteContext = RemoteContext().also {
        it.loadFloat(RemoteContext.ID_WINDOW_WIDTH, windowWidth)
        it.loadFloat(RemoteContext.ID_WINDOW_HEIGHT, windowHeight)
    }

    @Test
    fun rightOverflowOnly_unchangedFromRem156_trailing() {
        // Left anchor (panX=-1) at x=0 ⇒ px=0; textWidth 500 > window 300 → right overflow only.
        val c = ctx(windowWidth = 300f)
        val p = RecordingPaint(c, textWidth = 500f, height = 20f)
        DrawTextAnchored(textId = 1, x = 0f, y = 100f, panX = -1f, panY = Float.NaN, flags = 0).paint(c, p)
        assertEquals(1, p.clippedCalls); assertEquals(0, p.plainCalls)
        assertEquals(0f, p.lastX, 0.001f, "keeps left edge px=0 (REM-156)")
        assertEquals(300f, p.lastMaxWidth, 0.001f, "maxWidth = windowWidth - px")
        assertTrue(!p.lastLeading, "right overflow → trailing ellipsis")
    }

    @Test
    fun leftOverflowOnly_new_leadingEllipsis_keepsRightEdge() {
        // Right anchor (panX=+1) at x=250 ⇒ px = 250 - 400 = -150 (<0); px+tw = 250 ≤ window 1000.
        val c = ctx(windowWidth = 1000f)
        val p = RecordingPaint(c, textWidth = 400f, height = 20f)
        DrawTextAnchored(textId = 1, x = 250f, y = 100f, panX = 1f, panY = Float.NaN, flags = 0).paint(c, p)
        assertEquals(1, p.clippedCalls); assertEquals(0, p.plainCalls)
        assertEquals(0f, p.lastX, 0.001f, "drawn from the left edge 0")
        assertEquals(250f, p.lastMaxWidth, 0.001f, "maxWidth = px + textWidth (right edge kept)")
        assertTrue(p.lastLeading, "left overflow → LEADING ellipsis")
    }

    @Test
    fun centeredBothSides_fillsWindow_trailing() {
        // Centered (panX=0) at x=150 ⇒ px = 150 - 250 = -100 (<0); px+tw = 400 > window 300 → both.
        val c = ctx(windowWidth = 300f)
        val p = RecordingPaint(c, textWidth = 500f, height = 20f)
        DrawTextAnchored(textId = 1, x = 150f, y = 100f, panX = 0f, panY = Float.NaN, flags = 0).paint(c, p)
        assertEquals(1, p.clippedCalls); assertEquals(0, p.plainCalls)
        assertEquals(0f, p.lastX, 0.001f, "fills from left edge")
        assertEquals(300f, p.lastMaxWidth, 0.001f, "maxWidth = full window")
        assertTrue(!p.lastLeading, "both-sides overflow → trailing ellipsis (keep start)")
    }

    @Test
    fun fits_plainDraw_unchanged() {
        val c = ctx(windowWidth = 300f)
        val p = RecordingPaint(c, textWidth = 100f, height = 20f)
        DrawTextAnchored(textId = 1, x = 150f, y = 100f, panX = 0f, panY = Float.NaN, flags = 0).paint(c, p)
        assertEquals(1, p.plainCalls); assertEquals(0, p.clippedCalls)
    }

    @Test
    fun verticalClamp_fullyAboveBaseline_nudgedIntoView() {
        // panY=NaN ⇒ py = y = -50; band = [py-20, py] = [-70,-50], fully above the surface → clamp.
        val c = ctx(windowWidth = 300f, windowHeight = 400f)
        val p = RecordingPaint(c, textWidth = 50f, height = 20f) // horizontally fits → plain draw
        DrawTextAnchored(textId = 1, x = 0f, y = -50f, panX = -1f, panY = Float.NaN, flags = 0).paint(c, p)
        assertEquals(1, p.plainCalls)
        assertEquals(0f, p.lastY, 0.001f, "fully-above line clamped so its bottom edge reaches y=0")
    }

    @Test
    fun verticalClamp_visibleBaseline_unchanged() {
        val c = ctx(windowWidth = 300f, windowHeight = 400f)
        val p = RecordingPaint(c, textWidth = 50f, height = 20f)
        DrawTextAnchored(textId = 1, x = 0f, y = 100f, panX = -1f, panY = Float.NaN, flags = 0).paint(c, p)
        assertEquals(1, p.plainCalls)
        assertEquals(100f, p.lastY, 0.001f, "an on-screen baseline is left untouched (no golden churn)")
    }
}
