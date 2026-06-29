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
    // property entirely — Bug #2-Lehre, REM-130) but does NOT pull an id.
    //
    // REM-146 (REM-141 follow-up, W12 empirical verify against `c_modifier_on_touch_down.rc`):
    // the reservation is *also* form-conditional. In **flat-form** (api=6, PROFILE_BASELINE) the
    // description is encoded as two body ops (`DATA_TEXT(42)` + `ROOT_CONTENT_DESCRIPTION(42)`) that
    // reference id 42 — the reservation IS required there (REM-128 procedure_simple2 oracle relies
    // on it). In **map-form** (api=7) the description lives in header property 9 (set by the writer
    // constructor); no body op references id 42 → the reservation is **vestigial** and was an
    // earlier "cross-form determinism" assumption (REM-141 commit), which the corpus refutes:
    // `c_modifier_on_touch_down.rc` (PROFILE_ANDROIDX|EXPERIMENTAL, non-empty
    // contentDescription="DemoModifierOnTouchDown") starts the body with `DATA_INT id=42`, meaning
    // upstream does NOT reserve in map-form. Gating on `flatForm` aligns the procedural DSL with
    // the corpus oracle.
    if (contentDescription != null && contentDescription.isNotEmpty() && flatForm) {
        val descId = context.ids.nextId() // pins id 42 to the content-description (flat-form ONLY)
        context.add(TextData(descId, contentDescription))
        context.add(RootContentDescription(descId))
    }
    // Map-form (api=7): description was already written into the header by the writer constructor
    // via `contentDescription = … apiLevel = 7`. No body emission, no id reservation needed —
    // the first user `ids.nextId()` correctly returns 42, matching `c_modifier_on_touch_*.rc`.
    context.content()
    return context.encodeToByteArray()
}
