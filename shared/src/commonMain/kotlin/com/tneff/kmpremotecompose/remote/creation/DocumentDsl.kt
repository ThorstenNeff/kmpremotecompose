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

/**
 * Top-level DSL entry: open a document with [width] × [height], record [content] against the
 * [RemoteComposeContext], close, and return the encoded `.rc` bytes.
 *
 * The receiver-lambda *is* the lifecycle — header is stamped by [RemoteComposeWriter]'s `init`,
 * ops are appended inside [content], `encodeToByteArray()` runs on return. Construct-then-mutate
 * and separate `apply { }` forms are intentionally not exposed; the one-call shape keeps callers
 * from forgetting to encode and makes id-allocation deterministic per document.
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
    val writer = RemoteComposeWriter(
        width = width,
        height = height,
        profiles = profile.operationsProfiles,
        contentDescription = contentDescription,
    )
    val context = RemoteComposeContext(writer = writer, profile = profile)
    context.content()
    return context.encodeToByteArray()
}
