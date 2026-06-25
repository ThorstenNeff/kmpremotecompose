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
 * `LAYOUT_IMAGE` (opcode [Operations.LAYOUT_IMAGE]) — an image layout component.
 *
 * Wire layout: opcode byte + int `componentId` + int `animationId` + int `bitmapId` +
 * int `scaleType` + float `alpha` (mirrors upstream `ImageLayout.apply`/`read`).
 */
class ImageLayout(
    val componentId: Int,
    val animationId: Int,
    val bitmapId: Int,
    val scaleType: Int,
    val alpha: Float,
) : Operation {

    override val opcode: Int get() = Operations.LAYOUT_IMAGE

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(componentId)
        buffer.writeInt(animationId)
        buffer.writeInt(bitmapId)
        buffer.writeInt(scaleType)
        buffer.writeFloat(alpha)
    }

    override fun dump(): String =
        "LAYOUT_IMAGE id=$componentId anim=$animationId bitmap=$bitmapId " +
            "scaleType=$scaleType alpha=$alpha"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is ImageLayout &&
                componentId == other.componentId && animationId == other.animationId &&
                bitmapId == other.bitmapId && scaleType == other.scaleType &&
                alpha.toRawBits() == other.alpha.toRawBits()
            )

    override fun hashCode(): Int {
        var h = componentId
        h = 31 * h + animationId
        h = 31 * h + bitmapId
        h = 31 * h + scaleType
        h = 31 * h + alpha.toRawBits()
        return h
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += ImageLayout(
                buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readFloat(),
            )
        }
    }
}
