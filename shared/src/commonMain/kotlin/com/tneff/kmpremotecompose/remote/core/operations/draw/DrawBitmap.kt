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
package com.tneff.kmpremotecompose.remote.core.operations.draw

import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.OperationReader
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * `DRAW_BITMAP` (opcode [Operations.DRAW_BITMAP]) — draws a bitmap into a rect (the draw command,
 * distinct from the group-A `DATA_BITMAP` image data).
 *
 * Wire layout: opcode byte + int `id` + float `left` + float `top` + float `right` + float `bottom` +
 * int `descriptionId` = 25 bytes (mirrors upstream `DrawBitmap.apply`/`read`). Floats may carry
 * NaN-encoded ids; raw bits preserved.
 */
class DrawBitmap(
    val id: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val descriptionId: Int,
) : Operation {

    override val opcode: Int get() = Operations.DRAW_BITMAP

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeFloat(left)
        buffer.writeFloat(top)
        buffer.writeFloat(right)
        buffer.writeFloat(bottom)
        buffer.writeInt(descriptionId)
    }

    override fun dump(): String = "DRAW_BITMAP id=$id left=$left top=$top right=$right bottom=$bottom descId=$descriptionId"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is DrawBitmap &&
                id == other.id &&
                left.toRawBits() == other.left.toRawBits() && top.toRawBits() == other.top.toRawBits() &&
                right.toRawBits() == other.right.toRawBits() && bottom.toRawBits() == other.bottom.toRawBits() &&
                descriptionId == other.descriptionId
            )

    override fun hashCode(): Int {
        var h = id
        h = 31 * h + left.toRawBits()
        h = 31 * h + top.toRawBits()
        h = 31 * h + right.toRawBits()
        h = 31 * h + bottom.toRawBits()
        h = 31 * h + descriptionId
        return h
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += DrawBitmap(
                buffer.readInt(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readInt(),
            )
        }
    }
}
