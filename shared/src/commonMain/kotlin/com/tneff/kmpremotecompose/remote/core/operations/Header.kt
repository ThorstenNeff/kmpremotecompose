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

import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import com.tneff.kmpremotecompose.remote.wire.WireTypes

/**
 * The document header — always the first operation in a `.rc` stream (opcode [Operations.HEADER]).
 *
 * It carries the protocol version, the document's original size and density, a capabilities mask and
 * (API ≥ 7) a sorted property table. The header is the most byte-critical operation in the format —
 * every fixture hits it first — so the encoding here mirrors the upstream reference exactly:
 *
 * - **Flat form (API < 7):** version × 3, width, height, capabilities (long). Density is **not**
 *   written (it is supplied by the player at runtime — a `.rc` byte-compatibility invariant).
 * - **Map form (API ≥ 7):** `major | MAGIC`, minor, patch, entry count, then TLV entries sorted by
 *   key. Each entry is `short tag = key | (dataType shl 10)`, a `short` item length, then the value.
 *   A STRING entry writes its length twice — once in the item-length short (`len + 4`) and once via
 *   the length-prefixed buffer; the reader trusts the buffer's length and discards the short.
 *
 * Pulled forward from REM-4 as the round-trip anchor for the REM-3 framework.
 */
class Header private constructor(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val properties: Map<Int, Any>?,
    private val flatWidth: Int,
    private val flatHeight: Int,
    private val flatDensity: Float,
    private val flatCapabilities: Long,
) : Operation {

    override val opcode: Int get() = Operations.HEADER

    /** Document width in pixels (from the property table, or the flat field). */
    val width: Int get() = (properties?.get(DOC_WIDTH) as? Int) ?: flatWidth

    /** Document height in pixels. */
    val height: Int get() = (properties?.get(DOC_HEIGHT) as? Int) ?: flatHeight

    /** Density at generation. Never serialized; defaults to 1f (player supplies the real value). */
    val density: Float get() = (properties?.get(DOC_DENSITY_AT_GENERATION) as? Float) ?: flatDensity

    /** The DOC_PROFILES bitmask that selects the decode overlays for the rest of the document. */
    val profiles: Int get() = (properties?.get(DOC_PROFILES) as? Int) ?: 0

    /** The API level implied by this header's version. */
    val apiLevel: Int get() = versionToApiLevel(major, minor)

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(Operations.HEADER)
        val props = properties
        // Write THIS header's version, not the lib constants: a round-tripped document must preserve
        // its parsed version byte-for-byte (e.g. flat v1.0.0 must not re-encode as v1.1.0). New
        // documents still stamp the current version because the factories seed major/minor/patch with
        // the MAJOR/MINOR/PATCH constants.
        if (props != null && props.isNotEmpty()) {
            check(apiLevel >= 7) { "Header has properties but apiLevel $apiLevel < 7" }
            buffer.writeInt(major or WireTypes.MAGIC_NUMBER)
            buffer.writeInt(minor)
            buffer.writeInt(patch)
            buffer.writeInt(props.size)
            writeMap(buffer, props)
        } else {
            buffer.writeInt(major)
            buffer.writeInt(minor)
            buffer.writeInt(patch)
            buffer.writeInt(flatWidth)
            buffer.writeInt(flatHeight)
            buffer.writeLong(flatCapabilities)
            // density intentionally not written
        }
    }

    override fun dump(): String =
        "HEADER v$major.$minor.$patch w=$width h=$height profiles=$profiles" +
            // sortedBy is multiplatform stdlib; toSortedMap() is JVM-only and breaks Kotlin/Native.
            (properties?.let { " props=${it.entries.sortedBy { e -> e.key }}" } ?: " [$flatCapabilities]")

    companion object : OperationReader {

        // Current protocol version stamped on write (API level 7).
        const val MAJOR_VERSION = 1
        const val MINOR_VERSION = 1
        const val PATCH_VERSION = 0

        /** Mask isolating the high 16 bits (the magic) of a magic-tagged major-version int. */
        private const val MAGIC_HIGH_MASK = -0x10000 // 0xFFFF0000

        // Property keys (subset; full set lands with REM-4 op group A).
        const val DOC_WIDTH = 5
        const val DOC_HEIGHT = 6
        const val DOC_DENSITY_AT_GENERATION = 7
        const val DOC_DESIRED_FPS = 8
        const val DOC_CONTENT_DESCRIPTION = 9
        const val DOC_SOURCE = 11
        const val DOC_DATA_UPDATE = 12
        const val HOST_EXCEPTION_HANDLER = 13
        const val DOC_PROFILES = 14

        /** Build a map-form (API ≥ 7) header from a property table. */
        fun fromProperties(properties: Map<Int, Any>): Header =
            Header(MAJOR_VERSION, MINOR_VERSION, PATCH_VERSION, properties, 256, 256, 1f, 0L)

        /** Build a flat-form (API < 7) header. */
        fun flat(width: Int, height: Int, capabilities: Long = 0L): Header =
            Header(MAJOR_VERSION, MINOR_VERSION, PATCH_VERSION, null, width, height, 1f, capabilities)

        /**
         * Peek the api level from a buffer positioned at the very start, without consuming it.
         * Returns -1 if the first operation is not a header.
         */
        fun peekApiLevel(buffer: WireBuffer): Int {
            check(buffer.byteIndex == 0) { "can only peek the header at buffer start" }
            val op = buffer.readByte()
            if (op != Operations.HEADER) {
                buffer.byteIndex = 0
                return -1
            }
            val major = buffer.readInt()
            val minor = buffer.readInt()
            buffer.byteIndex = 0
            return versionToApiLevel(major, minor)
        }

        /** Map a (major, minor) version to its api level, or -1 if unknown. */
        fun versionToApiLevel(major: Int, minor: Int): Int {
            var m = major
            if (m >= 0x10000) {
                if ((m and 0xFFFF.inv()) != WireTypes.MAGIC_NUMBER) return -1
                m = m and WireTypes.MAGIC_MAJOR_MASK
            }
            return when {
                m == 1 && minor == 2 -> 8
                m == 1 && minor == 1 -> 7
                m == 1 && minor == 0 -> 6
                m == 0 && minor <= 3 -> 6
                else -> -1
            }
        }

        /** Decode a header (opcode already consumed) and append it to [operations]. */
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            var major = buffer.readInt()
            val minor = buffer.readInt()
            val patch = buffer.readInt()
            if (major < 0x10000) {
                val width = buffer.readInt()
                val height = buffer.readInt()
                val capabilities = buffer.readLong()
                operations += Header(major, minor, patch, null, width, height, 1f, capabilities)
                return
            }
            // Fail closed on a corrupt/foreign magic instead of silently masking it away — a `.rc`
            // is untrusted input, and the rest of the reader is fail-closed (table size, opcodes).
            if ((major and MAGIC_HIGH_MASK) != WireTypes.MAGIC_NUMBER) {
                throw IllegalStateException(
                    "invalid header magic 0x${(major and MAGIC_HIGH_MASK).toUInt().toString(16)} " +
                        "!= 0x${WireTypes.MAGIC_NUMBER.toUInt().toString(16)}",
                )
            }
            major = major and WireTypes.MAGIC_MAJOR_MASK
            val count = buffer.readInt()
            if (count < 0 || count > WireTypes.MAX_TABLE_SIZE) {
                throw IllegalStateException("invalid header table size $count")
            }
            val props = LinkedHashMap<Int, Any>(count)
            repeat(count) {
                val tag = buffer.readShort()
                buffer.readShort() // item length — redundant, discarded
                val dataType = tag shr WireTypes.TAG_TYPE_SHIFT
                val key = tag and WireTypes.TAG_KEY_MASK
                props[key] = when (dataType) {
                    WireTypes.DATA_TYPE_INT -> buffer.readInt()
                    WireTypes.DATA_TYPE_FLOAT -> buffer.readFloat()
                    WireTypes.DATA_TYPE_LONG -> buffer.readLong()
                    WireTypes.DATA_TYPE_STRING -> buffer.readUTF8(WireTypes.MAX_STRING_SIZE)
                    else -> throw IllegalStateException("unknown header data type $dataType")
                }
            }
            operations += Header(major, minor, patch, props, 256, 256, 1f, 0L)
        }

        private fun writeMap(buffer: WireBuffer, properties: Map<Int, Any>) {
            // Keys sorted ascending for deterministic, byte-stable output.
            for (key in properties.keys.sorted()) {
                when (val value = properties.getValue(key)) {
                    is String -> {
                        buffer.writeShort(key or (WireTypes.DATA_TYPE_STRING shl WireTypes.TAG_TYPE_SHIFT))
                        val data = value.encodeToByteArray()
                        buffer.writeShort(data.size + 4)
                        buffer.writeBuffer(data)
                    }

                    is Int -> {
                        buffer.writeShort(key or (WireTypes.DATA_TYPE_INT shl WireTypes.TAG_TYPE_SHIFT))
                        buffer.writeShort(4)
                        buffer.writeInt(value)
                    }

                    is Float -> {
                        buffer.writeShort(key or (WireTypes.DATA_TYPE_FLOAT shl WireTypes.TAG_TYPE_SHIFT))
                        buffer.writeShort(4)
                        buffer.writeFloat(value)
                    }

                    is Long -> {
                        buffer.writeShort(key or (WireTypes.DATA_TYPE_LONG shl WireTypes.TAG_TYPE_SHIFT))
                        buffer.writeShort(8)
                        buffer.writeLong(value)
                    }

                    else -> throw IllegalArgumentException("unsupported header property type: $value")
                }
            }
        }
    }
}
