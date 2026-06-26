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
 * `DRAW_PATH` (opcode [Operations.DRAW_PATH]) — draw a previously stored path by id.
 *
 * Wire layout: opcode byte + int `id` (mirrors upstream `DrawPath`).
 */
class DrawPath(val id: Int) : PaintOperation {

    override val opcode: Int get() = Operations.DRAW_PATH

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
    }

    /** L2 render: dispatch to the geometry adapter via the paint context (REM-8). */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        paint.drawPath(id, 0f, 1f)
    }

    override fun dump(): String = "DRAW_PATH id=$id"

    override fun equals(other: Any?): Boolean = this === other || (other is DrawPath && id == other.id)

    override fun hashCode(): Int = id

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += DrawPath(buffer.readInt())
        }
    }
}
