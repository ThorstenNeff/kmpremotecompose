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
 * `DRAW_BITMAP_SCALED` (opcode [Operations.DRAW_BITMAP_SCALED]) — draws a bitmap from a src rect into a
 * dst rect with a scale type.
 *
 * Wire layout: opcode byte + int `imageId` + float src(l,t,r,b) + float dst(l,t,r,b) + int `scaleType`
 * + float `scaleFactor` + int `contentDescriptionId` = 49 bytes (mirrors upstream
 * `DrawBitmapScaled.apply`/`read`). Floats may carry NaN-encoded ids; raw bits preserved.
 */
class DrawBitmapScaled(
    val imageId: Int,
    val srcLeft: Float,
    val srcTop: Float,
    val srcRight: Float,
    val srcBottom: Float,
    val dstLeft: Float,
    val dstTop: Float,
    val dstRight: Float,
    val dstBottom: Float,
    val scaleType: Int,
    val scaleFactor: Float,
    val contentDescriptionId: Int,
) : Operation {

    override val opcode: Int get() = Operations.DRAW_BITMAP_SCALED

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(imageId)
        buffer.writeFloat(srcLeft)
        buffer.writeFloat(srcTop)
        buffer.writeFloat(srcRight)
        buffer.writeFloat(srcBottom)
        buffer.writeFloat(dstLeft)
        buffer.writeFloat(dstTop)
        buffer.writeFloat(dstRight)
        buffer.writeFloat(dstBottom)
        buffer.writeInt(scaleType)
        buffer.writeFloat(scaleFactor)
        buffer.writeInt(contentDescriptionId)
    }

    override fun dump(): String =
        "DRAW_BITMAP_SCALED imageId=$imageId src=($srcLeft,$srcTop,$srcRight,$srcBottom) " +
            "dst=($dstLeft,$dstTop,$dstRight,$dstBottom) scaleType=$scaleType scaleFactor=$scaleFactor cdId=$contentDescriptionId"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is DrawBitmapScaled &&
                imageId == other.imageId &&
                srcLeft.toRawBits() == other.srcLeft.toRawBits() && srcTop.toRawBits() == other.srcTop.toRawBits() &&
                srcRight.toRawBits() == other.srcRight.toRawBits() && srcBottom.toRawBits() == other.srcBottom.toRawBits() &&
                dstLeft.toRawBits() == other.dstLeft.toRawBits() && dstTop.toRawBits() == other.dstTop.toRawBits() &&
                dstRight.toRawBits() == other.dstRight.toRawBits() && dstBottom.toRawBits() == other.dstBottom.toRawBits() &&
                scaleType == other.scaleType && scaleFactor.toRawBits() == other.scaleFactor.toRawBits() &&
                contentDescriptionId == other.contentDescriptionId
            )

    override fun hashCode(): Int {
        var h = imageId
        h = 31 * h + srcLeft.toRawBits()
        h = 31 * h + srcTop.toRawBits()
        h = 31 * h + srcRight.toRawBits()
        h = 31 * h + srcBottom.toRawBits()
        h = 31 * h + dstLeft.toRawBits()
        h = 31 * h + dstTop.toRawBits()
        h = 31 * h + dstRight.toRawBits()
        h = 31 * h + dstBottom.toRawBits()
        h = 31 * h + scaleType
        h = 31 * h + scaleFactor.toRawBits()
        h = 31 * h + contentDescriptionId
        return h
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += DrawBitmapScaled(
                buffer.readInt(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readInt(), buffer.readFloat(), buffer.readInt(),
            )
        }
    }
}
