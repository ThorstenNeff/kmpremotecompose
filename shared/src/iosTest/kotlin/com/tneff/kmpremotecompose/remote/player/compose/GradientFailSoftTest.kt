/*
 * Copyright 2026 The KmpRemoteCompose Authors
 * Licensed under the Apache License, Version 2.0.
 */
package com.tneff.kmpremotecompose.remote.player.compose

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * REM-37 belt-and-suspenders: the color/brush consumer must never throw on a malformed/out-of-range
 * gradient (the theme-seed regression's trigger). Degenerate gradients degrade to a solid fill.
 */
class GradientFailSoftTest {
    private val GRADIENT = 11
    private val RED = 0xFFFF0000.toInt()
    private val BLUE = 0xFF0000FF.toInt()

    private fun apply(vararg v: Int): PlayerPaintState =
        PlayerPaintState().also { PaintBundleApplier.applyTo(it, v) } // must not throw

    @Test fun oneColorLinear_degradesToSolid() {
        // GRADIENT type=0(linear), colorLen=1, RED, stopsLen=0, sx,sy,ex,ey,tile
        val s = apply(GRADIENT or (0 shl 16), 1, RED, 0, 0, 0, 0, 0, 0)
        assertNull(s.paint.shader, "1-color gradient → no shader")
        assertEquals(Color(RED), s.paint.color, "→ solid first color")
    }

    @Test fun truncatedGradient_doesNotThrow() {
        // colorLen says 2 but the bundle ends early — over-read must yield 0, never throw.
        apply(GRADIENT or (0 shl 16), 2, RED)
    }

    @Test fun radialRadiusZero_degradesToSolid() {
        // type=1(radial), colorLen=2, RED, BLUE, stopsLen=0, cx,cy, radius=0, tile
        val s = apply(GRADIENT or (1 shl 16), 2, RED, BLUE, 0, 0, 0, 0, 0)
        assertNull(s.paint.shader, "radius 0 → no shader")
        assertEquals(Color(RED), s.paint.color)
    }

    @Test fun wellFormedLinear_setsShader() {
        val s = apply(GRADIENT or (0 shl 16), 2, RED, BLUE, 0, 0, 0, 100, 100, 0)
        assertNotNull(s.paint.shader, "2-color linear → shader set")
    }
}
