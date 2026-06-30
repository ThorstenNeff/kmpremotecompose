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
import com.tneff.kmpremotecompose.remote.core.operations.layout.CoreText
import com.tneff.kmpremotecompose.remote.creation.RemoteComposeContext
import com.tneff.kmpremotecompose.remote.creation.coreText

/**
 * REM-149 — `CORE_TEXT` (opcode 239) styled-text-component composable.
 *
 * Mirrors the procedural-DSL `RemoteComposeContext.coreText(textId, params)` (REM-149,
 * corpus-byte-anchored against `c_modifier_align_by_baseline.rc`). The Compose-DSL node defers to
 * the byte-proven procedural helper at Phase-B render time, so compose==procedural by construction
 * (TechSpec §0 / W2).
 *
 * **Inputs.** [textId] references an existing region-0 text id (from a preceding `addText` /
 * `createTextFromFloat` / `textLookup` / `textMerge` — REM-149 is text-styling only, not
 * text-bearing). [params] is the styled-parameter list; each [CoreText.Param] carries a 1-byte
 * TextStyle parameter id (1..26) and raw BE-encoded value bytes. Construct typed params via the
 * `coreTextIntParam` / `coreTextFloatParam` / `coreTextShortParam` / `coreTextByteParam` /
 * `coreTextBoolParam` factories from `:shared` for byte-faithful encoding (Float via
 * `toRawBits()` → W14 NaN-bits preservation for variable refs).
 *
 * **Defensive copy at composition.** Both the [params] list and each `Param.value` ByteArray are
 * frozen via `.copyOf()` at composition time so that a caller mutating either after composition
 * cannot drift the render-time bytes (same Q4-Lock pattern as `RemotePathExpression` —
 * REM-148-S2).
 *
 * **Profile gating.** CORE_TEXT is in the AndroidX overlay — the surrounding
 * `captureSingleRemoteDocument(...)` must open with a profile carrying it (e.g.
 * `Profile(operationsProfiles = Operations.PROFILE_ANDROIDX, ...)`).
 */
@Composable
fun RemoteCoreText(
    textId: Int,
    params: List<CoreText.Param>,
) {
    val frozen = params.map { CoreText.Param(it.id, it.value.copyOf()) }
    ComposeNode<RemoteCoreTextNode, RemoteComposeApplier>(
        factory = { RemoteCoreTextNode(textId, frozen) },
        update = {
            set(textId) { this.textId = it }
            set(frozen) { this.params = it }
        },
    )
}

internal class RemoteCoreTextNode(
    var textId: Int,
    var params: List<CoreText.Param>,
) : RemoteComposeNode() {
    override fun render(context: RemoteComposeContext) {
        context.coreText(textId, params)
    }
}
