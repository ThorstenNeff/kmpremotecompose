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
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import com.tneff.kmpremotecompose.remote.player.core.PaintContext
import com.tneff.kmpremotecompose.remote.player.core.PaintOperation
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * `PATH_CREATE` (opcode [Operations.PATH_CREATE]) — begins a path at a start point.
 *
 * Wire layout: opcode byte + int `id` + float `startX` + float `startY` = 13 bytes (mirrors upstream
 * `PathCreate.apply`/`read`). The floats may carry NaN-encoded ids; raw bits preserved.
 */
class PathCreate(
    val id: Int,
    val startX: Float,
    val startY: Float,
) : PaintOperation {

    override val opcode: Int get() = Operations.PATH_CREATE

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeFloat(startX)
        buffer.writeFloat(startY)
    }

    /** L2 render: single-pass data-apply — create the path with its initial moveTo (REM-33). */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        // upstream PathCreate: mFloatPath = [MOVE_NAN, startX, startY] (PathData.MOVE = 10).
        context.putPathData(id, floatArrayOf(WireTypes.asNan(10), startX, startY))
    }

    override fun dump(): String = "PATH_CREATE id=$id startX=$startX startY=$startY"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is PathCreate &&
                id == other.id &&
                startX.toRawBits() == other.startX.toRawBits() && startY.toRawBits() == other.startY.toRawBits()
            )

    override fun hashCode(): Int {
        var h = id
        h = 31 * h + startX.toRawBits()
        h = 31 * h + startY.toRawBits()
        return h
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += PathCreate(buffer.readInt(), buffer.readFloat(), buffer.readFloat())
        }
    }
}
