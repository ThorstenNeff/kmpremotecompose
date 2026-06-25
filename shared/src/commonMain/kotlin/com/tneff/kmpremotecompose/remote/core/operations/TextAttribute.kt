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
 * A derived text attribute (`ATTRIBUTE_TEXT`): extracts attribute [type] from text [textId] into [id].
 *
 * Wire layout: opcode, `int id`, `int textId`, `short type`, `short 0` (reserved arg-length, always
 * zero on the write side; the reader discards it).
 */
class TextAttribute(val id: Int, val textId: Int, val type: Int) : Operation {
    override val opcode: Int get() = Operations.ATTRIBUTE_TEXT

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeInt(textId)
        buffer.writeShort(type)
        buffer.writeShort(0)
    }

    override fun dump(): String = "ATTRIBUTE_TEXT id=$id textId=$textId type=$type"

    override fun equals(other: Any?): Boolean =
        this === other || (other is TextAttribute && id == other.id && textId == other.textId && type == other.type)

    override fun hashCode(): Int = 31 * (31 * id + textId) + type

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val id = buffer.readInt()
            val textId = buffer.readInt()
            val type = buffer.readShort()
            buffer.readShort() // reserved arg-length, discarded
            operations += TextAttribute(id, textId, type)
        }
    }
}
