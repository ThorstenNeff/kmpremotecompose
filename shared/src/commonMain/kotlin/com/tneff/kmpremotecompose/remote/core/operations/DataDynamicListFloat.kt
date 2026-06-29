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
 * A dynamically-sized float list (`DYNAMIC_FLOAT_LIST`): reserves a float array of [nbValues] under
 * [id], where the size is itself a float (a literal count or a NaN-encoded id reference).
 *
 * Wire layout: opcode, `int id`, `float nbValues` (raw bits). Profile-overlay op (androidx + widgets).
 *
 * **REM-139 S1:** upstream `apply` registers a collection; we allocate a zeroed `FloatArray(nbValues)`
 * under [id] in Phase-A (the natural KMP mapping of the dynamic-float collection), which [UpdateDynamicFloatList]
 * then writes. Re-allocated each pass (MVP, no dirty tracking) — the declare op precedes its updates in
 * stream order, so the single Phase-A pass yields a correct list. [write]/[read] untouched (§2).
 */
class DataDynamicListFloat(val id: Int, val nbValues: Float) : Operation, VariableSupport {

    override val opcode: Int get() = Operations.DYNAMIC_FLOAT_LIST

    /**
     * Phase-A — allocate the (zeroed) backing float array so updates + consumers see it. **REM-139 S2:
     * allocate-if-absent (idempotent per frame):** the per-frame [RemoteContext] is reset each pass, so a
     * missing list is freshly allocated; but a list a `LayoutCompute` already seeded+computed in the
     * EARLIER measure() pass is left intact (no re-zero clobber). LayoutCompute children are
     * measure-authoritative; standalone lists allocate normally here.
     */
    override fun apply(context: RemoteContext) {
        if (context.getFloatArray(id) != null) return // already allocated/seeded this frame (e.g. by LayoutCompute)
        val n = context.resolveCoord(nbValues).toInt()
        if (n <= 0) return
        context.loadFloatArray(id, FloatArray(n))
    }

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeFloat(nbValues)
    }

    override fun dump(): String = "DYNAMIC_FLOAT_LIST id=$id nbValues=$nbValues"

    override fun equals(other: Any?): Boolean =
        this === other ||
            // raw bits so a NaN-encoded size id is distinguished
            (other is DataDynamicListFloat && id == other.id && nbValues.toRawBits() == other.nbValues.toRawBits())

    override fun hashCode(): Int = 31 * id + nbValues.toRawBits()

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += DataDynamicListFloat(buffer.readInt(), buffer.readFloat())
        }
    }
}
