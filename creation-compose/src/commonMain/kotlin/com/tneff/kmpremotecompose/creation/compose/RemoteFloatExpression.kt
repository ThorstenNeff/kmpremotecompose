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
import com.tneff.kmpremotecompose.remote.creation.floatExpression
import com.tneff.kmpremotecompose.remote.wire.WireTypes

/**
 * REM-141 T3 — `ANIMATED_FLOAT` (class `FloatExpression`) primitive composable.
 *
 * Emits an RPN float expression and binds the result id to [slot] so a downstream modifier
 * (`.visibility(slot)`, etc.) can reference it. Mirrors REM-92's procedural-DSL
 * `RemoteComposeContext.floatExpression(vararg Float)` — same RPN vararg semantics, same wire op,
 * same byte-for-byte output (the procedural helper IS the byte-proven path; the composable just
 * routes through it at Phase-B render time).
 *
 * [value] is an RPN expression: literal floats interleaved with NaN-encoded variable refs
 * (`WireTypes.asNan(id)`, `RcExpression.TIME_IN_SEC`, ...) and NaN-encoded operator ids
 * (`RcExpression.ADD/SUB/MUL/...`). NaN raw-bit fidelity is preserved end-to-end —
 * `floatArrayOf(...)` carries the bits unchanged into the procedural helper which writes them
 * verbatim to the wire (W2 byte-watchpoint pinned in the REM-141 S2 byte-anchor).
 *
 * **id-allocation order (W1):** the slot's id is the **next region-0 id** the writer hands out
 * when this composable's node renders. Place the primitive BEFORE any consumer in the tree
 * (Compose source order = render order = id-allocation order, REM-128 W2).
 */
@Composable
fun RemoteFloatExpression(slot: RemoteFloatSlot, vararg value: Float) {
    // ARRAY-COPY at composition: `vararg Float` shares the underlying array with the call site
    // in some target backends; copy to a stable array we own so a caller mutating their input
    // after composition can't drift our render-time bytes.
    val frozen = value.copyOf()
    ComposeNode<RemoteFloatExpressionNode, RemoteComposeApplier>(
        factory = { RemoteFloatExpressionNode(slot, frozen, animation = null) },
        update = {
            set(slot) { this.slot = it }
            set(frozen) { this.value = it }
        },
    )
}

/**
 * REM-141 T3 — `ANIMATED_FLOAT` with optional FloatAnimation. Mirrors the procedural
 * `floatExpression(value: FloatArray, animation: FloatArray?)` overload — animation bytes are
 * round-tripped verbatim (player-side animation execution is Epic-D1, not in scope).
 */
@Composable
fun RemoteFloatExpression(
    slot: RemoteFloatSlot,
    value: FloatArray,
    animation: FloatArray? = null,
) {
    val frozenValue = value.copyOf()
    val frozenAnim = animation?.copyOf()
    ComposeNode<RemoteFloatExpressionNode, RemoteComposeApplier>(
        factory = { RemoteFloatExpressionNode(slot, frozenValue, frozenAnim) },
        update = {
            set(slot) { this.slot = it }
            set(frozenValue) { this.value = it }
            set(frozenAnim) { this.animation = it }
        },
    )
}

internal class RemoteFloatExpressionNode(
    var slot: RemoteFloatSlot,
    var value: FloatArray,
    var animation: FloatArray?,
) : RemoteComposeNode() {
    override fun render(context: RemoteComposeContext) {
        // Procedural helper emits the FloatExpression op and returns a NaN-encoded float whose
        // low bits are the freshly-allocated region-0 id. Unwrap the id and bind it to the slot
        // so the consumer modifier (e.g. .visibility(slot)) can read it during its apply step
        // later in the same Phase-B walk.
        val nanId = context.floatExpression(value, animation)
        slot.id = WireTypes.idFromNan(nanId)
    }
}
