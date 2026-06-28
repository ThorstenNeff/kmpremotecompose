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

import com.tneff.kmpremotecompose.remote.core.operations.ColorConstant
import com.tneff.kmpremotecompose.remote.core.operations.ColorExpression
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.VariableSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-61 — `ColorConstant` (COLOR_CONSTANT) was byte-only (`: Operation`) → its `apply` never ran →
 * `loadColor` never called → `getColor(colorId)` stayed 0 (transparent). Result: the dark clock face/BG
 * (`DRAW_ROUND_RECT`) drew full-size but invisible (white-on-white). Binding it as a `VariableSupport`
 * producer (mirrors upstream `ColorConstant.apply` → `loadColor`) makes the document's declared colors
 * resolve, including the blend refs that `ColorExpression` reads via `getColor`.
 */
class ColorConstantBindingTest {

    @Test
    fun isVariableSupport() {
        assertTrue(ColorConstant(1, 0xFF112233.toInt()) is VariableSupport, "ColorConstant must dispatch in Phase A")
    }

    @Test
    fun apply_loadsDeclaredColor() {
        val ctx = RemoteContext()
        assertEquals(0, ctx.getColor(82), "unset colorId is the fail-soft transparent default")
        ColorConstant(82, 0xFFACB2F3.toInt()).apply(ctx)
        assertEquals(0xFFACB2F3.toInt(), ctx.getColor(82), "apply loads the declared ARGB under colorId")
    }

    @Test
    fun unrelatedColorIdsStayZero() {
        // A doc with COLOR_CONSTANT for id 77 must not synthesize values for ids it never declares
        // (the trap that forced the colorId-auto-seed revert). Only the declared id resolves.
        val ctx = RemoteContext()
        ColorConstant(77, 0xFF00FFFF.toInt()).apply(ctx)
        assertEquals(0xFF00FFFF.toInt(), ctx.getColor(77))
        assertEquals(0, ctx.getColor(1), "undeclared id stays 0 (no global seeding)")
    }

    @Test
    fun feedsColorExpressionBlendRef() {
        // The digital_clock1 BG chain: COLOR_CONSTANT id77/id78 are the blend leaves; COLOR_EXPRESSIONS
        // id80 = mode-3 tween reading getColor(77)/getColor(78). With the constants unbound the blend
        // collapses to 0; bound (in document order, as Phase A walks) it resolves the real color.
        val ctx = RemoteContext()
        ColorConstant(77, 0xFFFF0000.toInt()).apply(ctx) // color1 leaf
        ColorConstant(78, 0xFF0000FF.toInt()).apply(ctx) // color2 leaf
        // mode 3 (bit0|bit1 → both refs), tween 0 → color1 = getColor(77).
        val expr = ColorExpression(80, 3, 77, 78, 0f.toRawBits())
        expr.updateVariables(ctx); expr.apply(ctx)
        assertEquals(0xFFFF0000.toInt(), ctx.getColor(80), "blend leaf resolves via bound ColorConstant")
    }
}
