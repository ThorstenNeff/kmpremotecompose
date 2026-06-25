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
package com.tneff.kmpremotecompose.remote.core.operations.layout

import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.OperationReader
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * `TOUCH_EXPRESSION` (opcode [Operations.TOUCH_EXPRESSION]) — a touch-driven value expression with
 * stop and easing specs.
 *
 * Wire layout (mirrors upstream `TouchExpression.apply`/`read`): opcode byte + int `id` + float `value`
 * + float `min` + float `max` + float `velocityId` + int `touchEffects` + int `expLen` + `expLen`×float
 * + int `stopLogic` (packs `touchMode << 16 | stopLen`) + `stopLen`×float + int `easingLen` +
 * `easingLen`×float. [stopLogic] is carried verbatim so the packed `touchMode` high bits round-trip;
 * all float values may be NaN-encoded ids (raw bits preserved).
 */
class TouchExpression(
    val id: Int,
    val value: Float,
    val min: Float,
    val max: Float,
    val velocityId: Float,
    val touchEffects: Int,
    val exp: FloatArray,
    val stopLogic: Int,
    val stops: FloatArray,
    val easing: FloatArray,
) : Operation {

    override val opcode: Int get() = Operations.TOUCH_EXPRESSION

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeFloat(value)
        buffer.writeFloat(min)
        buffer.writeFloat(max)
        buffer.writeFloat(velocityId)
        buffer.writeInt(touchEffects)
        buffer.writeInt(exp.size)
        for (v in exp) buffer.writeFloat(v)
        buffer.writeInt(stopLogic)
        for (v in stops) buffer.writeFloat(v)
        buffer.writeInt(easing.size)
        for (v in easing) buffer.writeFloat(v)
    }

    override fun dump(): String =
        "TOUCH_EXPRESSION id=$id value=$value min=$min max=$max velocityId=$velocityId " +
            "touchEffects=$touchEffects exp=${exp.size} stopLogic=$stopLogic stops=${stops.size} easing=${easing.size}"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is TouchExpression &&
                id == other.id &&
                value.toRawBits() == other.value.toRawBits() && min.toRawBits() == other.min.toRawBits() &&
                max.toRawBits() == other.max.toRawBits() && velocityId.toRawBits() == other.velocityId.toRawBits() &&
                touchEffects == other.touchEffects && stopLogic == other.stopLogic &&
                rawEquals(exp, other.exp) && rawEquals(stops, other.stops) && rawEquals(easing, other.easing)
            )

    override fun hashCode(): Int {
        var h = id
        h = 31 * h + value.toRawBits()
        h = 31 * h + min.toRawBits()
        h = 31 * h + max.toRawBits()
        h = 31 * h + velocityId.toRawBits()
        h = 31 * h + touchEffects
        h = 31 * h + rawHash(exp)
        h = 31 * h + stopLogic
        h = 31 * h + rawHash(stops)
        h = 31 * h + rawHash(easing)
        return h
    }

    companion object : OperationReader {
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
            val value = buffer.readFloat()
            val min = buffer.readFloat()
            val max = buffer.readFloat()
            val velocityId = buffer.readFloat()
            val touchEffects = buffer.readInt()
            val expLen = buffer.readInt() and 0xFFFF
            val exp = FloatArray(expLen) { buffer.readFloat() }
            val stopLogic = buffer.readInt()
            val stops = FloatArray(stopLogic and 0xFFFF) { buffer.readFloat() }
            val easingLen = buffer.readInt()
            val easing = FloatArray(easingLen) { buffer.readFloat() }
            operations += TouchExpression(id, value, min, max, velocityId, touchEffects, exp, stopLogic, stops, easing)
        }
    }
}
