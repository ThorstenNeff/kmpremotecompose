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

import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.VariableSupport
import com.tneff.kmpremotecompose.remote.player.core.resolveCoord
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * Update one slot of a dynamic float list (`UPDATE_DYNAMIC_FLOAT_LIST`): sets `list[index] = value`
 * for the list [id].
 *
 * Wire layout: opcode, `int id`, `float index`, `float value` (raw bits — may be NaN-encoded ids).
 * Profile-overlay op (androidx + widgets).
 *
 * **REM-139 S1:** upstream `apply` writes `list[index]=value`; we resolve the NaN-ref index/value
 * ([updateVariables]) then write into the [DataDynamicListFloat]-backed array under [id] ([apply]).
 * Phase-A producer; bounds-checked + fail-soft when the list is absent. [write]/[read] untouched (§2).
 */
class UpdateDynamicFloatList(val id: Int, val index: Float, val value: Float) : Operation, VariableSupport {
    override val opcode: Int get() = Operations.UPDATE_DYNAMIC_FLOAT_LIST

    // REM-139 render-only resolved index/value (NaN refs → store); not serialized → byte-safe.
    private var rIndex: Float = index
    private var rValue: Float = value

    override fun updateVariables(context: RemoteContext) {
        rIndex = context.resolveCoord(index)
        rValue = context.resolveCoord(value)
    }

    /** Phase-A — write the resolved value into the backing list slot (bounds-checked, fail-soft). */
    override fun apply(context: RemoteContext) {
        val list = context.getFloatArray(id) ?: return
        val i = rIndex.toInt()
        if (i in list.indices) list[i] = rValue
    }

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeFloat(index)
        buffer.writeFloat(value)
    }

    override fun dump(): String = "UPDATE_DYNAMIC_FLOAT_LIST id=$id index=$index value=$value"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is UpdateDynamicFloatList && id == other.id &&
                index.toRawBits() == other.index.toRawBits() && value.toRawBits() == other.value.toRawBits())

    override fun hashCode(): Int = 31 * (31 * id + index.toRawBits()) + value.toRawBits()

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += UpdateDynamicFloatList(buffer.readInt(), buffer.readFloat(), buffer.readFloat())
        }
    }
}
