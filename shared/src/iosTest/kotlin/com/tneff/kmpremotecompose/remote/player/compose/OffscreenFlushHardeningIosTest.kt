/*
 * Copyright 2026 The KmpRemoteCompose Authors
 * Licensed under the Apache License, Version 2.0.
 */
package com.tneff.kmpremotecompose.remote.player.compose

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-63: the REM-60 offscreen flush fires only on `DRAW_TO_BITMAP(0)` (restore). Two producible op
 * sequences bypass that restore and would leave the bitmap store stale:
 *  - **(A) unbracketed switch** `DRAW_TO_BITMAP(A)` → `DRAW_TO_BITMAP(B)` with no restore between → A's
 *    drawn pixels are never mirrored (activeOffscreenId is overwritten).
 *  - **(B) read-while-active** `DRAW_BITMAP_SCALED(A)` while A is still the active target → the read
 *    sees the stale store instead of A's current pixels.
 *
 * Each test drives the sequence through the real [GeometryPaintDelegate] and proves, via the offscreen
 * snapshot in the store ([toPixelMap]), that the flushed bitmap is correct. Without the REM-63 guards
 * these assertions fail (store stays the empty seed), so the hardening is genuinely under test. The
 * bracketed path is asserted unchanged. (iosTest: ImageBitmap/Skia surfaces need Skiko, not headless jvm.)
 */
class OffscreenFlushHardeningIosTest {
    private val A = 1
    private val B = 2
    private val RED = 0xFFFF0000.toInt()
    private val BLUE = 0xFF0000FF.toInt()
    private val GREEN = 0xFF00FF00.toInt()
    private val W = 8

    private fun newDelegate(ctx: RemoteContext): GeometryPaintDelegate {
        ctx.putBitmap(A, ImageBitmap(W, W)) // empty seed targets (allocated by DATA_BITMAP at runtime)
        ctx.putBitmap(B, ImageBitmap(W, W))
        val mainCanvas = Canvas(ImageBitmap(W, W))
        return GeometryPaintDelegate(ctx, mainCanvas, PlayerPaintState())
    }

    private fun centerColor(ctx: RemoteContext, id: Int): Color =
        ctx.getBitmap(id)!!.toPixelMap()[W / 2, W / 2]

    @Test
    fun caseA_unbracketedSwitch_flushesPreviousOffscreen() {
        val ctx = RemoteContext()
        val d = newDelegate(ctx)
        d.drawToBitmap(A, 0, RED) // A offscreen ← red, active = A
        d.drawToBitmap(B, 0, BLUE) // switch to B with NO restore → must flush A first
        // A was flushed on the switch (pre-fix: store stayed the empty seed). B is now active/unflushed,
        // so its store is intentionally not asserted here (it flushes on its own restore/switch).
        assertEquals(Color(RED), centerColor(ctx, A))
    }

    @Test
    fun caseB_readWhileActive_seesCurrentPixels() {
        val ctx = RemoteContext()
        val d = newDelegate(ctx)
        d.drawToBitmap(A, 0, GREEN) // A offscreen ← green, active = A (no restore)
        d.drawBitmap(A, 0f, 0f, W.toFloat(), W.toFloat()) // read A while active → must mirror current pixels
        assertEquals(Color(GREEN), centerColor(ctx, A))
    }

    @Test
    fun bracketedPath_unchanged() {
        val ctx = RemoteContext()
        val d = newDelegate(ctx)
        d.drawToBitmap(A, 0, RED)
        d.drawToBitmap(0, 0, 0) // restore → flush A (the normal REM-60 path)
        assertEquals(Color(RED), centerColor(ctx, A))
    }
}
