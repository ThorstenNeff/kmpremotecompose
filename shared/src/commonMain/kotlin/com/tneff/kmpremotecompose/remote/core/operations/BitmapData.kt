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

import androidx.compose.ui.graphics.ImageBitmap
import com.tneff.kmpremotecompose.remote.player.core.PaintContext
import com.tneff.kmpremotecompose.remote.player.core.PaintOperation
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.decodeImageBitmap
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * Bitmap data (`DATA_BITMAP`): stores or references an image under [imageId].
 *
 * Wire layout: opcode, `int imageId`, `int widthAndType`, `int heightAndEncoding`, then a
 * length-prefixed byte buffer of the (raw or encoded) pixels. The image [type] is packed into the
 * high 16 bits of the width word and the [encoding] into the high 16 bits of the height word —
 * upstream always writes this packed form. For the default `TYPE_PNG_8888` / `ENCODING_INLINE`
 * (both 0) with dimensions ≤ 0xFFFF the packed words equal plain width/height, so it is byte-identical
 * to a naive width/height encoding (the only case that occurred before this completion).
 */
class BitmapData(
    val imageId: Int,
    val width: Int,
    val height: Int,
    val data: ByteArray,
    val type: Int = TYPE_PNG_8888,
    val encoding: Int = ENCODING_INLINE,
) : Operation, PaintOperation {

    override val opcode: Int get() = Operations.DATA_BITMAP

    /**
     * Register the image in the id store so draws / render-to-bitmap can find it. Idempotent (only the
     * first time per id, so a re-cleared render target persists across frames). Dimensions are bounded
     * (fail-closed vs hostile/corrupt sizes) and all allocation/decoding is fail-soft (a missing target
     * just renders empty, never crashes — incl. corrupt/hostile inline bytes).
     *  - `ENCODING_EMPTY` (REM-40): allocate an empty [width]×[height] render-target (DRAW_TO_BITMAP).
     *  - `ENCODING_INLINE` (REM-55): decode the inline PNG bytes via [decodeImageBitmap] (null → empty).
     */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        if (context.getBitmap(imageId) != null) return
        if (width !in 1..MAX_DIM || height !in 1..MAX_DIM) return
        when (encoding) {
            ENCODING_EMPTY -> runCatching { context.putBitmap(imageId, ImageBitmap(width, height)) }
            ENCODING_INLINE ->
                if (data.isNotEmpty()) decodeImageBitmap(data, type, MAX_DIM)?.let { context.putBitmap(imageId, it) }
        }
    }

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(imageId)
        buffer.writeInt((type shl 16) or (width and 0xFFFF))
        buffer.writeInt((encoding shl 16) or (height and 0xFFFF))
        buffer.writeBuffer(data)
    }

    override fun dump(): String =
        "DATA_BITMAP id=$imageId ${width}x$height type=$type encoding=$encoding ${data.size}B"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is BitmapData &&
                imageId == other.imageId &&
                width == other.width &&
                height == other.height &&
                type == other.type &&
                encoding == other.encoding &&
                data.contentEquals(other.data))

    override fun hashCode(): Int {
        var result = imageId
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + type
        result = 31 * result + encoding
        result = 31 * result + data.contentHashCode()
        return result
    }

    companion object : OperationReader {

        // Encodings (high 16 bits of the height word).
        /** Max render-target dimension (fail-closed bound against hostile/corrupt sizes). */
        const val MAX_DIM = 8192
        const val ENCODING_INLINE = 0
        const val ENCODING_URL = 1
        const val ENCODING_FILE = 2
        const val ENCODING_EMPTY = 3

        // Image types (high 16 bits of the width word).
        const val TYPE_PNG_8888 = 0
        const val TYPE_PNG = 1
        const val TYPE_RAW8 = 2
        const val TYPE_RAW8888 = 3
        const val TYPE_PNG_ALPHA_8 = 4

        // Limits mirrored from the upstream reference (fail-closed read guards).
        const val MAX_IMAGE_DIMENSION = 8000
        const val MAX_BITMAP_MEMORY = 20 * 1024 * 1024
        const val MAX_IMAGE_HEADER_SIZE = 10000

        // URL/file-backed images are not supported (matches upstream defaults).
        const val ENABLE_IMAGE_URLS = false
        const val ENABLE_IMAGE_FILES = false

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val imageId = buffer.readInt()
            var width = buffer.readInt()
            var height = buffer.readInt()

            val type: Int
            if (width > 0xFFFF) {
                type = width shr 16
                width = width and 0xFFFF
            } else {
                type = TYPE_PNG_8888
            }
            val encoding: Int
            if (height > 0xFFFF) {
                encoding = height shr 16
                height = height and 0xFFFF
            } else {
                encoding = ENCODING_INLINE
            }

            // Fail closed on untrusted input: reject unsupported encodings and out-of-range
            // dimensions before allocating, and bound the pixel buffer to the declared size.
            if (!ENABLE_IMAGE_URLS && encoding == ENCODING_URL) {
                throw IllegalStateException("URL image not supported [$imageId]")
            }
            if (!ENABLE_IMAGE_FILES && encoding == ENCODING_FILE) {
                throw IllegalStateException("file image not supported [$imageId]")
            }
            if (width < 1 || height < 1 ||
                width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION ||
                width.toLong() * height.toLong() > MAX_BITMAP_MEMORY
            ) {
                throw IllegalStateException("invalid image dimensions ${width}x$height [$imageId]")
            }

            val data = buffer.readBuffer(width * height * 4 + MAX_IMAGE_HEADER_SIZE)
            operations += BitmapData(imageId, width, height, data, type, encoding)
        }
    }
}
