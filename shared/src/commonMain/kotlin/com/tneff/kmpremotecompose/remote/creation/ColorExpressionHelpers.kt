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
package com.tneff.kmpremotecompose.remote.creation

import com.tneff.kmpremotecompose.remote.core.operations.ColorExpression

/**
 * REM-92 (E4) — `COLOR_EXPRESSIONS` mode-specific builders. Each helper packs the four-int wire
 * format that the player's `ColorExpression.apply` decodes (mirrors upstream
 * `RemoteComposeWriter.addColorExpression` overloads):
 *  - **param1.lowByte = mode**: 0..3 = two-colour interpolate (`bit0` = `c1` is a colorId,
 *    `bit1` = `c2` is a colorId); 4 = HSV; 5 = ARGB-float; 6 = ID-ARGB (alpha-by-id).
 *  - **param1.high16** holds: alpha-byte (mode 4 HSV, 0..255), `alpha * 1024` (mode 5 ARGB),
 *    alpha colourId (mode 6 ID-ARGB), unused (modes 0..3).
 *  - **param2/3/4** are the channel slots (raw float bits for HSV/ARGB, raw ARGB-int or colourId
 *    for modes 0..3); `tween` lives in `param4` for tween modes.
 *
 * Closes the COLOR_EXPRESSIONS slot from `procedure_text_path_effects` (the fourth E5 fixture).
 *
 * **id-allocation:** each helper pulls one **region-0** id and returns it.
 */

/**
 * Interpolate `tween` of the way from [color1] to [color2]. Both are literal ARGB ints (mode 0).
 * `tween` is a literal float (`0f..1f`, gamma-2.2 interpolation matching upstream
 * `Utils.interpolateColor`).
 */
fun RemoteComposeContext.colorExpressionInterpolate(
    color1: Int,
    color2: Int,
    tween: Float,
): Int {
    val id = ids.nextId()
    add(ColorExpression(id = id, param1 = 0, param2 = color1, param3 = color2, param4 = tween.toRawBits()))
    return id
}

/**
 * Interpolate between [color1] (colourId reference) and [color2] (literal). Mode 1 (bit0 set).
 */
fun RemoteComposeContext.colorExpressionInterpolateIdLit(
    colorId1: Int,
    color2: Int,
    tween: Float,
): Int {
    val id = ids.nextId()
    add(ColorExpression(id = id, param1 = 1, param2 = colorId1, param3 = color2, param4 = tween.toRawBits()))
    return id
}

/**
 * Interpolate between [color1] (literal) and [colorId2] (colourId reference). Mode 2 (bit1 set).
 */
fun RemoteComposeContext.colorExpressionInterpolateLitId(
    color1: Int,
    colorId2: Int,
    tween: Float,
): Int {
    val id = ids.nextId()
    add(ColorExpression(id = id, param1 = 2, param2 = color1, param3 = colorId2, param4 = tween.toRawBits()))
    return id
}

/**
 * Interpolate between two colourId references. Mode 3 (bit0 + bit1 set).
 */
fun RemoteComposeContext.colorExpressionInterpolateIds(
    colorId1: Int,
    colorId2: Int,
    tween: Float,
): Int {
    val id = ids.nextId()
    add(ColorExpression(id = id, param1 = 3, param2 = colorId1, param3 = colorId2, param4 = tween.toRawBits()))
    return id
}

/**
 * HSV colour (mode 4). [hue]/[saturation]/[value] are floats (may be NaN-encoded variable refs);
 * [alpha] is an opaque byte (0..255, default 255 = fully opaque) packed into `param1.high16`.
 */
fun RemoteComposeContext.colorExpressionHsv(
    hue: Float,
    saturation: Float,
    value: Float,
    alpha: Int = 255,
): Int {
    require(alpha in 0..255) { "HSV alpha must be 0..255, was $alpha" }
    val id = ids.nextId()
    add(
        ColorExpression(
            id = id,
            param1 = 4 or (alpha shl 16),
            param2 = hue.toRawBits(),
            param3 = saturation.toRawBits(),
            param4 = value.toRawBits(),
        ),
    )
    return id
}

/**
 * Float-channel ARGB colour (mode 5). All channels are `0f..1f` floats (may be NaN-encoded variable
 * refs for [red]/[green]/[blue]); [alpha] is quantised as `(alpha * 1024).toInt()` into
 * `param1.high16` — upstream's encoding (the player re-derives the float via `/ 1024f`).
 */
fun RemoteComposeContext.colorExpressionArgb(
    alpha: Float,
    red: Float,
    green: Float,
    blue: Float,
): Int {
    val alphaQuantised = (alpha * 1024f).toInt()
    val id = ids.nextId()
    add(
        ColorExpression(
            id = id,
            param1 = 5 or (alphaQuantised shl 16),
            param2 = red.toRawBits(),
            param3 = green.toRawBits(),
            param4 = blue.toRawBits(),
        ),
    )
    return id
}

/**
 * Float-channel ARGB colour with alpha-by-id (mode 6). [alphaId] is a previously bound float id
 * (e.g. another `floatExpression` result), packed into `param1.high16`; the player resolves it via
 * `asNan(alphaId)` at apply time. [red]/[green]/[blue] are float-channel literals (may be NaN refs).
 */
fun RemoteComposeContext.colorExpressionArgbById(
    alphaId: Int,
    red: Float,
    green: Float,
    blue: Float,
): Int {
    require(alphaId in 0..0xFFFF) { "alphaId must fit in 16 bits, was $alphaId" }
    val id = ids.nextId()
    add(
        ColorExpression(
            id = id,
            param1 = 6 or (alphaId shl 16),
            param2 = red.toRawBits(),
            param3 = green.toRawBits(),
            param4 = blue.toRawBits(),
        ),
    )
    return id
}

/**
 * NamedVariable type constants (mirrors upstream `NamedVariable.STRING_TYPE` / `FLOAT_TYPE` /
 * `COLOR_TYPE` / `IMAGE_TYPE` / `INT_TYPE` / `LONG_TYPE` / `FLOAT_ARRAY_TYPE`).
 */
const val NAMED_STRING_TYPE: Int = 0
const val NAMED_FLOAT_TYPE: Int = 1
const val NAMED_COLOR_TYPE: Int = 2
const val NAMED_IMAGE_TYPE: Int = 3
const val NAMED_INT_TYPE: Int = 4
const val NAMED_LONG_TYPE: Int = 5
const val NAMED_FLOAT_ARRAY_TYPE: Int = 6

/**
 * `NAMED_VARIABLE` (allocate-and-emit) — allocate a fresh **region-0** id and emit a name binding
 * for it. Returns the allocated id. Mirrors upstream `RemoteComposeWriter.createNamedVariable`
 * (which also pulls from the plain pool — verified against `color_table.rc` oracle:
 * `NAMED_VARIABLE id=50` is region-0, not region-1).
 *
 * **Byte-blocker fix (REM-92 review).** An earlier version of this helper pulled from a separate
 * region-1 counter at `(1 shl 20) + 42 = 1048618`; upstream never does — its `NanMap.TYPE_VARIABLE`
 * region is creation-vestigial. Using region-1 would have produced byte-divergent documents and a
 * name→id-registry mismatch (consumers reference the plain id; a region-1 key would never resolve).
 *
 * For the typical pattern "bind a name to a freshly-created colour", prefer the dedicated helper
 * (e.g. an `addNamedColor(name, argb)` future helper) or use [setNamedVariable] on the result of
 * a previously-allocated `addText` / `addInt` / `addColor` / etc.
 */
fun RemoteComposeContext.addNamedVariable(name: String, varType: Int): Int {
    val id = ids.nextId()
    add(com.tneff.kmpremotecompose.remote.core.operations.NamedVariable(varId = id, varType = varType, name = name))
    return id
}

/**
 * `NAMED_VARIABLE` (bind-existing-id) — bind [name] of [varType] to an existing [id] without
 * allocating a new one. Mirrors upstream `RemoteComposeWriter.setNamedVariable(id, name, type)`.
 * This is the pattern used by `color_table.rc`: a `COLOR_CONSTANT id=50, …` op binds a colour to
 * id 50, then `NAMED_VARIABLE id=50, type=COLOR, name="color.system_accent1_0"` attaches the name
 * — no second allocation.
 */
fun RemoteComposeContext.setNamedVariable(id: Int, name: String, varType: Int) {
    add(com.tneff.kmpremotecompose.remote.core.operations.NamedVariable(varId = id, varType = varType, name = name))
}
