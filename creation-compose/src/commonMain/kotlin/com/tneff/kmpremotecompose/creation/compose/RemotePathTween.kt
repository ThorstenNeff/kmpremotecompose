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
import com.tneff.kmpremotecompose.remote.creation.pathTween

/**
 * REM-148 S1 — `PATH_TWEEN` (opcode 158) primitive composable.
 *
 * Mirrors the procedural-DSL `RemoteComposeContext.pathTween(pathId1, pathId2, tween): Int`
 * (REM-148 S1, corpus-byte-anchored against `path_demo_path_tween_demo.rc` op #22 / #23). The
 * Compose-DSL node defers to the byte-proven procedural helper at Phase-B render time, so the
 * Compose path is byte-identical to the procedural path by construction (TechSpec §0 / W2).
 *
 * **Inputs ([pathId1] / [pathId2]) are raw region-0 ids** (typed `Int`, not a slot), reflecting
 * REM-148 S1's scope: PATH_TWEEN is the lone op landing here, so the inputs come from outside
 * the slot infrastructure (procedural setup, future `RemotePathData` composable in a later
 * slice, etc.). A chain — `pathId1 = outerSlot.id` after an earlier `RemotePathTween(outerSlot,
 * ...)` rendered — is the corpus pattern (`path_demo_path_tween_demo.rc` op #23 uses op #22's
 * outId as `pathId1`).
 *
 * **Output ([slot]) follows the REM-141 slot-out pattern.** The freshly-allocated region-0 id
 * lands in `slot.id` during Phase-B render; a downstream consumer composable can then read it.
 * For the S1 byte-anchor test, the slot is allocated but not consumed — the sub-span extraction
 * is opcode-position-based, slot.id participation isn't asserted.
 *
 * **NaN-bit fidelity (W14).** [tween] is `Float`-typed (not `Number`); a NaN-encoded var-ref
 * `Float.fromBits(0xFF80002B.toInt())` (corpus op #22 pattern — refers to ANIMATED_FLOAT id=43)
 * preserves its signaling-NaN payload end-to-end through `WireBuffer.writeFloat`'s `toRawBits()`
 * write path. Coercion through `Number.toFloat()` would canonicalise the NaN and break the
 * Stage-2 byte-anchor.
 */
@Composable
fun RemotePathTween(
    slot: RemotePathSlot,
    pathId1: Int,
    pathId2: Int,
    tween: Float,
) {
    ComposeNode<RemotePathTweenNode, RemoteComposeApplier>(
        factory = { RemotePathTweenNode(slot, pathId1, pathId2, tween) },
        update = {
            set(slot) { this.slot = it }
            set(pathId1) { this.pathId1 = it }
            set(pathId2) { this.pathId2 = it }
            set(tween) { this.tween = it }
        },
    )
}

internal class RemotePathTweenNode(
    var slot: RemotePathSlot,
    var pathId1: Int,
    var pathId2: Int,
    var tween: Float,
) : RemoteComposeNode() {
    override fun render(context: RemoteComposeContext) {
        // Procedural helper allocates the out id, emits PATH_TWEEN, returns the allocated id —
        // the slot picks it up for any later consumer in the same Phase-B walk.
        slot.id = context.pathTween(pathId1, pathId2, tween)
    }
}
