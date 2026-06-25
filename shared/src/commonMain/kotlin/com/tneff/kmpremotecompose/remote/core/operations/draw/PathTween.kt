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
 * `PATH_TWEEN` (opcode [Operations.PATH_TWEEN]) — interpolates between two paths into an output path.
 *
 * Wire layout: opcode byte + int `outId` + int `pathId1` + int `pathId2` + float `tween` = 17 bytes
 * (mirrors upstream `PathTween.apply`/`read`). `tween` may carry a NaN-encoded id; raw bits preserved.
 */
class PathTween(
    val outId: Int,
    val pathId1: Int,
    val pathId2: Int,
    val tween: Float,
) : Operation {

    override val opcode: Int get() = Operations.PATH_TWEEN

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(outId)
        buffer.writeInt(pathId1)
        buffer.writeInt(pathId2)
        buffer.writeFloat(tween)
    }

    override fun dump(): String = "PATH_TWEEN outId=$outId pathId1=$pathId1 pathId2=$pathId2 tween=$tween"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is PathTween &&
                outId == other.outId && pathId1 == other.pathId1 && pathId2 == other.pathId2 &&
                tween.toRawBits() == other.tween.toRawBits()
            )

    override fun hashCode(): Int {
        var h = outId
        h = 31 * h + pathId1
        h = 31 * h + pathId2
        h = 31 * h + tween.toRawBits()
        return h
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += PathTween(buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readFloat())
        }
    }
}
