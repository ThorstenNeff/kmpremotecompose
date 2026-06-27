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
 * `DRAW_TO_BITMAP` (opcode [Operations.DRAW_TO_BITMAP]) — redirects drawing into a bitmap target
 * (ANDROIDX + WIDGETS overlay op).
 *
 * Wire layout: opcode byte + int `bitmapId` + int `mode` + int `color` = 13 bytes (mirrors upstream
 * `DrawToBitmap.apply`/`read`).
 */
class DrawToBitmap(
    val bitmapId: Int,
    val mode: Int,
    val color: Int,
) : Operation, PaintOperation {

    override val opcode: Int get() = Operations.DRAW_TO_BITMAP

    /**
     * REM-40: redirect subsequent drawing into the offscreen bitmap [bitmapId] (or back to the main
     * canvas when `bitmapId == 0`). Mirrors upstream `DrawToBitmap.paint` → `PaintContext.drawToBitmap`.
     */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        paint.drawToBitmap(bitmapId, mode, color)
    }

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(bitmapId)
        buffer.writeInt(mode)
        buffer.writeInt(color)
    }

    override fun dump(): String = "DRAW_TO_BITMAP bitmapId=$bitmapId mode=$mode color=$color"

    override fun equals(other: Any?): Boolean =
        this === other || (other is DrawToBitmap && bitmapId == other.bitmapId && mode == other.mode && color == other.color)

    override fun hashCode(): Int = 31 * (31 * bitmapId + mode) + color

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += DrawToBitmap(buffer.readInt(), buffer.readInt(), buffer.readInt())
        }
    }
}
