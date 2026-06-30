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

import com.tneff.kmpremotecompose.remote.core.operations.utilities.IntegerExpressionEvaluator
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.VariableSupport
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * An integer RPN expression (`INTEGER_EXPRESSION`): binds the computed result to [id]. [mask] marks
 * which [value] entries are ids (vs literals/operators).
 *
 * Wire layout: opcode, `int id`, `int mask`, `int count`, then `count` ints.
 *
 * **REM-176 render-apply (`VariableSupport`).** Phase-A producer — mirrors upstream
 * `IntegerExpression.apply` (`IntegerExpression.java:103`): [updateVariables] resolves int-id
 * references via `context.getInt(srcId)` into a [resolved] cache (raw values for non-id slots),
 * then [apply] evaluates the RPN with [IntegerExpressionEvaluator] and writes the result to the
 * int-store via `context.loadInt(id, result)`. Downstream FloatExpressions that hold the result
 * via `WireTypes.asNan(id)` read it cross-store via `context.getFloat(id)` (kept in sync by the
 * caller; `experimental_solar_gmt.rc`'s solar chain consumes id=88 as a float-NaN-ref).
 *
 * **Cross-store synchronization:** when [apply] writes the int result, we mirror it into the
 * float-store too (`context.loadFloat(id, result.toFloat())`). The corpus has at least one chain
 * (id=88 in experimental_solar_gmt) that consumes the INTEGER_EXPRESSION result via a FLOAT
 * NaN-encoded var-ref — without the cross-store mirror the downstream FloatExpressions would
 * read 0.0f and the whole solar chain collapses to 1970-Werte (the original REM-176 defect).
 */
class IntegerExpression(val id: Int, val mask: Int, val value: IntArray) : Operation, VariableSupport {
    override val opcode: Int get() = Operations.INTEGER_EXPRESSION

    /** Render-only resolved-values cache (REM-176). Same shape as upstream `mPreCalcValue` /
     *  `mPreMask`: the mask bits for resolved id-slots are cleared so the evaluator treats them
     *  as literals. Reset every [updateVariables] call. Not serialized. */
    private var resolvedValue: IntArray = IntArray(0)
    private var resolvedMask: Int = 0

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeInt(mask)
        buffer.writeInt(value.size)
        for (v in value) buffer.writeInt(v)
    }

    override fun updateVariables(context: RemoteContext) {
        if (resolvedValue.size != value.size) resolvedValue = IntArray(value.size)
        resolvedMask = mask
        for (i in value.indices) {
            val v = value[i]
            if (IntegerExpressionEvaluator.isId(mask, i, v)) {
                // Clear the mask bit so the evaluator treats this slot as a literal, then put the
                // resolved value in. Mirrors upstream IntegerExpression.updateVariables.
                resolvedMask = resolvedMask and (1 shl i).inv()
                resolvedValue[i] = context.getInt(v)
            } else {
                resolvedValue[i] = v
            }
        }
    }

    override fun apply(context: RemoteContext) {
        // Defensive: if updateVariables hasn't been called for this pass, do it now.
        if (resolvedValue.size != value.size) updateVariables(context)
        // The evaluator destructively reuses its input array → pass a copy so a future re-eval
        // (e.g. animation frames) sees the same resolved baseline.
        val v = IntegerExpressionEvaluator.eval(resolvedMask, resolvedValue.copyOf())
        context.loadInt(id, v)
        // REM-176 — cross-store mirror so a downstream FloatExpression consuming `asNan(id)` via
        // `context.getFloat(id)` sees the same value. `experimental_solar_gmt.rc`'s chain at id=88
        // (days-since-epoch) flows into FloatExpression id=89 via this exact pattern.
        context.loadFloat(id, v.toFloat())
    }

    override fun dump(): String = "INTEGER_EXPRESSION id=$id mask=$mask value[${value.size}]"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is IntegerExpression && id == other.id && mask == other.mask && value.contentEquals(other.value))

    override fun hashCode(): Int = 31 * (31 * id + mask) + value.contentHashCode()

    companion object : OperationReader {
        /** Mirror of the upstream `Limits.MAX_EXPRESSION_SIZE`. */
        const val MAX_SIZE = 32

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val id = buffer.readInt()
            val mask = buffer.readInt()
            val count = buffer.readInt()
            if (count < 0 || count > MAX_SIZE) throw IllegalStateException("integer expression too long: $count")
            operations += IntegerExpression(id, mask, IntArray(count) { buffer.readInt() })
        }
    }
}
