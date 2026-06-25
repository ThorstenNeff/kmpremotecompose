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
package com.tneff.kmpremotecompose.remote.core.operations.layout

import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.OperationReader
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * `CLICK_AREA` (opcode [Operations.CLICK_AREA]) — defines a clickable region.
 *
 * Wire layout: opcode byte + int `id` + int `contentDescription` + float `left` + float `top` +
 * float `right` + float `bottom` + int `metadata` (mirrors upstream `ClickArea` operation).
 */
class ClickArea(
    val id: Int,
    val contentDescription: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val metadata: Int,
) : Operation {

    override val opcode: Int get() = Operations.CLICK_AREA

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeInt(contentDescription)
        buffer.writeFloat(left)
        buffer.writeFloat(top)
        buffer.writeFloat(right)
        buffer.writeFloat(bottom)
        buffer.writeInt(metadata)
    }

    override fun dump(): String =
        "CLICK_AREA id=$id contentDescription=$contentDescription left=$left top=$top " +
            "right=$right bottom=$bottom metadata=$metadata"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is ClickArea &&
                id == other.id && contentDescription == other.contentDescription &&
                left.toRawBits() == other.left.toRawBits() &&
                top.toRawBits() == other.top.toRawBits() &&
                right.toRawBits() == other.right.toRawBits() &&
                bottom.toRawBits() == other.bottom.toRawBits() &&
                metadata == other.metadata
            )

    override fun hashCode(): Int {
        var h = id
        h = 31 * h + contentDescription
        h = 31 * h + left.toRawBits()
        h = 31 * h + top.toRawBits()
        h = 31 * h + right.toRawBits()
        h = 31 * h + bottom.toRawBits()
        h = 31 * h + metadata
        return h
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += ClickArea(
                buffer.readInt(), buffer.readInt(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readInt(),
            )
        }
    }
}
