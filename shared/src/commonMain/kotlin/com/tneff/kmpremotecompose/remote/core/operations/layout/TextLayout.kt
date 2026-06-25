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
 * `LAYOUT_TEXT` (opcode [Operations.LAYOUT_TEXT]) — a text layout component.
 *
 * Wire layout: opcode byte + int `componentId` + int `animationId` + int `textId` + int `color` +
 * float `fontSize` + int `fontStyle` + float `fontWeight` + int `fontFamilyId` + int `textAlign` +
 * int `overflow` + int `maxLines` (mirrors upstream `TextLayout.apply`/`read`).
 */
class TextLayout(
    val componentId: Int,
    val animationId: Int,
    val textId: Int,
    val color: Int,
    val fontSize: Float,
    val fontStyle: Int,
    val fontWeight: Float,
    val fontFamilyId: Int,
    val textAlign: Int,
    val overflow: Int,
    val maxLines: Int,
) : Operation {

    override val opcode: Int get() = Operations.LAYOUT_TEXT

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(componentId)
        buffer.writeInt(animationId)
        buffer.writeInt(textId)
        buffer.writeInt(color)
        buffer.writeFloat(fontSize)
        buffer.writeInt(fontStyle)
        buffer.writeFloat(fontWeight)
        buffer.writeInt(fontFamilyId)
        buffer.writeInt(textAlign)
        buffer.writeInt(overflow)
        buffer.writeInt(maxLines)
    }

    override fun dump(): String =
        "LAYOUT_TEXT id=$componentId anim=$animationId text=$textId color=$color " +
            "fontSize=$fontSize fontStyle=$fontStyle fontWeight=$fontWeight " +
            "fontFamily=$fontFamilyId textAlign=$textAlign overflow=$overflow maxLines=$maxLines"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is TextLayout &&
                componentId == other.componentId && animationId == other.animationId &&
                textId == other.textId && color == other.color &&
                fontSize.toRawBits() == other.fontSize.toRawBits() &&
                fontStyle == other.fontStyle &&
                fontWeight.toRawBits() == other.fontWeight.toRawBits() &&
                fontFamilyId == other.fontFamilyId && textAlign == other.textAlign &&
                overflow == other.overflow && maxLines == other.maxLines
            )

    override fun hashCode(): Int {
        var h = componentId
        h = 31 * h + animationId
        h = 31 * h + textId
        h = 31 * h + color
        h = 31 * h + fontSize.toRawBits()
        h = 31 * h + fontStyle
        h = 31 * h + fontWeight.toRawBits()
        h = 31 * h + fontFamilyId
        h = 31 * h + textAlign
        h = 31 * h + overflow
        h = 31 * h + maxLines
        return h
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += TextLayout(
                buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readFloat(),
                buffer.readInt(), buffer.readFloat(), buffer.readInt(), buffer.readInt(), buffer.readInt(),
                buffer.readInt(),
            )
        }
    }
}
