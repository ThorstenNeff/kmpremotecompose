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
 * `DRAW_TEXT_ON_PATH` (opcode [Operations.DRAW_TEXT_ON_PATH]) — draws text along a path.
 *
 * Wire layout: opcode byte + int `textId` + int `pathId` + float `vOffset` + float `hOffset` = 17 bytes
 * (mirrors upstream `DrawTextOnPath.apply`/`read` — wire order is vOffset then hOffset). Floats may
 * carry NaN-encoded ids; raw bits preserved.
 */
class DrawTextOnPath(
    val textId: Int,
    val pathId: Int,
    val vOffset: Float,
    val hOffset: Float,
) : PaintOperation {

    override val opcode: Int get() = Operations.DRAW_TEXT_ON_PATH

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(textId)
        buffer.writeInt(pathId)
        buffer.writeFloat(vOffset)
        buffer.writeFloat(hOffset)
    }

    /**
     * Bind to the text-on-path primitive (primitive arg order is `hOffset, vOffset`). The CMP
     * implementation is **deferred to L2-D1** (needs a built Path + PathMeasure) and is a documented
     * no-op today; the dispatch seam is in place so it lights up when D1 lands.
     */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        paint.drawTextOnPath(textId, pathId, hOffset, vOffset)
    }

    override fun dump(): String = "DRAW_TEXT_ON_PATH textId=$textId pathId=$pathId vOffset=$vOffset hOffset=$hOffset"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is DrawTextOnPath &&
                textId == other.textId && pathId == other.pathId &&
                vOffset.toRawBits() == other.vOffset.toRawBits() && hOffset.toRawBits() == other.hOffset.toRawBits()
            )

    override fun hashCode(): Int {
        var h = textId
        h = 31 * h + pathId
        h = 31 * h + vOffset.toRawBits()
        h = 31 * h + hOffset.toRawBits()
        return h
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += DrawTextOnPath(buffer.readInt(), buffer.readInt(), buffer.readFloat(), buffer.readFloat())
        }
    }
}
