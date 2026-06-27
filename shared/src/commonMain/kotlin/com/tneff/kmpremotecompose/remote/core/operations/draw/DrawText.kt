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
import com.tneff.kmpremotecompose.remote.wire.WireTypes

/**
 * `DRAW_TEXT_RUN` (opcode [Operations.DRAW_TEXT_RUN]) — draw a run of a text resource.
 *
 * Wire layout (mirrors upstream `DrawText.apply`/`read`, 30 bytes): opcode byte + int `textId` +
 * int `start` + int `end` + int `contextStart` + int `contextEnd` + float `x` + float `y` +
 * boolean `rtl` (1 byte). `x`/`y` may carry NaN-encoded ids; raw float bits preserved.
 */
class DrawText(
    val textId: Int,
    val start: Int,
    val end: Int,
    val contextStart: Int,
    val contextEnd: Int,
    val x: Float,
    val y: Float,
    val rtl: Boolean,
) : PaintOperation {

    override val opcode: Int get() = Operations.DRAW_TEXT_RUN

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(textId)
        buffer.writeInt(start)
        buffer.writeInt(end)
        buffer.writeInt(contextStart)
        buffer.writeInt(contextEnd)
        buffer.writeFloat(x)
        buffer.writeFloat(y)
        buffer.writeBoolean(rtl)
    }

    /** Render the run: fields map 1:1 onto the text primitive (color/size from the shared paint state). */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        // Resolve NaN-encoded variable refs for the run position (REM-54), like DrawTextAnchored.
        val rx = if (x.isNaN()) context.getFloat(WireTypes.idFromNan(x)) else x
        val ry = if (y.isNaN()) context.getFloat(WireTypes.idFromNan(y)) else y
        paint.drawTextRun(textId, start, end, contextStart, contextEnd, rx, ry, rtl)
    }

    override fun dump(): String =
        "DRAW_TEXT_RUN textId=$textId range=[$start,$end) ctx=[$contextStart,$contextEnd) x=$x y=$y rtl=$rtl"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is DrawText &&
                textId == other.textId && start == other.start && end == other.end &&
                contextStart == other.contextStart && contextEnd == other.contextEnd &&
                x.toRawBits() == other.x.toRawBits() && y.toRawBits() == other.y.toRawBits() &&
                rtl == other.rtl
            )

    override fun hashCode(): Int {
        var h = textId
        h = 31 * h + start
        h = 31 * h + end
        h = 31 * h + contextStart
        h = 31 * h + contextEnd
        h = 31 * h + x.toRawBits()
        h = 31 * h + y.toRawBits()
        h = 31 * h + rtl.hashCode()
        return h
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += DrawText(
                textId = buffer.readInt(),
                start = buffer.readInt(),
                end = buffer.readInt(),
                contextStart = buffer.readInt(),
                contextEnd = buffer.readInt(),
                x = buffer.readFloat(),
                y = buffer.readFloat(),
                rtl = buffer.readBoolean(),
            )
        }
    }
}
