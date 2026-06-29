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
package com.tneff.kmpremotecompose.creation.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import com.tneff.kmpremotecompose.remote.creation.RemoteComposeContext
import com.tneff.kmpremotecompose.remote.creation.colorExpressionArgb
import com.tneff.kmpremotecompose.remote.creation.colorExpressionArgbById
import com.tneff.kmpremotecompose.remote.creation.colorExpressionHsv
import com.tneff.kmpremotecompose.remote.creation.colorExpressionInterpolate
import com.tneff.kmpremotecompose.remote.creation.colorExpressionInterpolateIdLit
import com.tneff.kmpremotecompose.remote.creation.colorExpressionInterpolateIds
import com.tneff.kmpremotecompose.remote.creation.colorExpressionInterpolateLitId

/**
 * REM-141 T3 — `COLOR_EXPRESSIONS` (class `ColorExpression`) primitive composables. Seven
 * mode-specific composables that mirror the REM-92 procedural-DSL helpers in
 * `ColorExpressionHelpers.kt` 1:1. Each routes through its procedural helper at Phase-B render and
 * binds the returned plain-int colour id to [slot] so a downstream `RemoteModifier.border(...,
 * colorIdSlot)` (or any future colour-id consumer) can reference it.
 *
 * The mode-specific surface (vs a single Composable with a `mode: Int` param) preserves the
 * upstream API shape and gives each call site a self-documenting wire-mode at compile time. Wire
 * modes (param1.lowByte):
 *  - 0/1/2/3 = two-colour interpolate (`bit0`=`c1` is colorId, `bit1`=`c2` is colorId)
 *  - 4 = HSV; 5 = ARGB-float; 6 = ID-ARGB (alpha-by-id).
 *
 * NaN-bit-faithfulness (W2): the `Float` channels for HSV/ARGB modes may carry NaN-encoded
 * variable refs — the procedural helper passes them through via `.toRawBits()` so the wire bytes
 * are bit-exact.
 */

@Composable
fun RemoteColorExpressionInterpolate(
    slot: RemoteColorSlot,
    color1: Int,
    color2: Int,
    tween: Float,
) {
    ColorExpressionEmitNode(slot) { colorExpressionInterpolate(color1, color2, tween) }
}

@Composable
fun RemoteColorExpressionInterpolateIdLit(
    slot: RemoteColorSlot,
    colorId1: Int,
    color2: Int,
    tween: Float,
) {
    ColorExpressionEmitNode(slot) { colorExpressionInterpolateIdLit(colorId1, color2, tween) }
}

@Composable
fun RemoteColorExpressionInterpolateLitId(
    slot: RemoteColorSlot,
    color1: Int,
    colorId2: Int,
    tween: Float,
) {
    ColorExpressionEmitNode(slot) { colorExpressionInterpolateLitId(color1, colorId2, tween) }
}

@Composable
fun RemoteColorExpressionInterpolateIds(
    slot: RemoteColorSlot,
    colorId1: Int,
    colorId2: Int,
    tween: Float,
) {
    ColorExpressionEmitNode(slot) { colorExpressionInterpolateIds(colorId1, colorId2, tween) }
}

@Composable
fun RemoteColorExpressionHsv(
    slot: RemoteColorSlot,
    hue: Float,
    saturation: Float,
    value: Float,
    alpha: Int = 255,
) {
    ColorExpressionEmitNode(slot) { colorExpressionHsv(hue, saturation, value, alpha) }
}

@Composable
fun RemoteColorExpressionArgb(
    slot: RemoteColorSlot,
    alpha: Float,
    red: Float,
    green: Float,
    blue: Float,
) {
    ColorExpressionEmitNode(slot) { colorExpressionArgb(alpha, red, green, blue) }
}

@Composable
fun RemoteColorExpressionArgbById(
    slot: RemoteColorSlot,
    alphaId: Int,
    red: Float,
    green: Float,
    blue: Float,
) {
    ColorExpressionEmitNode(slot) { colorExpressionArgbById(alphaId, red, green, blue) }
}

/**
 * Shared `ComposeNode { … }` factory for all seven [RemoteColorExpression*] composables — accepts
 * a slot + an [emit] lambda that invokes the right `colorExpression*` procedural helper at
 * Phase-B render and returns the freshly allocated plain-int colour id.
 *
 * Lambda equality across recompositions: the same call site produces a different lambda instance
 * each time the composition runs, so `update { set(emit) { … } }` always updates. That is the
 * correct behaviour — if any captured arg changed, the new lambda will emit the new value at next
 * render; if no captured arg changed, the same bytes get emitted (W1: emission is render-time, not
 * composition-time). Compose's optimisation only matters for downstream `update { set(modifier) }`
 * change-detection on **modifier** identity, which goes through the data-class element list (Q4).
 */
@Composable
private fun ColorExpressionEmitNode(
    slot: RemoteColorSlot,
    emit: RemoteComposeContext.() -> Int,
) {
    ComposeNode<RemoteColorExpressionNode, RemoteComposeApplier>(
        factory = { RemoteColorExpressionNode(slot, emit) },
        update = {
            set(slot) { this.slot = it }
            set(emit) { this.emit = it }
        },
    )
}

internal class RemoteColorExpressionNode(
    var slot: RemoteColorSlot,
    var emit: RemoteComposeContext.() -> Int,
) : RemoteComposeNode() {
    override fun render(context: RemoteComposeContext) {
        slot.id = context.emit()
    }
}
