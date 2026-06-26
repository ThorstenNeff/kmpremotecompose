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

import com.tneff.kmpremotecompose.remote.player.core.PaintContext
import com.tneff.kmpremotecompose.remote.player.core.PaintOperation
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/** Restore the previously saved canvas matrix (`MATRIX_RESTORE`). Wire layout: opcode only. */
class MatrixRestore : PaintOperation {
    override val opcode: Int get() = Operations.MATRIX_RESTORE
    override fun write(buffer: WireBuffer) = buffer.writeByte(opcode)
    /** L2 render: drive the canvas transform via the paint context (REM-33). */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        paint.matrixRestore()
    }

    override fun dump(): String = "MATRIX_RESTORE"
    override fun equals(other: Any?): Boolean = this === other || other is MatrixRestore
    override fun hashCode(): Int = Operations.MATRIX_RESTORE

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += MatrixRestore()
        }
    }
}
