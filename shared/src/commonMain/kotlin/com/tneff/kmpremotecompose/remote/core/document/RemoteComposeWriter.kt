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
package com.tneff.kmpremotecompose.remote.core.document

import com.tneff.kmpremotecompose.remote.core.operations.Header
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * Writer facade skeleton: opens a document with a header and appends operations, enforcing that
 * every emitted operation is valid for the document's declared profiles.
 *
 * The profile gate ([add]) is the concrete form of the writer/reader profile-consistency rule
 * (REM-3 acceptance): if the writer emitted an operation whose opcode is not in the decode map the
 * reader builds from `DOC_PROFILES`, the reader would reject the document with an unknown-opcode
 * error. Failing here at write time turns that latent byte-incompatibility into an immediate,
 * local error. The per-operation `add*` helpers (drawRect, addText, …) land with REM-4.
 */
class RemoteComposeWriter(
    width: Int,
    height: Int,
    val profiles: Int = Operations.PROFILE_BASELINE,
    contentDescription: String? = null,
) {

    private val buffer = WireBuffer()

    /** API level of the documents this writer produces (map-form header ⇒ 7). */
    val apiLevel: Int = Header.versionToApiLevel(Header.MAJOR_VERSION, Header.MINOR_VERSION)

    init {
        val properties = LinkedHashMap<Int, Any>()
        properties[Header.DOC_WIDTH] = width
        properties[Header.DOC_HEIGHT] = height
        if (contentDescription != null) properties[Header.DOC_CONTENT_DESCRIPTION] = contentDescription
        if (profiles != Operations.PROFILE_BASELINE) properties[Header.DOC_PROFILES] = profiles
        Header.fromProperties(properties).write(buffer)
    }

    /**
     * Append [operation], rejecting any opcode not valid under this document's api level + profiles
     * (fail closed). REM-4 operations route their emission through here.
     */
    fun add(operation: Operation) {
        require(Operations.isValid(operation.opcode, apiLevel, profiles)) {
            "opcode ${operation.opcode} (${Operations.name(operation.opcode)}) " +
                "is not valid for profiles=$profiles — would be unreadable"
        }
        operation.write(buffer)
    }

    /** The encoded `.rc` document bytes. */
    fun encodeToByteArray(): ByteArray = buffer.toByteArray()
}
