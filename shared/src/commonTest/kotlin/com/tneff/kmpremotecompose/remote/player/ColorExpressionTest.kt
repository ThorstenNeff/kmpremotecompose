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

import com.tneff.kmpremotecompose.remote.core.operations.ColorExpression
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-37 ColorExpression eval — modes load a computed ARGB into the color store (was byte-only → colors
 * via colorId stayed 0 → transparent borders/backgrounds). Verifies the ported color math.
 */
class ColorExpressionTest {

    private fun argb(ctx: RemoteContext, id: Int) = ctx.getColor(id)
    private fun ce(id: Int, p1: Int, p2: Int, p3: Int, p4: Int): ColorExpression =
        ColorExpression(id, p1, p2, p3, p4)

    @Test
    fun argbMode_packsChannels() {
        val ctx = RemoteContext()
        // mode 5 (ARGB), alpha = 512/1024 = 0.5 → 128; red=1, green=0, blue=0.
        val op = ce(1, 5 or (512 shl 16), 1f.toRawBits(), 0f.toRawBits(), 0f.toRawBits())
        op.updateVariables(ctx); op.apply(ctx)
        assertEquals(0x80FF0000.toInt(), argb(ctx, 1), "ARGB(0.5,1,0,0) → 0x80FF0000")
    }

    @Test
    fun hsvMode_redFromHue0() {
        val ctx = RemoteContext()
        // mode 4 (HSV), alpha byte 255; hue=0, sat=1, value=1 → pure red.
        val op = ce(2, 4 or (255 shl 16), 0f.toRawBits(), 1f.toRawBits(), 1f.toRawBits())
        op.updateVariables(ctx); op.apply(ctx)
        assertEquals(0xFFFF0000.toInt(), argb(ctx, 2), "HSV(0,1,1) → red")
    }

    @Test
    fun tweenMode0_atZero_isColor1_literal() {
        val ctx = RemoteContext()
        // mode 0 (color-color), both literal; tween 0 → color1.
        val op = ce(3, 0, 0xFFFF0000.toInt(), 0xFF0000FF.toInt(), 0f.toRawBits())
        op.updateVariables(ctx); op.apply(ctx)
        assertEquals(0xFFFF0000.toInt(), argb(ctx, 3), "tween=0 → color1 (red)")
    }

    @Test
    fun tweenMode1_color1IsColorIdRef() {
        val ctx = RemoteContext()
        ctx.loadColor(99, 0xFF00FF00.toInt()) // the referenced color
        // mode 1 (bit0 set → color1 is a colorId ref to id 99); tween 0 → color1 = getColor(99).
        val op = ce(4, 1, 99, 0xFF0000FF.toInt(), 0f.toRawBits())
        op.updateVariables(ctx); op.apply(ctx)
        assertEquals(0xFF00FF00.toInt(), argb(ctx, 4), "mode1 color1 = getColor(99)")
    }
}
