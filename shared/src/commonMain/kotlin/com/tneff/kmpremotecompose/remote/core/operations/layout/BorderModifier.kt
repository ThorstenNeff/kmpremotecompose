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
 * `MODIFIER_BORDER` (opcode [Operations.MODIFIER_BORDER]) — draws a border around a component.
 *
 * Wire layout: opcode byte + int `flags` + int `colorId` + int `reserve1` + int `reserve2` +
 * float `borderWidth` + float `roundedCorner` + float `r` + float `g` + float `b` + float `a` +
 * int `shapeType` (mirrors upstream `BorderModifierOperation.apply`/`read`).
 */
class BorderModifier(
    val flags: Int,
    val colorId: Int,
    val reserve1: Int,
    val reserve2: Int,
    val borderWidth: Float,
    val roundedCorner: Float,
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float,
    val shapeType: Int,
) : Operation {

    override val opcode: Int get() = Operations.MODIFIER_BORDER

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(flags)
        buffer.writeInt(colorId)
        buffer.writeInt(reserve1)
        buffer.writeInt(reserve2)
        buffer.writeFloat(borderWidth)
        buffer.writeFloat(roundedCorner)
        buffer.writeFloat(r)
        buffer.writeFloat(g)
        buffer.writeFloat(b)
        buffer.writeFloat(a)
        buffer.writeInt(shapeType)
    }

    override fun dump(): String =
        "MODIFIER_BORDER flags=$flags colorId=$colorId reserve1=$reserve1 reserve2=$reserve2 " +
            "borderWidth=$borderWidth roundedCorner=$roundedCorner r=$r g=$g b=$b a=$a " +
            "shapeType=$shapeType"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is BorderModifier &&
                flags == other.flags && colorId == other.colorId &&
                reserve1 == other.reserve1 && reserve2 == other.reserve2 &&
                borderWidth.toRawBits() == other.borderWidth.toRawBits() &&
                roundedCorner.toRawBits() == other.roundedCorner.toRawBits() &&
                r.toRawBits() == other.r.toRawBits() && g.toRawBits() == other.g.toRawBits() &&
                b.toRawBits() == other.b.toRawBits() && a.toRawBits() == other.a.toRawBits() &&
                shapeType == other.shapeType
            )

    override fun hashCode(): Int {
        var h = flags
        h = 31 * h + colorId
        h = 31 * h + reserve1
        h = 31 * h + reserve2
        h = 31 * h + borderWidth.toRawBits()
        h = 31 * h + roundedCorner.toRawBits()
        h = 31 * h + r.toRawBits()
        h = 31 * h + g.toRawBits()
        h = 31 * h + b.toRawBits()
        h = 31 * h + a.toRawBits()
        h = 31 * h + shapeType
        return h
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += BorderModifier(
                buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readInt(),
            )
        }
    }
}
