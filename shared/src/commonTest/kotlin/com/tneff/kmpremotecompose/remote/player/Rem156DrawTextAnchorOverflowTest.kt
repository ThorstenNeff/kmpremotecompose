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

/**
 * REM-156 — headless proof of the `DRAW_TEXT_ANCHOR` **overflow decision** (audit-P1: long anchored text
 * ran off the surface edge with no ellipsis). Uses a recording [NoOpPaintContext] with a stubbed text
 * width + a seeded `ID_WINDOW_WIDTH` to assert exactly when the op routes to the clipped/ellipsize path
 * vs the unchanged plain draw, and that the computed `maxWidth` = (doc-right − left-edge). The *visual*
 * ellipsis correctness is test-3's `long_wrap_latin` render-sweep; this pins the branch logic precisely.
 */
class Rem156DrawTextAnchorOverflowTest {

    private class RecordingPaint(context: RemoteContext, private val textWidth: Float) :
        NoOpPaintContext(context) {
        var plainCalls = 0
        var clippedCalls = 0
        var lastMaxWidth = Float.NaN
        var lastX = Float.NaN

        override fun getTextBounds(textId: Int, start: Int, end: Int, flags: Int, bounds: FloatArray) {
            bounds[0] = 0f; bounds[1] = 0f; bounds[2] = textWidth; bounds[3] = 20f
        }

        override fun drawTextRun(
            textId: Int, start: Int, end: Int, contextStart: Int, contextEnd: Int,
            x: Float, y: Float, rtl: Boolean,
        ) {
            plainCalls++; lastX = x
        }

        override fun drawTextRunClipped(
            textId: Int, start: Int, end: Int, x: Float, y: Float, rtl: Boolean, maxWidth: Float,
        ) {
            clippedCalls++; lastMaxWidth = maxWidth; lastX = x
        }
    }

    private fun context(windowWidth: Float?): RemoteContext = RemoteContext().also {
        if (windowWidth != null) it.loadFloat(RemoteContext.ID_WINDOW_WIDTH, windowWidth)
    }

    // Left-anchored (panX = -1, panY = NaN) ⇒ left edge px = x = 0; width comes from the stub.
    private fun leftAnchor() =
        DrawTextAnchored(textId = TEXT_ID, x = 0f, y = 0f, panX = -1f, panY = Float.NaN, flags = 0)

    @Test
    fun overflow_routesToClippedDraw_withRemainingWidth() {
        val ctx = context(windowWidth = 300f)
        val paint = RecordingPaint(ctx, textWidth = 500f) // 0 + 500 > 300 ⇒ overflows the right edge
        leftAnchor().paint(ctx, paint)
        assertEquals(1, paint.clippedCalls, "overflowing anchored text must take the ellipsize path")
        assertEquals(0, paint.plainCalls, "and must NOT also take the plain path")
        assertEquals(300f, paint.lastMaxWidth, 0.001f, "maxWidth = doc-right (300) − left edge (0)")
        assertEquals(0f, paint.lastX, 0.001f, "left edge stays anchored at px=0")
    }

    @Test
    fun fits_keepsPlainDraw_goldenSafe() {
        val ctx = context(windowWidth = 300f)
        val paint = RecordingPaint(ctx, textWidth = 200f) // 0 + 200 ≤ 300 ⇒ fits
        leftAnchor().paint(ctx, paint)
        assertEquals(1, paint.plainCalls, "fitting text keeps the unchanged plain draw (no golden shift)")
        assertEquals(0, paint.clippedCalls)
    }

    @Test
    fun noWindowWidthSeeded_policyIsInert() {
        val ctx = context(windowWidth = null) // getFloat(ID_WINDOW_WIDTH) → 0 ⇒ overflow logic skipped
        val paint = RecordingPaint(ctx, textWidth = 9999f)
        leftAnchor().paint(ctx, paint)
        assertEquals(1, paint.plainCalls, "without a seeded window width the policy is inert (no regression)")
        assertEquals(0, paint.clippedCalls)
    }

    private companion object {
        const val TEXT_ID = 7
    }
}
