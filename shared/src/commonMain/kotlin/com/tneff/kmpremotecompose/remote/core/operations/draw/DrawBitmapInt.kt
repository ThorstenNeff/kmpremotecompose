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
import com.tneff.kmpremotecompose.remote.player.core.PaintContext
import com.tneff.kmpremotecompose.remote.player.core.PaintOperation
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * `DRAW_BITMAP_INT` (opcode [Operations.DRAW_BITMAP_INT]) — render a sub-rect of the bitmap stored
 * under [imageId] into the destination rect, with an optional content-description id for a11y.
 *
 * Wire layout (mirrors upstream `DrawBitmapInt.apply` —
 * `androidx/compose/remote/core/operations/DrawBitmapInt.java:152-175`):
 * opcode byte + 10 big-endian ints = 41 bytes total. Order:
 * `imageId, srcLeft, srcTop, srcRight, srcBottom, dstLeft, dstTop, dstRight, dstBottom, cdId`.
 *
 * [imageId] is a region-0 plain id pointing at a previously emitted `DATA_BITMAP` op (no inline
 * pixel array — bitmap data lives in the DATA_BITMAP referenced). [cdId] is a region-0 id pointing
 * at a `DATA_TEXT` (content description); pass `0` if not used.
 */
class DrawBitmapInt(
    val imageId: Int,
    val srcLeft: Int,
    val srcTop: Int,
    val srcRight: Int,
    val srcBottom: Int,
    val dstLeft: Int,
    val dstTop: Int,
    val dstRight: Int,
    val dstBottom: Int,
    val cdId: Int = 0,
) : PaintOperation {

    override val opcode: Int get() = Operations.DRAW_BITMAP_INT

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(imageId)
        buffer.writeInt(srcLeft)
        buffer.writeInt(srcTop)
        buffer.writeInt(srcRight)
        buffer.writeInt(srcBottom)
        buffer.writeInt(dstLeft)
        buffer.writeInt(dstTop)
        buffer.writeInt(dstRight)
        buffer.writeInt(dstBottom)
        buffer.writeInt(cdId)
    }

    /** L2 render placeholder — int-coords bitmap blit lands with the player-side bitmap engine. */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        // Out of scope for REM-113 (creation-side only).
    }

    override fun dump(): String =
        "DRAW_BITMAP_INT imageId=$imageId src=($srcLeft,$srcTop,$srcRight,$srcBottom) " +
            "dst=($dstLeft,$dstTop,$dstRight,$dstBottom) cdId=$cdId"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is DrawBitmapInt &&
                imageId == other.imageId &&
                srcLeft == other.srcLeft && srcTop == other.srcTop &&
                srcRight == other.srcRight && srcBottom == other.srcBottom &&
                dstLeft == other.dstLeft && dstTop == other.dstTop &&
                dstRight == other.dstRight && dstBottom == other.dstBottom &&
                cdId == other.cdId
            )

    override fun hashCode(): Int {
        var h = imageId
        h = 31 * h + srcLeft; h = 31 * h + srcTop
        h = 31 * h + srcRight; h = 31 * h + srcBottom
        h = 31 * h + dstLeft; h = 31 * h + dstTop
        h = 31 * h + dstRight; h = 31 * h + dstBottom
        h = 31 * h + cdId
        return h
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val imageId = buffer.readInt()
            val srcLeft = buffer.readInt()
            val srcTop = buffer.readInt()
            val srcRight = buffer.readInt()
            val srcBottom = buffer.readInt()
            val dstLeft = buffer.readInt()
            val dstTop = buffer.readInt()
            val dstRight = buffer.readInt()
            val dstBottom = buffer.readInt()
            val cdId = buffer.readInt()
            operations += DrawBitmapInt(
                imageId, srcLeft, srcTop, srcRight, srcBottom,
                dstLeft, dstTop, dstRight, dstBottom, cdId,
            )
        }
    }
}
