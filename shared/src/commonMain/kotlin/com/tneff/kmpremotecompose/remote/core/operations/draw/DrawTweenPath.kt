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
 * `DRAW_TWEEN_PATH` (opcode [Operations.DRAW_TWEEN_PATH]) — draws an interpolation between two paths.
 *
 * Wire layout: opcode byte + int `path1Id` + int `path2Id` + float `tween` + float `start` + float
 * `stop` = 21 bytes (mirrors upstream `DrawTweenPath.apply`/`read`). Floats may carry NaN-encoded ids.
 */
class DrawTweenPath(
    val path1Id: Int,
    val path2Id: Int,
    val tween: Float,
    val start: Float,
    val stop: Float,
) : PaintOperation {

    override val opcode: Int get() = Operations.DRAW_TWEEN_PATH

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(path1Id)
        buffer.writeInt(path2Id)
        buffer.writeFloat(tween)
        buffer.writeFloat(start)
        buffer.writeFloat(stop)
    }

    /** L2 render: draw the interpolated path via the paint context (REM-33). */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        paint.drawTweenPath(path1Id, path2Id, tween, start, stop)
    }

    override fun dump(): String = "DRAW_TWEEN_PATH path1=$path1Id path2=$path2Id tween=$tween start=$start stop=$stop"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is DrawTweenPath &&
                path1Id == other.path1Id && path2Id == other.path2Id &&
                tween.toRawBits() == other.tween.toRawBits() && start.toRawBits() == other.start.toRawBits() &&
                stop.toRawBits() == other.stop.toRawBits()
            )

    override fun hashCode(): Int {
        var h = path1Id
        h = 31 * h + path2Id
        h = 31 * h + tween.toRawBits()
        h = 31 * h + start.toRawBits()
        h = 31 * h + stop.toRawBits()
        return h
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += DrawTweenPath(
                buffer.readInt(), buffer.readInt(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
            )
        }
    }
}
