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
package com.tneff.kmpremotecompose.remote.wire

/**
 * The base communication buffer for the RemoteCompose `.rc` binary format: it encodes and decodes
 * the primitive types the format is built from.
 *
 * This is the byte-exact core of the writer/reader (PROJECT_CONTEXT §2). It is deliberately a plain,
 * growing [ByteArray] with manual big-endian shifting — no `java.*`, no `okio` in the hot path. okio
 * only ever appears at the file boundary (a later story), never here.
 *
 * Invariants that compatibility depends on:
 * - **Big-endian**, fixed width. `int` = 4 B, `short` = 2 B, `long` = 8 B, `float` = 4 B, `double` =
 *   8 B. There is no varint anywhere.
 * - **Raw** float/double bits ([Float.toRawBits] / [Double.toRawBits]). NaN payloads carry ids, so
 *   the canonicalizing [Float.toBits] must never be used on the encode side.
 * - Strings are a 4-byte big-endian length followed by **UTF-8** bytes ([encodeToByteArray]).
 */
class WireBuffer private constructor(
    private var buffer: ByteArray,
    private var sizeInternal: Int,
) {

    /** Create an empty, growing buffer for writing. */
    constructor(initialCapacity: Int = WireTypes.BUFFER_SIZE) :
        this(ByteArray(maxOf(initialCapacity, 1)), 0)

    /** The read/write cursor. Reads and writes happen at this position; advance and back-patch. */
    var byteIndex: Int = 0

    /** The number of meaningful bytes written so far (high-water mark of the cursor). */
    val size: Int
        get() = sizeInternal

    /** True while there are unread bytes between the cursor and [size]. */
    fun available(): Boolean = sizeInternal - byteIndex > 0

    /** Reset the buffer to empty without releasing its backing storage. */
    fun reset() {
        byteIndex = 0
        sizeInternal = 0
    }

    /** Copy the meaningful portion of the buffer into a freshly sized [ByteArray]. */
    fun toByteArray(): ByteArray = buffer.copyOf(sizeInternal)

    private fun ensureCapacity(required: Int) {
        if (required > buffer.size) {
            var newCapacity = buffer.size * 2
            if (newCapacity < required) newCapacity = required
            buffer = buffer.copyOf(newCapacity)
        }
    }

    private fun advanced(written: Int) {
        byteIndex += written
        if (byteIndex > sizeInternal) sizeInternal = byteIndex
    }

    // ---------------------------------------------------------------------------------------------
    // Write
    // ---------------------------------------------------------------------------------------------

    /** Write a boolean as a single byte (`1` = true, `0` = false). */
    fun writeBoolean(value: Boolean) {
        ensureCapacity(byteIndex + 1)
        buffer[byteIndex] = if (value) 1 else 0
        advanced(1)
    }

    /** Write the low 8 bits of [value] as a single byte. */
    fun writeByte(value: Int) {
        ensureCapacity(byteIndex + 1)
        buffer[byteIndex] = value.toByte()
        advanced(1)
    }

    /** Write a 16-bit big-endian short. */
    fun writeShort(value: Int) {
        ensureCapacity(byteIndex + 2)
        buffer[byteIndex] = (value ushr 8 and 0xFF).toByte()
        buffer[byteIndex + 1] = (value and 0xFF).toByte()
        advanced(2)
    }

    /** Write a 32-bit big-endian int. */
    fun writeInt(value: Int) {
        ensureCapacity(byteIndex + 4)
        buffer[byteIndex] = (value ushr 24 and 0xFF).toByte()
        buffer[byteIndex + 1] = (value ushr 16 and 0xFF).toByte()
        buffer[byteIndex + 2] = (value ushr 8 and 0xFF).toByte()
        buffer[byteIndex + 3] = (value and 0xFF).toByte()
        advanced(4)
    }

    /** Write a 64-bit big-endian long. */
    fun writeLong(value: Long) {
        ensureCapacity(byteIndex + 8)
        buffer[byteIndex] = (value ushr 56 and 0xFF).toByte()
        buffer[byteIndex + 1] = (value ushr 48 and 0xFF).toByte()
        buffer[byteIndex + 2] = (value ushr 40 and 0xFF).toByte()
        buffer[byteIndex + 3] = (value ushr 32 and 0xFF).toByte()
        buffer[byteIndex + 4] = (value ushr 24 and 0xFF).toByte()
        buffer[byteIndex + 5] = (value ushr 16 and 0xFF).toByte()
        buffer[byteIndex + 6] = (value ushr 8 and 0xFF).toByte()
        buffer[byteIndex + 7] = (value and 0xFF).toByte()
        advanced(8)
    }

    /** Write a 32-bit IEEE-754 float using its **raw** bit pattern (NaN payloads preserved). */
    fun writeFloat(value: Float) {
        writeInt(value.toRawBits())
    }

    /** Write a 64-bit IEEE-754 double using its **raw** bit pattern (NaN payloads preserved). */
    fun writeDouble(value: Double) {
        writeLong(value.toRawBits())
    }

    /** Overwrite a 32-bit big-endian int at an absolute [position] without moving the cursor. */
    fun overwriteInt(position: Int, value: Int) {
        buffer[position] = (value ushr 24 and 0xFF).toByte()
        buffer[position + 1] = (value ushr 16 and 0xFF).toByte()
        buffer[position + 2] = (value ushr 8 and 0xFF).toByte()
        buffer[position + 3] = (value and 0xFF).toByte()
    }

    /** Write a byte buffer as a 4-byte big-endian length followed by the raw bytes. */
    fun writeBuffer(bytes: ByteArray) {
        ensureCapacity(byteIndex + 4 + bytes.size)
        writeInt(bytes.size)
        bytes.copyInto(buffer, byteIndex)
        advanced(bytes.size)
    }

    /** Write a string as a 4-byte big-endian length followed by its UTF-8 bytes. */
    fun writeUTF8(content: String) {
        writeBuffer(content.encodeToByteArray())
    }

    // ---------------------------------------------------------------------------------------------
    // Read
    // ---------------------------------------------------------------------------------------------

    /** Read a boolean stored as a single byte (`1` = true). */
    fun readBoolean(): Boolean {
        val value = buffer[byteIndex]
        byteIndex++
        return value.toInt() == 1
    }

    /** Read a single unsigned byte as an `Int` in `0..255`. */
    fun readByte(): Int {
        val value = buffer[byteIndex].toInt() and 0xFF
        byteIndex++
        return value
    }

    /** Read a 16-bit big-endian short, returned as an `Int` in `0..65535`. */
    fun readShort(): Int {
        val v1 = (buffer[byteIndex].toInt() and 0xFF) shl 8
        val v2 = (buffer[byteIndex + 1].toInt() and 0xFF)
        byteIndex += 2
        return v1 or v2
    }

    /** Read a 32-bit big-endian int. */
    fun readInt(): Int {
        val v1 = (buffer[byteIndex].toInt() and 0xFF) shl 24
        val v2 = (buffer[byteIndex + 1].toInt() and 0xFF) shl 16
        val v3 = (buffer[byteIndex + 2].toInt() and 0xFF) shl 8
        val v4 = (buffer[byteIndex + 3].toInt() and 0xFF)
        byteIndex += 4
        return v1 or v2 or v3 or v4
    }

    /** Read a 32-bit big-endian int without advancing the cursor. */
    fun peekInt(): Int {
        val v1 = (buffer[byteIndex].toInt() and 0xFF) shl 24
        val v2 = (buffer[byteIndex + 1].toInt() and 0xFF) shl 16
        val v3 = (buffer[byteIndex + 2].toInt() and 0xFF) shl 8
        val v4 = (buffer[byteIndex + 3].toInt() and 0xFF)
        return v1 or v2 or v3 or v4
    }

    /** Read a 64-bit big-endian long. */
    fun readLong(): Long {
        val v1 = (buffer[byteIndex].toLong() and 0xFF) shl 56
        val v2 = (buffer[byteIndex + 1].toLong() and 0xFF) shl 48
        val v3 = (buffer[byteIndex + 2].toLong() and 0xFF) shl 40
        val v4 = (buffer[byteIndex + 3].toLong() and 0xFF) shl 32
        val v5 = (buffer[byteIndex + 4].toLong() and 0xFF) shl 24
        val v6 = (buffer[byteIndex + 5].toLong() and 0xFF) shl 16
        val v7 = (buffer[byteIndex + 6].toLong() and 0xFF) shl 8
        val v8 = (buffer[byteIndex + 7].toLong() and 0xFF)
        byteIndex += 8
        return v1 or v2 or v3 or v4 or v5 or v6 or v7 or v8
    }

    /** Read a 32-bit IEEE-754 float from its raw bit pattern. */
    fun readFloat(): Float = Float.fromBits(readInt())

    /** Read a 64-bit IEEE-754 double from its raw bit pattern. */
    fun readDouble(): Double = Double.fromBits(readLong())

    /** Read a byte buffer encoded as a 4-byte big-endian length followed by that many bytes. */
    fun readBuffer(): ByteArray {
        val count = readInt()
        val out = buffer.copyOfRange(byteIndex, byteIndex + count)
        byteIndex += count
        return out
    }

    /**
     * Read a byte buffer, rejecting a length outside `0..maxSize`. This is the guarded form used
     * when reading untrusted documents.
     */
    fun readBuffer(maxSize: Int): ByteArray {
        val count = readInt()
        if (count < 0 || count > maxSize) {
            throw IllegalStateException("attempt to read a buffer of invalid size 0 <= $count <= $maxSize")
        }
        val out = buffer.copyOfRange(byteIndex, byteIndex + count)
        byteIndex += count
        return out
    }

    /** Read a UTF-8 string encoded as a length-prefixed byte buffer. */
    fun readUTF8(): String = readBuffer().decodeToString()

    /** Read a UTF-8 string, rejecting a length outside `0..maxSize`. */
    fun readUTF8(maxSize: Int): String = readBuffer(maxSize).decodeToString()

    companion object {
        /**
         * Wrap an existing `.rc` byte array for reading. The cursor starts at 0 and [size] is the
         * array length. The array is used directly (not copied) and must not be mutated externally.
         */
        fun fromBytes(bytes: ByteArray): WireBuffer = WireBuffer(bytes, bytes.size)
    }
}
