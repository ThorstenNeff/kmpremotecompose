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
import com.tneff.kmpremotecompose.remote.creation.pathExpression

/**
 * REM-148 S2 — `PATH_EXPRESSION` (opcode 193) producer composable.
 *
 * Mirrors the procedural-DSL `RemoteComposeContext.pathExpression(flags, min, max, count,
 * expressionX, expressionY): Int` (REM-148 S2, corpus-byte-anchored against
 * `demo_path_expression_path_test1.rc` op #23). The Compose-DSL node defers to the byte-proven
 * procedural helper at Phase-B render time, so compose==procedural by construction (TechSpec
 * §0 / W2). The freshly-allocated region-0 path id lands in `slot.id` (REM-141 slot-out
 * pattern) so a downstream `drawPath(slot)` / `pathTween(slot, ...)` can read it.
 *
 * **Inputs.** [flags] / [min] / [max] / [count] follow upstream `PathExpression` semantics
 * (`LOOP=0x1`, mode=`flags and 0x6`, `POLAR=0x8`, winding=`(flags and 0x3000000) ushr 24`).
 * [expressionX] / [expressionY] are RPN float-arrays: literal floats interleaved with NaN-encoded
 * variable refs (`WireTypes.asNan(id)`) and NaN-encoded RPN operator ids (`RcExpression.*`).
 *
 * **NaN-bit fidelity (W14, intensive).** Every float — `min` / `max` / `count` and each X/Y
 * element — preserves its raw bits end-to-end through the procedural helper's `WireBuffer.writeFloat`
 * (`toRawBits()`) write path. The composable freezes the input arrays at composition time
 * (`copyOf()`) so a caller mutating them after composition cannot drift the render-time bytes
 * (W2 ID-order pin + Q4 lock: structural-identity stable across recompositions).
 *
 * **Profile gating.** PATH_EXPRESSION is in the AndroidX overlay — the surrounding
 * `captureSingleRemoteDocument(...)` must open with a profile carrying it (e.g.
 * `Profile(operationsProfiles = Operations.PROFILE_ANDROIDX, ...)`).
 */
@Composable
fun RemotePathExpression(
    slot: RemotePathSlot,
    flags: Int,
    min: Float,
    max: Float,
    count: Float,
    expressionX: FloatArray,
    expressionY: FloatArray,
) {
    // Freeze inputs at composition so a caller mutating their FloatArray after composition cannot
    // drift the render-time bytes. Same defensive-copy pattern as RemoteFloatExpression (REM-141).
    val frozenX = expressionX.copyOf()
    val frozenY = expressionY.copyOf()
    ComposeNode<RemotePathExpressionNode, RemoteComposeApplier>(
        factory = { RemotePathExpressionNode(slot, flags, min, max, count, frozenX, frozenY) },
        update = {
            set(slot) { this.slot = it }
            set(flags) { this.flags = it }
            set(min) { this.min = it }
            set(max) { this.max = it }
            set(count) { this.count = it }
            set(frozenX) { this.expressionX = it }
            set(frozenY) { this.expressionY = it }
        },
    )
}

internal class RemotePathExpressionNode(
    var slot: RemotePathSlot,
    var flags: Int,
    var min: Float,
    var max: Float,
    var count: Float,
    var expressionX: FloatArray,
    var expressionY: FloatArray,
) : RemoteComposeNode() {
    override fun render(context: RemoteComposeContext) {
        slot.id = context.pathExpression(flags, min, max, count, expressionX, expressionY)
    }
}
