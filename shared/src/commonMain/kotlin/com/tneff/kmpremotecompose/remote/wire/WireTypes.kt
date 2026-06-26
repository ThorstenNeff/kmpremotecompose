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
 * Wire-format constants for the RemoteCompose `.rc` binary document.
 *
 * These values mirror the upstream AndroidX reference byte-for-byte and are the basis of
 * `.rc` binary compatibility (PROJECT_CONTEXT §2). They are intentionally NOT "tidied up":
 * a prettier-but-different value here breaks compatibility.
 */
object WireTypes {

    // ---------------------------------------------------------------------------------------------
    // Header property value types (Header map-based encoding, API >= 7).
    // A header TLV tag packs the data type in the high bits and the property key in the low bits.
    // ---------------------------------------------------------------------------------------------

    /** The property value is a 32-bit signed integer. */
    const val DATA_TYPE_INT: Int = 0

    /** The property value is a 32-bit IEEE-754 float. */
    const val DATA_TYPE_FLOAT: Int = 1

    /** The property value is a 64-bit signed long. */
    const val DATA_TYPE_LONG: Int = 2

    /** The property value is a UTF-8 encoded string. */
    const val DATA_TYPE_STRING: Int = 3

    /** Bit shift applied to the data type when packing it into a header TLV tag (`type shl 10`). */
    const val TAG_TYPE_SHIFT: Int = 10

    /** Mask isolating the property key from a header TLV tag (`tag and 0x3F`). */
    const val TAG_KEY_MASK: Int = 0x3F

    /** Magic number identifying the protocol; lives in the high 16 bits of the major-version int. */
    const val MAGIC_NUMBER: Int = 0x048C0000

    /** Mask isolating the protocol major version from a magic-tagged major-version int. */
    const val MAGIC_MAJOR_MASK: Int = 0xFFFF

    // ---------------------------------------------------------------------------------------------
    // NaN-encoded id helpers.
    //
    // RemoteCompose transports ids as NaN float bit patterns so that a single float field can carry
    // either a literal value or an id reference. The encode side MUST use raw float bits
    // (`Float.toRawBits` / `Float.fromBits`) so the NaN payload survives; the canonicalizing
    // `Float.toBits` would collapse the payload and destroy the id.
    // ---------------------------------------------------------------------------------------------

    /** OR-mask turning a 22-bit id into a NaN float bit pattern (`id or 0xFF800000`). */
    const val ID_NAN_MASK: Int = -0x800000 // == 0xFF800000

    /** Mask isolating the 22-bit id payload from a NaN-encoded float's raw bits. */
    const val ID_PAYLOAD_MASK: Int = 0x3FFFFF

    // ---------------------------------------------------------------------------------------------
    // Limits (mirror of the upstream `Limits` class — used for buffer sizing and read guards).
    // ---------------------------------------------------------------------------------------------

    /** Default initial capacity of a [WireBuffer], in bytes. */
    const val BUFFER_SIZE: Int = 1024 * 1024

    /** Maximum number of entries in the header property table. */
    const val MAX_TABLE_SIZE: Int = 1000

    /** Maximum length, in bytes, of a single encoded string. */
    const val MAX_STRING_SIZE: Int = 4000

    /**
     * Encode a 22-bit integer id as a NaN float (raw bits preserved).
     *
     * @param id the id to encode
     * @return the id as a NaN float
     */
    fun asNan(id: Int): Float = Float.fromBits(id or ID_NAN_MASK)

    /**
     * Decode a NaN-encoded float back into its 22-bit integer id.
     *
     * @param value the NaN float to decode
     * @return the id payload
     */
    fun idFromNan(value: Float): Int = value.toRawBits() and ID_PAYLOAD_MASK

    // ---------------------------------------------------------------------------------------------
    // NaN id classification (REM-36, Eval-Engine E1). A NaN-encoded float is one of several classes
    // distinguished by its 23-bit region payload `>> 20` (upstream `NanMap`): 0 = system variable,
    // 1 = normal variable, 2 = **data** variable (resolves to a stored DATA_FLOAT/INT/COLOR value),
    // 3 = operation (math/RPN operator). The eval engine resolves coords that are data variables.
    // ---------------------------------------------------------------------------------------------

    /** Mask isolating the 23-bit region+id payload from a NaN float's raw bits (upstream `fromNaN`). */
    const val ID_REGION_PAYLOAD_MASK: Int = 0x7FFFFF

    /** The 23-bit region+id payload of a NaN float (upstream `NanMap.fromNaN`). */
    fun fromNaN(value: Float): Int = value.toRawBits() and ID_REGION_PAYLOAD_MASK

    /** The class region of a NaN id (`fromNaN >> 20`): 0=system, 1=normal, 2=data, 3=operation. */
    fun nanRegion(value: Float): Int = fromNaN(value) shr 20

    /** True if [value] is a **data** variable NaN id (region 2) — resolves to a stored value. */
    fun isDataVariable(value: Float): Boolean = value.isNaN() && nanRegion(value) == 2

    /** True if [value] is a math/RPN **operation** NaN id (region 3) — evaluated by the RPN engine (E2). */
    fun isOperationVariable(value: Float): Boolean = value.isNaN() && nanRegion(value) == 3
}
