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
 * Transform a substring of a source text (`TEXT_TRANSFORM`): applies [operation] (e.g. upper/lower)
 * to `srcId1[start..start+len]` into [textId].
 *
 * Wire layout: opcode, `int textId`, `int srcId1`, `float start`, `float len`, `int operation`.
 * Profile-overlay op (androidx + widgets).
 */
class TextTransform(
    val textId: Int,
    val srcId1: Int,
    val start: Float,
    val len: Float,
    val operation: Int,
) : Operation {

    override val opcode: Int get() = Operations.TEXT_TRANSFORM

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(textId)
        buffer.writeInt(srcId1)
        buffer.writeFloat(start)
        buffer.writeFloat(len)
        buffer.writeInt(operation)
    }

    override fun dump(): String = "TEXT_TRANSFORM id=$textId src=$srcId1 [$start,$len] op=$operation"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is TextTransform && textId == other.textId && srcId1 == other.srcId1 &&
                start.toRawBits() == other.start.toRawBits() && len.toRawBits() == other.len.toRawBits() &&
                operation == other.operation)

    override fun hashCode(): Int {
        var r = textId
        r = 31 * r + srcId1
        r = 31 * r + start.toRawBits()
        r = 31 * r + len.toRawBits()
        r = 31 * r + operation
        return r
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += TextTransform(
                buffer.readInt(), buffer.readInt(), buffer.readFloat(), buffer.readFloat(), buffer.readInt(),
            )
        }
    }
}
