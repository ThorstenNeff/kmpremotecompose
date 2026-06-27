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
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * An id list (`ID_LIST`): binds an array of ids to [id].
 *
 * Wire layout: opcode, `int id`, `int count`, then `count` ints.
 *
 * **Binding (REM-59 I2):** a producer — Phase A stores [ids] into the context's id-array collection
 * ([RemoteContext.loadIdArray]) so `TEXT_LOOKUP` can resolve `dataSet[index] → text-id` (chart labels).
 */
class DataListIds(val id: Int, val ids: IntArray) : Operation, VariableSupport {

    override val opcode: Int get() = Operations.ID_LIST

    override fun apply(context: RemoteContext) {
        context.loadIdArray(id, ids)
    }

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeInt(ids.size)
        for (v in ids) buffer.writeInt(v)
    }

    override fun dump(): String = "ID_LIST id=$id ids[${ids.size}]"

    override fun equals(other: Any?): Boolean =
        this === other || (other is DataListIds && id == other.id && ids.contentEquals(other.ids))

    override fun hashCode(): Int = 31 * id + ids.contentHashCode()

    companion object : OperationReader {
        /** Mirror of the upstream `Limits.MAX_DATA_MAP_SIZE` ceiling. */
        const val MAX_ID_LIST_SIZE = 2000

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val id = buffer.readInt()
            val count = buffer.readInt()
            if (count < 0 || count > MAX_ID_LIST_SIZE) throw IllegalStateException("id list too long: $count")
            val ids = IntArray(count) { buffer.readInt() }
            operations += DataListIds(id, ids)
        }
    }
}
