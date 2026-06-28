/*
 * Copyright 2026 The KmpRemoteCompose Authors
 * Licensed under the Apache License, Version 2.0.
 */
package com.tneff.kmpremotecompose.remote.player.compose

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * REM-65/REM-70 render parity. The ONLY real degenerate divergence is the circle: android.graphics +
 * CMP-Canvas skip a non-positive radius, but Skiko paints abs(r) (flow_control r=-240) → guarded.
 *
 * Inverted rects are NOT a divergence: CMP-Canvas normalizes and PAINTS them on BOTH platforms (the
 * Android reference renders heart_rate_timeline's inverted-rect bands), so guarding them over-skipped
 * legitimate shapes (heart_rate regression). The rect-family guards were reverted; these tests lock in
 * that inverted rect/oval/roundRect still paint. (iosTest: Skia toPixelMap, not headless jvm.)
 */
class RenderParityIosTest {
    private val W = 8

    /** Render [block] onto a white WxW bitmap with a red Fill paint; return whether any red pixel landed. */
    private fun paints(block: (GeometryPaintDelegate) -> Unit): Boolean {
        val target = ImageBitmap(W, W)
        val canvas = Canvas(target)
        canvas.drawRect(0f, 0f, W.toFloat(), W.toFloat(), Paint().apply { color = Color.White; blendMode = BlendMode.Src })
        val ps = PlayerPaintState().apply { paint.color = Color.Red }
        block(GeometryPaintDelegate(RemoteContext(), canvas, ps))
        val px = target.toPixelMap()
        for (y in 0 until W) for (x in 0 until W) if (px[x, y] == Color.Red) return true
        return false
    }

    @Test fun circle_negativeRadius_skipped() {
        assertFalse(paints { it.drawCircle(W / 2f, W / 2f, -3f) }, "r<0 must paint nothing")
        assertFalse(paints { it.drawCircle(W / 2f, W / 2f, 0f) }, "r==0 must paint nothing")
        assertTrue(paints { it.drawCircle(W / 2f, W / 2f, W / 3f) }, "valid radius must paint")
    }

    // heart_rate_timeline regression: an inverted-bounds rect is a legitimate band CMP normalizes+paints.
    @Test fun rect_inverted_stillPaints() {
        assertTrue(paints { it.drawRect(W.toFloat(), W.toFloat(), 1f, 1f) }, "inverted rect must still paint (CMP normalizes)")
        assertTrue(paints { it.drawRect(1f, 1f, W - 1f, W - 1f) }, "valid rect must paint")
    }

    @Test fun oval_inverted_stillPaints() {
        assertTrue(paints { it.drawOval(W.toFloat(), W.toFloat(), 1f, 1f) }, "inverted oval must still paint (CMP normalizes)")
        assertTrue(paints { it.drawOval(1f, 1f, W - 1f, W - 1f) }, "valid oval must paint")
    }

    @Test fun roundRect_inverted_stillPaints() {
        assertTrue(paints { it.drawRoundRect(W.toFloat(), W.toFloat(), 1f, 1f, 2f, 2f) }, "inverted roundRect must still paint (CMP normalizes)")
        assertTrue(paints { it.drawRoundRect(1f, 1f, W - 1f, W - 1f, 2f, 2f) }, "valid roundRect must paint")
    }
}
