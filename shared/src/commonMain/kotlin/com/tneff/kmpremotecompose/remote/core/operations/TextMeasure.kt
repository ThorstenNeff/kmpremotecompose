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
 * Measure a text (`TEXT_MEASURE`): exposes a measured dimension ([type]) of [textId] as [id].
 *
 * Wire layout: opcode, `int id`, `int textId`, `int type`.
 */
class TextMeasure(val id: Int, val textId: Int, val type: Int) : Operation {
    override val opcode: Int get() = Operations.TEXT_MEASURE

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeInt(textId)
        buffer.writeInt(type)
    }

    override fun dump(): String = "TEXT_MEASURE id=$id textId=$textId type=$type"

    override fun equals(other: Any?): Boolean =
        this === other || (other is TextMeasure && id == other.id && textId == other.textId && type == other.type)

    override fun hashCode(): Int = 31 * (31 * id + textId) + type

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += TextMeasure(buffer.readInt(), buffer.readInt(), buffer.readInt())
        }
    }
}
