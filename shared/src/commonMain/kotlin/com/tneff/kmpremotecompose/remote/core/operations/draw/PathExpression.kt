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
import com.tneff.kmpremotecompose.remote.player.core.PathGenerator
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.RpnFloatEvaluator
import com.tneff.kmpremotecompose.remote.player.core.VariableSupport
import com.tneff.kmpremotecompose.remote.player.core.resolveCoord
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * `PATH_EXPRESSION` (opcode [Operations.PATH_EXPRESSION]) — a path defined by two float expression
 * arrays (X, Y). ANDROIDX + WIDGETS overlay op.
 *
 * Wire layout (mirrors upstream `PathExpression.apply`/`read`): opcode byte + int `id` + int `flags` +
 * float `min` + float `max` + float `count` + int `lenX` + `lenX`×float (X) + int `lenY` + `lenY`×float (Y).
 * Float values may carry NaN-encoded ids; raw bits preserved.
 *
 * **REM-127 render-apply:** upstream `PathExpression implements VariableSupport` — a PRODUCER. In
 * [apply] it samples `X(t)`/`Y(t)` over `[min,max]` (`t` injected as the RPN `VAR1`) via [PathGenerator]
 * into cubic-marker path-data and loads it under [id]; the existing `DrawPath#id` (REM-121) renders it.
 * Render-only: [write]/[read] and the raw fields are untouched → 173-byte-conformance intact (§2/§6).
 */
class PathExpression(
    val id: Int,
    val flags: Int,
    val min: Float,
    val max: Float,
    val count: Float,
    val expressionX: FloatArray,
    val expressionY: FloatArray,
) : Operation, VariableSupport {

    override val opcode: Int get() = Operations.PATH_EXPRESSION

    /**
     * REM-127 — evaluate the path and load it into the store (producer side). MVP re-evaluates every
     * frame (no dirty tracking, mirrors the E1 eval MVP). Flags: `LOOP=0x1`, mode `=(flags & 0x6)`
     * (0=SPLINE/2=MONOTONIC/4=LINEAR), `POLAR=0x8`, `winding=(flags & 0x3000000) >> 24`.
     */
    override fun apply(context: RemoteContext) {
        val rCount = context.resolveCoord(count).toInt()
        if (rCount < 2) return // upstream throws on 0; fail-soft (a degenerate count renders nothing)
        val rMin = context.resolveCoord(min)
        val rMax = context.resolveCoord(max)
        val mode = flags and 0x6
        val loop = (flags and 0x1) == LOOP
        val winding = (flags and WINDING_MASK) ushr 24
        // Our RpnFloatEvaluator resolves a NaN var-ref inline (getFloat) AND substitutes VAR1=t, so the
        // raw expression arrays can be evaluated directly per sample — no pre-resolve pass needed.
        val eval: (FloatArray, Float) -> Float = { expr, t -> RpnFloatEvaluator.eval(expr, expr.size, context, t) }
        val path = if ((flags and POLAR) == POLAR) {
            // POLAR: r = X(t), angle = t, centre = the (resolved) 2-element Y array (upstream getPolarPath).
            val center = FloatArray(expressionY.size) { context.resolveCoord(expressionY[it]) }
            PathGenerator.getPolarPath(expressionX, center, rMin, rMax, rCount, mode, loop, eval)
        } else {
            PathGenerator.getPath(expressionX, expressionY, rMin, rMax, rCount, mode, loop, eval)
        }
        context.putPathData(id, path)
        context.putPathWinding(id, winding)
    }

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeInt(flags)
        buffer.writeFloat(min)
        buffer.writeFloat(max)
        buffer.writeFloat(count)
        buffer.writeInt(expressionX.size)
        for (v in expressionX) buffer.writeFloat(v)
        buffer.writeInt(expressionY.size)
        for (v in expressionY) buffer.writeFloat(v)
    }

    override fun dump(): String =
        "PATH_EXPRESSION id=$id flags=$flags min=$min max=$max count=$count " +
            "x=${expressionX.size} y=${expressionY.size}"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is PathExpression &&
                id == other.id && flags == other.flags &&
                min.toRawBits() == other.min.toRawBits() && max.toRawBits() == other.max.toRawBits() &&
                count.toRawBits() == other.count.toRawBits() &&
                rawEquals(expressionX, other.expressionX) && rawEquals(expressionY, other.expressionY)
            )

    override fun hashCode(): Int {
        var h = id
        h = 31 * h + flags
        h = 31 * h + min.toRawBits()
        h = 31 * h + max.toRawBits()
        h = 31 * h + count.toRawBits()
        h = 31 * h + rawHash(expressionX)
        h = 31 * h + rawHash(expressionY)
        return h
    }

    companion object : OperationReader {
        // REM-127 flag bits (upstream PathExpression): mode = flags & 0x6 (0=SPLINE/2=MONOTONIC/4=LINEAR).
        private const val LOOP = 1
        private const val POLAR = 8
        private const val WINDING_MASK = 0x3000000

        /** NaN-safe float-array equality on raw bits (the wire carries NaN-encoded ids). */
        private fun rawEquals(a: FloatArray, b: FloatArray): Boolean {
            if (a.size != b.size) return false
            for (i in a.indices) if (a[i].toRawBits() != b[i].toRawBits()) return false
            return true
        }

        private fun rawHash(a: FloatArray): Int {
            var h = 1
            for (v in a) h = 31 * h + v.toRawBits()
            return h
        }

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val id = buffer.readInt()
            val flags = buffer.readInt()
            val min = buffer.readFloat()
            val max = buffer.readFloat()
            val count = buffer.readFloat()
            val lenX = buffer.readInt()
            val x = FloatArray(lenX) { buffer.readFloat() }
            val lenY = buffer.readInt()
            val y = FloatArray(lenY) { buffer.readFloat() }
            operations += PathExpression(id, flags, min, max, count, x, y)
        }
    }
}
