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
import com.tneff.kmpremotecompose.remote.creation.textTransform

/**
 * REM-149-S2 — `TEXT_TRANSFORM` (opcode 199 / 0xC7) primitive composable.
 *
 * Mirrors the procedural-DSL `RemoteComposeContext.textTransform(srcId1, start, len, operation):
 * Int` (REM-149-S2, corpus-byte-anchored against `demo_text_transform.rc` ops #0..#4). The
 * Compose-DSL node defers to the byte-proven procedural helper at Phase-B render time, so
 * compose==procedural by construction (TechSpec §0 / W2). The freshly-allocated region-0 text id
 * lands in `slot.id` (REM-141 slot-out pattern) so a downstream consumer composable can read it.
 *
 * **Inputs.** [srcId1] is a raw region-0 text id (an earlier `addText` / Compose-DSL text producer
 * — supply directly or via another `RemoteTextSlot`). [start] / [len] are float positions (W14:
 * may be NaN-encoded variable refs). [operation] is the transform code (upstream `TextTransform`
 * operation constants; empirically observed in the corpus: 1 / 2 / 3 / 4 / 5 for the standard
 * case/Unicode transforms).
 *
 * **NaN-bit fidelity (W14).** Both float positions round-trip via the procedural helper's
 * `WireBuffer.writeFloat`'s `toRawBits()` write path — `Float.fromBits(...)` var-refs preserve
 * their signaling-NaN payloads byte-exact.
 *
 * **Profile gating.** TEXT_TRANSFORM lives in the AndroidX overlay — the surrounding
 * `captureSingleRemoteDocument(...)` must open with a profile carrying it (e.g.
 * `Profile(operationsProfiles = Operations.PROFILE_ANDROIDX, ...)`).
 */
@Composable
fun RemoteTextTransform(
    slot: RemoteTextSlot,
    srcId1: Int,
    start: Float,
    len: Float,
    operation: Int,
) {
    ComposeNode<RemoteTextTransformNode, RemoteComposeApplier>(
        factory = { RemoteTextTransformNode(slot, srcId1, start, len, operation) },
        update = {
            set(slot) { this.slot = it }
            set(srcId1) { this.srcId1 = it }
            set(start) { this.start = it }
            set(len) { this.len = it }
            set(operation) { this.operation = it }
        },
    )
}

internal class RemoteTextTransformNode(
    var slot: RemoteTextSlot,
    var srcId1: Int,
    var start: Float,
    var len: Float,
    var operation: Int,
) : RemoteComposeNode() {
    override fun render(context: RemoteComposeContext) {
        slot.id = context.textTransform(srcId1, start, len, operation)
    }
}
