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
 * A derived color attribute (`ATTRIBUTE_COLOR`): extracts a channel/attribute [type] from the color
 * [colorId] into [id].
 *
 * Wire layout: opcode, `int id`, `int colorId`, `short type`.
 */
class ColorAttribute(val id: Int, val colorId: Int, val type: Int) : Operation {
    override val opcode: Int get() = Operations.ATTRIBUTE_COLOR

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeInt(colorId)
        buffer.writeShort(type)
    }

    override fun dump(): String = "ATTRIBUTE_COLOR id=$id colorId=$colorId type=$type"

    override fun equals(other: Any?): Boolean =
        this === other || (other is ColorAttribute && id == other.id && colorId == other.colorId && type == other.type)

    override fun hashCode(): Int = 31 * (31 * id + colorId) + type

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += ColorAttribute(buffer.readInt(), buffer.readInt(), buffer.readShort())
        }
    }
}
