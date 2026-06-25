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
package com.tneff.kmpremotecompose.remote.core.operations

import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * The document's content description (`ROOT_CONTENT_DESCRIPTION`): references a [TextData] id that
 * holds the accessibility text for the whole document.
 *
 * Wire layout: opcode, `int contentDescriptionId`. Used by API-6 (flat) documents — where the
 * content description is a standalone operation rather than a header property as in the API-7 map
 * header (which is why it was missed in the API-7 byte maps).
 */
class RootContentDescription(val contentDescriptionId: Int) : Operation {

    override val opcode: Int get() = Operations.ROOT_CONTENT_DESCRIPTION

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(contentDescriptionId)
    }

    override fun dump(): String = "ROOT_CONTENT_DESCRIPTION id=$contentDescriptionId"

    override fun equals(other: Any?): Boolean =
        this === other || (other is RootContentDescription && contentDescriptionId == other.contentDescriptionId)

    override fun hashCode(): Int = contentDescriptionId

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += RootContentDescription(buffer.readInt())
        }
    }
}
