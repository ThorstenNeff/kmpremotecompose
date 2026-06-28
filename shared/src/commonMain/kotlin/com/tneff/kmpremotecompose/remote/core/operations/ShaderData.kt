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
import com.tneff.kmpremotecompose.remote.wire.WireTypes

/**
 * Shader data (`DATA_SHADER`): a shader program ([shaderTextId]) plus its named float/int/bitmap
 * uniforms, bound to [shaderId].
 *
 * Wire layout: opcode, `int shaderId`, `int shaderTextId`, `int sizes` packing the three uniform
 * map sizes (`floatSize | intSize<<8 | bitmapSize<<16`), then the float uniforms, the int uniforms
 * and the bitmap uniforms. Each float/int uniform is a name + length + values; each bitmap uniform
 * is a name + an int. Entry order is preserved (ordered lists) so a decoded shader re-encodes
 * byte-for-byte even though upstream emits from an unordered map.
 *
 * Layer: V6 base and the V7 androidx overlay (NOT V7_BASE, NOT V7_WIDGETS).
 */
class ShaderData(
    val shaderId: Int,
    val shaderTextId: Int,
    val floatUniforms: List<FloatUniform> = emptyList(),
    val intUniforms: List<IntUniform> = emptyList(),
    val bitmapUniforms: List<BitmapUniform> = emptyList(),
) : Operation, VariableSupport {

    class FloatUniform(val name: String, val values: FloatArray) {
        override fun equals(other: Any?): Boolean =
            this === other || (other is FloatUniform && name == other.name && values.contentEquals(other.values))

        override fun hashCode(): Int = 31 * name.hashCode() + values.contentHashCode()
    }

    class IntUniform(val name: String, val values: IntArray) {
        override fun equals(other: Any?): Boolean =
            this === other || (other is IntUniform && name == other.name && values.contentEquals(other.values))

        override fun hashCode(): Int = 31 * name.hashCode() + values.contentHashCode()
    }

    data class BitmapUniform(val name: String, val bitmapId: Int)

    override val opcode: Int get() = Operations.DATA_SHADER

    /**
     * REM-77 (Epic D, DATA_SHADER): Phase-A producer — register this shader (source + uniforms) into the
     * context's shader store by [shaderId] so the `SHADER` paint-bundle tag can resolve it at paint time
     * (mirrors upstream `ShaderData` landing in `RemoteComposeState`). Decode (write/read) is untouched —
     * byte-format invariant preserved. Uniform NaN var-ref resolution (`updateVariables`) is a follow-up.
     */
    override fun apply(context: RemoteContext) {
        context.loadShaderData(shaderId, this)
    }

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(shaderId)
        buffer.writeInt(shaderTextId)
        val sizes = (floatUniforms.size and 0xFF) or
            ((intUniforms.size and 0xFF) shl 8) or
            ((bitmapUniforms.size and 0xFF) shl 16)
        buffer.writeInt(sizes)
        for (u in floatUniforms) {
            buffer.writeUTF8(u.name)
            buffer.writeInt(u.values.size)
            for (v in u.values) buffer.writeFloat(v)
        }
        for (u in intUniforms) {
            buffer.writeUTF8(u.name)
            buffer.writeInt(u.values.size)
            for (v in u.values) buffer.writeInt(v)
        }
        for (u in bitmapUniforms) {
            buffer.writeUTF8(u.name)
            buffer.writeInt(u.bitmapId)
        }
    }

    override fun dump(): String =
        "DATA_SHADER id=$shaderId text=$shaderTextId floats=${floatUniforms.size} ints=${intUniforms.size} bitmaps=${bitmapUniforms.size}"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is ShaderData && shaderId == other.shaderId && shaderTextId == other.shaderTextId &&
                floatUniforms == other.floatUniforms && intUniforms == other.intUniforms &&
                bitmapUniforms == other.bitmapUniforms)

    override fun hashCode(): Int {
        var r = shaderId
        r = 31 * r + shaderTextId
        r = 31 * r + floatUniforms.hashCode()
        r = 31 * r + intUniforms.hashCode()
        r = 31 * r + bitmapUniforms.hashCode()
        return r
    }

    companion object : OperationReader {
        /** Mirror of the upstream `Limits.MAX_SHADER_FLOAT_COUNT`. */
        const val MAX_SHADER_FLOAT_COUNT = 200

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val shaderId = buffer.readInt()
            val shaderTextId = buffer.readInt()
            val sizes = buffer.readInt()
            val floatSize = sizes and 0xFF
            val intSize = (sizes shr 8) and 0xFF
            val bitmapSize = (sizes shr 16) and 0xFF

            val floats = ArrayList<FloatUniform>(floatSize)
            repeat(floatSize) {
                val name = buffer.readUTF8(WireTypes.MAX_STRING_SIZE)
                val len = buffer.readInt()
                if (len < 0 || len > MAX_SHADER_FLOAT_COUNT) throw IllegalStateException("shader float array too long: $len")
                floats += FloatUniform(name, FloatArray(len) { buffer.readFloat() })
            }
            val ints = ArrayList<IntUniform>(intSize)
            repeat(intSize) {
                val name = buffer.readUTF8(WireTypes.MAX_STRING_SIZE)
                val len = buffer.readInt()
                if (len < 0 || len > MAX_SHADER_FLOAT_COUNT) throw IllegalStateException("shader int array too long: $len")
                ints += IntUniform(name, IntArray(len) { buffer.readInt() })
            }
            val bitmaps = ArrayList<BitmapUniform>(bitmapSize)
            repeat(bitmapSize) {
                val name = buffer.readUTF8(WireTypes.MAX_STRING_SIZE)
                bitmaps += BitmapUniform(name, buffer.readInt())
            }
            operations += ShaderData(shaderId, shaderTextId, floats, ints, bitmaps)
        }
    }
}
