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
 * Raw bitmap data (`DATA_BITMAP`): stores an image's pixels under [imageId].
 *
 * Wire layout (basic form): opcode, `int imageId`, `int width`, `int height`, then a length-prefixed
 * byte buffer holding the encoded pixels. (The reference's packed type/encoding form is deferred.)
 */
class BitmapData(
    val imageId: Int,
    val width: Int,
    val height: Int,
    val data: ByteArray,
) : Operation {

    override val opcode: Int get() = Operations.DATA_BITMAP

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(imageId)
        buffer.writeInt(width)
        buffer.writeInt(height)
        buffer.writeBuffer(data)
    }

    override fun dump(): String = "DATA_BITMAP id=$imageId ${width}x$height ${data.size}B"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is BitmapData &&
                imageId == other.imageId &&
                width == other.width &&
                height == other.height &&
                data.contentEquals(other.data))

    override fun hashCode(): Int {
        var result = imageId
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + data.contentHashCode()
        return result
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val imageId = buffer.readInt()
            val width = buffer.readInt()
            val height = buffer.readInt()
            val data = buffer.readBuffer()
            operations += BitmapData(imageId, width, height, data)
        }
    }
}
