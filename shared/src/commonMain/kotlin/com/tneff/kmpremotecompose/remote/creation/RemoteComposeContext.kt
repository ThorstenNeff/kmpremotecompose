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
import com.tneff.kmpremotecompose.remote.core.operations.Operation

/**
 * Recording target for the creation DSL: an ergonomic wrapper around [RemoteComposeWriter] that
 * pairs the byte-bewiesene L1 writer with a deterministic [IdAllocator] and the active [Profile].
 *
 * **Op-emitter, not encoder (§2).** Every DSL helper on this context — draw* (E2), text/bitmap/
 * matrix/clip (E3), high-level (E4) — terminates in `writer.add(TheOp(operands))`; the bytes are
 * produced by `Operation.write()` which is byte-proven against the upstream oracle. The DSL only
 * shapes inputs, allocates ids, and orders the calls. Byte-correctness therefore reduces to
 * *right ops + right order + right operands/ids*.
 *
 * **External-drive seam for E6.** The Compose-DSL applier (`@RemoteComposable` + `CaptureRemote-`
 * `Document`) will drive this context from outside rather than re-implementing emission. Both
 * [writer] and [ids] are therefore public; `add()` exists as the lowest-level entry point so an
 * external applier can append parsed ops without going through a typed helper. The receiver-lambda
 * lifecycle (see [document]) is the ergonomic path for callers; the public surface is the
 * extensibility path for E6.
 *
 * E1 deliberately ships no draw / text / layout helpers — those are E2–E4. What E1 freezes is the
 * *shape* of the recording target, the lifecycle, the id source, and the platform-services seam.
 */
@RemoteComposeCreationDsl
class RemoteComposeContext(
    val writer: RemoteComposeWriter,
    val profile: Profile,
    val ids: IdAllocator = IdAllocator(),
) {

    /**
     * Negative component-id counter (REM-96 / FC-Layout-Container). Mirrors upstream
     * `RemoteComposeBuffer.mGeneratedComponentId` (`remote-core/.../RemoteComposeBuffer.java:212`):
     * init `-1`, post-decrement → first call returns `-2`, then `-3`, `-4`, ... .
     *
     * **Separate pool from [ids] / [IdAllocator].** Container open ops (`BoxLayout` /
     * `ColumnLayout` / ...) and `LayoutContent` ops use this negative counter for their
     * auto-generated `componentId` slot. User-provided ids pass through via `getComponentId(id)`
     * pass-through (only `id == -1` triggers a decrement).
     */
    private var generatedComponentId: Int = -1

    /**
     * Upstream `getComponentId(int)` equivalent — pass [id] through unless it's `-1`, in which case
     * advance the negative counter and return the new value. See [RemoteComposeBuffer.java:1687-1696].
     * Byte-anchor: see `LayoutContainerHelpersTest.nestedContainers_advanceNegativeCounter`.
     */
    fun resolveComponentId(id: Int): Int {
        if (id != -1) return id
        generatedComponentId -= 1
        return generatedComponentId
    }

    /**
     * Append [operation] to the document. Delegates to [RemoteComposeWriter.add], which fail-closes
     * on opcodes invalid for [Profile.operationsProfiles] (REM-3 gate).
     */
    fun add(operation: Operation) {
        writer.add(operation)
    }

    /** Encoded bytes for the document recorded so far. */
    fun encodeToByteArray(): ByteArray = writer.encodeToByteArray()

    companion object
}
