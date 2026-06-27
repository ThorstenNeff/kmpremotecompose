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
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import com.tneff.kmpremotecompose.remote.wire.WireTypes

/**
 * A conditional gate (`CONDITIONAL_OPERATIONS`): compares [a] and [b] by [type]; the operations that
 * follow — up to the matching `CONTAINER_END` — run only if the comparison holds (REM-41). It is a
 * **Container** (upstream): the gated block is the following ops, not part of its bytes.
 *
 * Wire layout: opcode, `byte type`, `float a`, `float b` (a/b raw bits — may be NaN-encoded ids).
 * The gating is **runtime-only** (the player walk skips the block when [conditionHolds] is false);
 * `write`/`read` are unchanged → byte-compatibility preserved.
 */
class ConditionalOperations(val type: Int, val a: Float, val b: Float) : Operation {
    override val opcode: Int get() = Operations.CONDITIONAL_OPERATIONS

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeByte(type)
        buffer.writeFloat(a)
        buffer.writeFloat(b)
    }

    /**
     * Evaluate the gate against [context]: resolve [a]/[b] (NaN data-var refs → the stored value, else
     * the literal) and compare by [type] (upstream `ConditionalOperations.paint`). The block runs iff
     * this is true.
     */
    fun conditionHolds(context: RemoteContext): Boolean {
        val av = if (a.isNaN()) context.getFloat(WireTypes.idFromNan(a)) else a
        val bv = if (b.isNaN()) context.getFloat(WireTypes.idFromNan(b)) else b
        return when (type) {
            TYPE_EQ -> av == bv
            TYPE_NEQ -> av != bv
            TYPE_LT -> av < bv
            TYPE_LTE -> av <= bv
            TYPE_GT -> av > bv
            TYPE_GTE -> av >= bv
            else -> true // unknown type → don't gate out (fail-open)
        }
    }

    override fun dump(): String = "CONDITIONAL_OPERATIONS type=$type a=$a b=$b"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is ConditionalOperations && type == other.type &&
                a.toRawBits() == other.a.toRawBits() && b.toRawBits() == other.b.toRawBits())

    override fun hashCode(): Int = 31 * (31 * type + a.toRawBits()) + b.toRawBits()

    companion object : OperationReader {
        // Comparison types (upstream ConditionalOperations).
        const val TYPE_EQ = 0
        const val TYPE_NEQ = 1
        const val TYPE_LT = 2
        const val TYPE_LTE = 3
        const val TYPE_GT = 4
        const val TYPE_GTE = 5

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += ConditionalOperations(buffer.readByte(), buffer.readFloat(), buffer.readFloat())
        }
    }
}
