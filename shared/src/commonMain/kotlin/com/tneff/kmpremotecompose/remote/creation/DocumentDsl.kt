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
package com.tneff.kmpremotecompose.remote.creation

import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeWriter
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.RootContentDescription
import com.tneff.kmpremotecompose.remote.core.operations.TextData

/**
 * Top-level DSL entry: open a document with [width] × [height], record [content] against the
 * [RemoteComposeContext], close, and return the encoded `.rc` bytes.
 *
 * The receiver-lambda *is* the lifecycle — header is stamped by [RemoteComposeWriter]'s `init`,
 * the byte-faithful prolog (REM-85) is emitted automatically (see below), ops are appended inside
 * [content], `encodeToByteArray()` runs on return. Construct-then-mutate and separate `apply { }`
 * forms are intentionally not exposed; the one-call shape keeps callers from forgetting to encode
 * and makes id-allocation deterministic per document.
 *
 * **Auto-form selection (REM-85).** Baseline-profile documents land as flat-form headers (api 6,
 * v1.0.0) to match the upstream corpus oracles byte-for-byte; non-baseline profiles use map-form
 * (api 7) because flat-form cannot encode `DOC_PROFILES`. The flat-form path emits the
 * content-description as a body op pair (`DATA_TEXT(42)` + `ROOT_CONTENT_DESCRIPTION(42)`), mirror-
 * ing upstream's `RemoteComposeWriter.header()`; the map-form path embeds it in the header property
 * table instead. In both cases id 42 is reserved for the content-description (per the E5 id-order
 * reference) when one is provided.
 *
 * For an externally-driven recording (E6 Compose-DSL applier path), construct a
 * [RemoteComposeContext] directly via its public constructor and call [RemoteComposeContext.add] /
 * [RemoteComposeContext.encodeToByteArray] from the applier.
 */
fun document(
    width: Int,
    height: Int,
    profile: Profile = Profile.Baseline,
    contentDescription: String? = null,
    content: RemoteComposeContext.() -> Unit,
): ByteArray {
    val flatForm = profile.operationsProfiles == Operations.PROFILE_BASELINE
    val writer = RemoteComposeWriter(
        width = width,
        height = height,
        profiles = profile.operationsProfiles,
        // Flat-form cannot encode the content-description in the header — it becomes a body op below.
        contentDescription = if (flatForm) null else contentDescription,
        apiLevel = if (flatForm) 6 else 7,
    )
    val context = RemoteComposeContext(writer = writer, profile = profile)
    // REM-141 — empirical-empty-description rule (corpus-grounded against
    // `c_modifier_visibility.rc` / `c_modifier_dynamic_border.rc`): upstream reserves id 42 for the
    // content-description ONLY when the description carries text. An empty string still encodes the
    // `DOC_CONTENT_DESCRIPTION` header property (zero-length STRING in map-form; null skips the
    // property entirely — Bug #2-Lehre, REM-130) but does NOT pull an id. This matters in map-form
    // because the first region-0 allocation lands at id 42 (e.g. the FloatExpression in
    // `c_modifier_visibility.rc`); reserving for an empty string would push the user's first id to
    // 43 and break the byte-anchor.
    if (contentDescription != null && contentDescription.isNotEmpty()) {
        val descId = context.ids.nextId() // pins id 42 to the content-description per the id-order reference
        if (flatForm) {
            context.add(TextData(descId, contentDescription))
            context.add(RootContentDescription(descId))
        }
        // Map-form documents already carry DOC_CONTENT_DESCRIPTION in the header; re-emitting the
        // body ops would double-bind id 42. Reserving the id without emission keeps body ids on the
        // same 43+ track as the flat-form path so a single DSL script encodes identically whichever
        // form the profile selects.
    }
    context.content()
    return context.encodeToByteArray()
}
