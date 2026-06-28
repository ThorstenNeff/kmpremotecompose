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

import com.tneff.kmpremotecompose.remote.core.operations.draw.PaintData

/**
 * Creation-DSL surface for assembling a `PAINT_VALUES` bundle.
 *
 * **Op-emitter, not a new encoder (§2).** Every setter appends the upstream-verified tag layout to an
 * `IntArray`, which is then handed to [PaintData] — byte-proven by the L1 conformance harness (REM-37).
 * Tag values come from [PaintData]'s public constants so the encoding stays in lockstep with the
 * reader/writer pair; the slot order recorded here is exactly the call order, which is what the
 * upstream `PaintBundle` cursor expects.
 *
 * **Argument encoding:** scalar floats are stored as raw IEEE-754 bits (`Float.toRawBits()`) to keep
 * NaN-encoded variable refs (REM-36) byte-exact through the bundle. Packed enums (style/cap/join/
 * blend/anti-alias) ride in the tag int's high 16 bits — zero trailing args.
 *
 * **Coverage (E2 scope):** the scalar paint attributes called out by the spec (§E2) — color/colorId/
 * strokeWidth/strokeMiter/style/strokeCap/strokeJoin/alpha/blendMode/antiAlias/textSize. Gradients,
 * shaders, typefaces, colour filters, path effects, and font axes stay deferred (they need their own
 * cursor walks and id-resolution semantics); construct `PaintData` from a raw [IntArray] for those
 * until later epics add their helpers.
 */
@RemoteComposeCreationDsl
class RcPaint {

    private val ints = ArrayList<Int>()

    /** ARGB colour: `[COLOR, argb]`. */
    fun color(argb: Int): RcPaint = apply {
        ints += PaintData.COLOR
        ints += argb
    }

    /** Reference a [com.tneff.kmpremotecompose.remote.core.operations.ColorConstant]-style colour id: `[COLOR_ID, id]`. */
    fun colorId(id: Int): RcPaint = apply {
        ints += PaintData.COLOR_ID
        ints += id
    }

    /** Stroke width (px). Stored as raw float bits so NaN-encoded variable refs survive: `[STROKE_WIDTH, bits]`. */
    fun strokeWidth(width: Float): RcPaint = apply {
        ints += PaintData.STROKE_WIDTH
        ints += width.toRawBits()
    }

    /** Stroke miter limit. `[STROKE_MITER, bits]`. */
    fun strokeMiter(miter: Float): RcPaint = apply {
        ints += PaintData.STROKE_MITER
        ints += miter.toRawBits()
    }

    /** Paint style (one of [STYLE_FILL]/[STYLE_STROKE]/[STYLE_FILL_AND_STROKE]). Packed: `[STYLE | (style shl 16)]`. */
    fun style(style: Int): RcPaint = apply {
        ints += PaintData.STYLE or (style shl 16)
    }

    /** Stroke cap (one of [CAP_BUTT]/[CAP_ROUND]/[CAP_SQUARE]). Packed: `[STROKE_CAP | (cap shl 16)]`. */
    fun strokeCap(cap: Int): RcPaint = apply {
        ints += PaintData.STROKE_CAP or (cap shl 16)
    }

    /** Stroke join (one of [JOIN_MITER]/[JOIN_ROUND]/[JOIN_BEVEL]). Packed: `[STROKE_JOIN | (join shl 16)]`. */
    fun strokeJoin(join: Int): RcPaint = apply {
        ints += PaintData.STROKE_JOIN or (join shl 16)
    }

    /** Alpha in `[0..1]`. Stored as raw float bits: `[ALPHA, bits]`. */
    fun alpha(alpha: Float): RcPaint = apply {
        ints += PaintData.ALPHA
        ints += alpha.toRawBits()
    }

    /** Blend mode (Android [PorterDuff.Mode] ordinal). Packed: `[BLEND_MODE | (mode shl 16)]`. */
    fun blendMode(mode: Int): RcPaint = apply {
        ints += PaintData.BLEND_MODE or (mode shl 16)
    }

    /** Anti-alias on/off. Packed: `[ANTI_ALIAS | ((if on 1 else 0) shl 16)]`. */
    fun antiAlias(on: Boolean): RcPaint = apply {
        ints += PaintData.ANTI_ALIAS or ((if (on) 1 else 0) shl 16)
    }

    /** Text size (px). `[TEXT_SIZE, bits]`. */
    fun textSize(px: Float): RcPaint = apply {
        ints += PaintData.TEXT_SIZE
        ints += px.toRawBits()
    }

    /**
     * Linear gradient (REM-92, closes the `procedure_gradient1` paint slot).
     *
     * **Slot layout** (matches `PaintData.resolveBundle` GRADIENT walk + upstream `PaintBundle`):
     * `[GRADIENT_TAG | (LINEAR shl 16)]`, `[control = colorCount]`, `colorCount` colour ints,
     * `[stopsLen]`, then if `stopsLen > 0` `colorCount` raw-bit stop floats, then geometry
     * `[x0, y0, x1, y1, tile]` (raw float bits + int [tile]). Geometry is emitted only when
     * `colors.isNotEmpty()` — upstream's contract.
     *
     * [colors] are ARGB ints (or colorIds — mode-routing is the player's job). [stops] is optional;
     * if `null`, emits no stop floats and writes `stopsLen=0`. [tile] is the [TILE_CLAMP] /
     * [TILE_REPEAT] / [TILE_MIRROR] enum (upstream `Shader.TileMode` ordinals).
     */
    fun linearGradient(
        x0: Float, y0: Float, x1: Float, y1: Float,
        colors: IntArray,
        stops: FloatArray? = null,
        tile: Int = TILE_CLAMP,
    ): RcPaint = apply {
        emitGradient(LINEAR_GRADIENT, colors, stops) {
            ints += x0.toRawBits(); ints += y0.toRawBits()
            ints += x1.toRawBits(); ints += y1.toRawBits()
            ints += tile
        }
    }

    /**
     * Radial gradient — geometry is `[centerX, centerY, radius, tile]` (3 raw-float-bit slots +
     * int tile). See [linearGradient] for the slot-layout / [stops] / [tile] contract.
     */
    fun radialGradient(
        centerX: Float, centerY: Float, radius: Float,
        colors: IntArray,
        stops: FloatArray? = null,
        tile: Int = TILE_CLAMP,
    ): RcPaint = apply {
        emitGradient(RADIAL_GRADIENT, colors, stops) {
            ints += centerX.toRawBits(); ints += centerY.toRawBits()
            ints += radius.toRawBits()
            ints += tile
        }
    }

    /**
     * Sweep gradient — geometry is `[centerX, centerY]` (2 raw-float-bit slots; sweep has no tile
     * mode in upstream's slot walk).
     */
    fun sweepGradient(
        centerX: Float, centerY: Float,
        colors: IntArray,
        stops: FloatArray? = null,
    ): RcPaint = apply {
        emitGradient(SWEEP_GRADIENT, colors, stops) {
            ints += centerX.toRawBits(); ints += centerY.toRawBits()
        }
    }

    private inline fun emitGradient(
        type: Int,
        colors: IntArray,
        stops: FloatArray?,
        geometry: () -> Unit,
    ) {
        ints += PaintData.GRADIENT or (type shl 16)
        ints += colors.size // control: low byte = color count
        for (c in colors) ints += c
        val stopsLen = stops?.size ?: 0
        ints += stopsLen
        if (stopsLen > 0 && colors.isNotEmpty()) {
            require(stopsLen == colors.size) {
                "stops.size (=$stopsLen) must equal colors.size (=${colors.size})"
            }
            for (s in stops!!) ints += s.toRawBits()
        }
        if (colors.isNotEmpty()) geometry()
    }

    /** Materialise the accumulated tags into a [PaintData] op (PAINT_VALUES). */
    fun build(): PaintData = PaintData(ints.toIntArray())

    companion object {
        // Paint.Style ordinals (Android Paint.Style.FILL/STROKE/FILL_AND_STROKE).
        const val STYLE_FILL: Int = 0
        const val STYLE_STROKE: Int = 1
        const val STYLE_FILL_AND_STROKE: Int = 2

        // Paint.Cap ordinals.
        const val CAP_BUTT: Int = 0
        const val CAP_ROUND: Int = 1
        const val CAP_SQUARE: Int = 2

        // Paint.Join ordinals.
        const val JOIN_MITER: Int = 0
        const val JOIN_ROUND: Int = 1
        const val JOIN_BEVEL: Int = 2

        // Gradient type tags (packed into the GRADIENT command's high 16 bits).
        // Match the private constants in PaintData.resolveBundle's gradient walk.
        private const val LINEAR_GRADIENT: Int = 0
        private const val RADIAL_GRADIENT: Int = 1
        private const val SWEEP_GRADIENT: Int = 2

        // Shader.TileMode ordinals (Android).
        const val TILE_CLAMP: Int = 0
        const val TILE_REPEAT: Int = 1
        const val TILE_MIRROR: Int = 2
    }
}

/**
 * Emit a `PAINT_VALUES` op against the current document, configured by [block]. Mirrors upstream's
 * `RcPaint`-then-`commit()` two-step in a single DSL call.
 *
 * Paint state in `.rc` is per-op (each `PAINT_VALUES` overwrites the player's paint bundle for the
 * following draws), so this is intentionally not scoped — call it before each group of draws that
 * needs a distinct paint. Successive `paint { … }` blocks emit successive `PAINT_VALUES` ops.
 */
fun RemoteComposeContext.paint(block: RcPaint.() -> Unit) {
    add(RcPaint().apply(block).build())
}
