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
package com.tneff.kmpremotecompose.remote.player.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.StampedPathEffectStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.SweepGradientShader
import androidx.compose.ui.graphics.TileMode
import com.tneff.kmpremotecompose.remote.core.operations.ShaderData
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.createRuntimeShader
import com.tneff.kmpremotecompose.remote.wire.WireTypes

/**
 * REM-31 (L2-S2) — applies a Layer-1 `PAINT_VALUES` bundle (raw `IntArray` from
 * [com.tneff.kmpremotecompose.remote.core.operations.draw.PaintData]) onto a Compose [Paint].
 *
 * Ported from upstream `PaintBundle.applyPaintChange` + `ComposePaintChanges` (reference for behavior,
 * not paste — PROJECT_CONTEXT §5). The int-array framing is `[cmd, args…]*` where `type = cmd & 0xFFFF`
 * and packed sub-values live in `cmd >> 16`.
 *
 * **MVP scope (L2-S2, PO 2026-06-26):** color, stroke (width/miter/cap/join), style, alpha, anti-alias,
 * blend mode, filter-bitmap/quality, color-filter, and gradients (linear/radial/sweep → CMP gradient
 * shaders).
 *
 * **Out of S2 scope — skipped here, advanced exactly so the walk stays in sync, recorded in
 * [deferred] for honest reporting (no silent caps):**
 * - `TEXT_SIZE` / `TYPEFACE` / `FALLBACK_TYPEFACE` / `FONT_AXIS` → text/fonts = L2-S3 (dev-1) + GAP-6.
 * - `SHADER` (RuntimeShader) → GAP-5 / L2-D3 (AGSL→SkSL).
 * - `TEXTURE` (bitmap shader), `SHADER_MATRIX` → deferred (low prio).
 * - `PATH_EFFECT` → non-gap (Skiko) but `PaintPathEffects` parse is a separate follow-up within S2.
 *
 * The exact carve-up of who owns text-paint setters is reconciled with dev-1 at S1/S3 integration.
 */
internal object PaintBundleApplier {

    // PaintBundle attribute tags — verified against upstream PaintBundle.
    private const val TEXT_SIZE = 1
    private const val COLOR = 4
    private const val STROKE_WIDTH = 5
    private const val STROKE_MITER = 6
    private const val STROKE_CAP = 7
    private const val STYLE = 8
    private const val SHADER = 9
    private const val IMAGE_FILTER_QUALITY = 10
    private const val GRADIENT = 11
    private const val ALPHA = 12
    private const val COLOR_FILTER = 13
    private const val ANTI_ALIAS = 14
    private const val STROKE_JOIN = 15
    private const val TYPEFACE = 16
    private const val FILTER_BITMAP = 17
    private const val BLEND_MODE = 18
    private const val COLOR_ID = 19
    private const val COLOR_FILTER_ID = 20
    private const val CLEAR_COLOR_FILTER = 21
    private const val SHADER_MATRIX = 22
    private const val FONT_AXIS = 23
    private const val TEXTURE = 24
    private const val PATH_EFFECT = 25
    private const val FALLBACK_TYPEFACE = 26

    private const val STYLE_FILL_AND_STROKE = 2

    // REM-99: PaintPathEffects type tags (verified against upstream PaintPathEffects).
    private const val PE_DASH = 1
    private const val PE_DISCRETE = 2
    private const val PE_PATH_DASH = 3
    private const val PE_SUM = 4
    private const val PE_COMPOSE = 5

    private const val LINEAR_GRADIENT = 0
    private const val RADIAL_GRADIENT = 1
    private const val SWEEP_GRADIENT = 2

    /**
     * Apply the bundle [values] (first [count] ints) onto [state]: paint attributes mutate
     * `state.paint`, `TEXT_SIZE`/`TYPEFACE` land in `state.textSizePx`/`state.typefaceId` (read by the
     * text renderer, L2-S3). Remaining out-of-scope tags are skipped (kept in sync) and recorded in
     * [deferred] when provided.
     */
    fun applyTo(
        context: RemoteContext,
        state: PlayerPaintState,
        values: IntArray,
        count: Int = values.size,
        deferred: MutableSet<String>? = null,
    ) {
        val paint = state.paint
        var i = 0
        while (i < count) {
            val cmd = values[i++]
            when (cmd and 0xFFFF) {
                // Upstream PaintBundle: COLOR = literal ARGB; COLOR_ID = a color-id resolved via
                // context.getColor (fixColor). Treating COLOR_ID as literal made e.g. Color(61)=0x0000003D
                // (α≈0) → invisible clocks (REM-67). getColor is fail-soft (unset→0) → never-throw kept.
                COLOR -> paint.color = Color(values[i++])
                COLOR_ID -> paint.color = Color(context.getColor(values[i++]))
                STROKE_WIDTH -> paint.strokeWidth = Float.fromBits(values[i++])
                STROKE_MITER -> paint.strokeMiterLimit = Float.fromBits(values[i++])
                STROKE_CAP -> paint.strokeCap = strokeCap(cmd shr 16)
                STROKE_JOIN -> paint.strokeJoin = strokeJoin(cmd shr 16)
                STYLE -> {
                    val s = cmd shr 16
                    // REM-94: FILL_AND_STROKE has no single CMP PaintingStyle → flag it so the geometry
                    // adapter draws a fill pass + a stroke pass (two-pass), matching upstream
                    // Paint.Style.FILL_AND_STROKE. paint.style carries Fill (the fill-pass default); a
                    // plain FILL/STROKE clears the flag so a later shape isn't accidentally double-drawn.
                    state.fillAndStroke = (s == STYLE_FILL_AND_STROKE)
                    paint.style = paintingStyle(s)
                }
                ALPHA -> paint.alpha = Float.fromBits(values[i++])
                ANTI_ALIAS -> paint.isAntiAlias = (cmd shr 16) != 0
                BLEND_MODE -> paint.blendMode = blendMode(cmd shr 16)
                FILTER_BITMAP -> paint.filterQuality =
                    if ((cmd shr 16) != 0) FilterQuality.Low else FilterQuality.None
                IMAGE_FILTER_QUALITY -> paint.filterQuality =
                    if ((cmd shr 16) == 1) FilterQuality.Low else FilterQuality.None
                // Same literal-vs-color-id split for the tint colour (upstream fixColor on COLOR_FILTER_ID).
                COLOR_FILTER ->
                    paint.colorFilter = ColorFilter.tint(Color(values[i++]), blendMode(cmd shr 16))
                COLOR_FILTER_ID ->
                    paint.colorFilter = ColorFilter.tint(Color(context.getColor(values[i++])), blendMode(cmd shr 16))
                CLEAR_COLOR_FILTER -> paint.colorFilter = null
                GRADIENT -> i = applyGradient(context, paint, cmd, values, i, deferred)

                // ---- text attributes → shared state (read by the L2-S3 text renderer) ----
                TEXT_SIZE -> state.textSizePx = Float.fromBits(values[i++])
                // REM-158: decode TYPEFACE faithfully (upstream PaintBundle.applyPaintChange): the high half
                // packs weight/italic/ttf; the next int is `font_type`. When `font_type > 10 && !ttf` it is a
                // DATA_TEXT name id → resolve the string (the named-font case), else a generic enum
                // (0=default/1=sans/2=serif/3=mono). The family is mapped + applied by the text renderer's
                // NamedFontResolver. Weight/italic from this op stay family-only scope (REM-37 fontStyle/
                // fontWeight path owns those — avoid double-setting). 🚩 weight/italic-via-TYPEFACE = follow-up.
                TYPEFACE -> {
                    val ttf = ((cmd shr 16) and 1024) != 0
                    val fontType = values[i++]
                    state.typefaceId = fontType
                    state.typefaceName =
                        if (fontType > NamedFontResolver.NAME_ID_THRESHOLD && !ttf) context.getText(fontType) else null
                }

                // ---- out of S2/text scope: advance correctly, record, do not apply ----
                FALLBACK_TYPEFACE -> { i++; deferred?.add("FALLBACK_TYPEFACE") }
                // REM-77: the SHADER tag carries a shaderId → resolve the DATA_SHADER (source text +
                // uniforms) from the context store and build a platform runtime shader (AGSL on Android,
                // AGSL→SkSL on Skiko). Fail-soft: a missing/unsupported shader leaves the paint unshaded
                // (plain fill) and is recorded in [deferred] — never throws the render path.
                SHADER -> applyShader(context, paint, values[i++], deferred)
                SHADER_MATRIX -> { i++; deferred?.add("SHADER_MATRIX") }
                FONT_AXIS -> { i += 2 * (cmd shr 16); deferred?.add("FONT_AXIS") }
                // REM-98: the TEXTURE tag carries a bitmapId + tile/filter packing → resolve the decoded
                // bitmap and set a tiled ImageShader. Fail-soft: a missing bitmap leaves the paint unshaded
                // and is recorded in [deferred] — never throws the render path.
                TEXTURE -> i = applyTexture(context, paint, values, i, deferred)
                // REM-99: PATH_EFFECT carries (cmd>>16) float-encoded ints describing a (possibly nested)
                // path effect → parse into a CMP PathEffect (dash/stamped/chain). CMP-unreachable types
                // (discrete/sum) are recorded in [deferred], never silently dropped. The cursor always
                // advances by the declared count (paint-byte-sync) regardless of what parse consumed.
                PATH_EFFECT -> { applyPathEffect(context, paint, values, i, cmd shr 16, deferred); i += (cmd shr 16) }

                else -> {
                    // Unknown tag — we can't know its arg width, so stop to avoid desync.
                    deferred?.add("UNKNOWN_${cmd and 0xFFFF}")
                    return
                }
            }
        }
    }

    /**
     * Decode a GRADIENT command, set the corresponding shader on [paint]; returns the new cursor.
     *
     * **Cursor matches upstream `PaintBundle.callSetGradient` EXACTLY** (slot-count fidelity is the
     * paint analogue of L1 byte-sync — a wrong advance desyncs every paint op after): read the control
     * int (color count in its low byte), the colors, the stops-length int, the stops (only when colors
     * are present), then — only if colors are present — the per-type geometry. When `colorLen == 0`
     * upstream returns right after the stops-length int **without** consuming geometry; mirrored here.
     */
    private fun applyGradient(context: RemoteContext, paint: Paint, cmd: Int, a: IntArray, start: Int, deferred: MutableSet<String>?): Int {
        var ret = start
        // Bounds-safe read: a truncated/corrupt bundle must never throw the render path (REM-37 fail-soft,
        // belt-and-suspenders). An over-read yields 0 but still advances the cursor so slot-count stays in
        // sync with upstream (the paint analogue of L1 byte-sync).
        fun rd(): Int = if (ret < a.size) a[ret++] else { ret++; 0 }

        val type = cmd shr 16
        // The control int packs colorLen in its low byte and a per-color id-mask in the high 16 bits
        // (upstream PaintBundle.updateFloatsInGradient): bit j set → color j is a color-id resolved via
        // context.getColor, else a literal ARGB. Ignoring the mask rendered raw ids as ARGB garbage (REM-67).
        val control = rd()
        val colorLen = 0xFF and control
        val idMask = (control shr 16) and 0xFFFF
        val colors: ArrayList<Color>? =
            if (colorLen > 0) ArrayList<Color>(colorLen).apply {
                for (j in 0 until colorLen) {
                    val raw = rd()
                    add(if ((idMask and (1 shl j)) != 0) Color(context.getColor(raw)) else Color(raw))
                }
            } else null

        val stopsLen = rd()
        var stops: List<Float>? = null
        if (stopsLen > 0 && colors != null) {
            // upstream: stops length must equal colors length; read colorLen stops.
            val s = ArrayList<Float>(colorLen)
            for (j in 0 until colorLen) s.add(Float.fromBits(rd()))
            stops = s
        }

        // upstream `if (colors == null) return ret;` — geometry is NOT consumed for a 0-color gradient.
        if (colors == null) return ret

        // Read the per-type geometry (always advancing the cursor for slot fidelity) into locals, then
        // build the shader fail-soft: CMP's gradient shaders throw on <2 colors / mismatched stops /
        // non-positive radius. A bad color/gradient value must degrade to a sensible default (the first
        // color as a solid fill), never throw — same contract as getColor (unset→0, never throws).
        when (type) {
            LINEAR_GRADIENT -> {
                val sx = Float.fromBits(rd()); val sy = Float.fromBits(rd())
                val ex = Float.fromBits(rd()); val ey = Float.fromBits(rd())
                val tile = tileMode(rd())
                setGradientOrSolid(paint, colors, stops, deferred) {
                    LinearGradientShader(Offset(sx, sy), Offset(ex, ey), colors, stops, tile)
                }
            }
            RADIAL_GRADIENT -> {
                val cx = Float.fromBits(rd()); val cy = Float.fromBits(rd())
                val radius = Float.fromBits(rd())
                val tile = tileMode(rd())
                setGradientOrSolid(paint, colors, stops, deferred, radius) {
                    RadialGradientShader(Offset(cx, cy), radius, colors, stops, tile)
                }
            }
            SWEEP_GRADIENT -> {
                val cx = Float.fromBits(rd()); val cy = Float.fromBits(rd())
                setGradientOrSolid(paint, colors, stops, deferred) {
                    SweepGradientShader(Offset(cx, cy), colors, stops)
                }
            }
        }
        return ret
    }

    /**
     * Set [make]'s gradient shader on [paint] only when it is well-formed (≥2 colors, stops match colors,
     * radius > 0); otherwise degrade to a solid fill with the first color — never throw (REM-37 fail-soft).
     * The shader construction is additionally guarded so any CMP-side validation throw also degrades.
     */
    private inline fun setGradientOrSolid(
        paint: Paint,
        colors: List<Color>,
        stops: List<Float>?,
        deferred: MutableSet<String>?,
        radius: Float = 1f,
        make: () -> Shader,
    ) {
        val wellFormed = colors.size >= 2 && (stops == null || stops.size == colors.size) && radius > 0f
        if (wellFormed) {
            try {
                paint.shader = make()
                return
            } catch (t: Throwable) {
                deferred?.add("GRADIENT_INVALID")
            }
        } else {
            deferred?.add("GRADIENT_DEGENERATE")
        }
        // Fail-soft default: solid fill with the first color (a single-color "gradient" is just that color).
        colors.firstOrNull()?.let { paint.shader = null; paint.color = it }
    }

    /**
     * REM-77: resolve [shaderId] → its `DATA_SHADER` (source-text id + uniforms) → a platform runtime
     * shader, and set it on [paint]. Source text is resolved via [RemoteContext.getText]; the AGSL→SkSL
     * split lives in the platform [createRuntimeShader] actual. Fail-soft at every miss (no shader data,
     * no source text, build returns null) — the paint is left unshaded and the reason recorded.
     */
    private fun applyShader(context: RemoteContext, paint: Paint, shaderId: Int, deferred: MutableSet<String>?) {
        // Upstream `setShader(0)` clears the runtime shader on this paint (a SHADER bundle entry with id 0
        // is intentional reset, not a missing shader) — do not flag it.
        if (shaderId == 0) { paint.shader = null; return }
        val data = context.getShaderData(shaderId)
        if (data == null) { deferred?.add("SHADER_NO_DATA"); return }
        val source = context.getText(data.shaderTextId)
        if (source.isNullOrEmpty()) { deferred?.add("SHADER_NO_SOURCE"); return }
        if (data.bitmapUniforms.isNotEmpty()) deferred?.add("SHADER_BITMAP_UNIFORM") // scaffold: not yet bound
        // Resolve NaN-encoded var-ref float uniforms (e.g. `iTime`) via getFloat — mirrors
        // BackgroundModifier.resolveArgb's NaN handling (static time → 0). Non-NaN uniforms pass through.
        val floats = data.floatUniforms.map { u ->
            if (u.values.none { it.isNaN() }) u
            else ShaderData.FloatUniform(
                u.name,
                FloatArray(u.values.size) { k ->
                    val v = u.values[k]
                    if (v.isNaN()) context.getFloat(WireTypes.idFromNan(v)) else v
                },
            )
        }
        val shader = createRuntimeShader(source, floats, data.intUniforms)
        if (shader == null) { deferred?.add("SHADER_UNSUPPORTED"); return }
        paint.shader = shader
    }

    /**
     * REM-98: resolve the TEXTURE bundle entry → a tiled bitmap [ImageShader] and set it on [paint].
     *
     * **Cursor + packing match upstream `PaintBundle.setTextureShader` EXACTLY** (3 ints, the paint
     * analogue of L1 byte-sync): `[bitmapId][tileX | tileY<<16][filterMode | maxAnisotropy<<16]`. The
     * bitmap is the same decoded [androidx.compose.ui.graphics.ImageBitmap] the draw path uses
     * ([RemoteContext.getBitmap], populated by `DATA_BITMAP`/`ImageDecode`). Tile modes map through the
     * same `Shader.TileMode.values()` order as gradients ([tileMode]: 0=Clamp/1=Repeat/2=Mirror/3=Decal).
     *
     * `filterMode > 0` requests bilinear sampling — CMP's [ImageShader] has no per-shader filter setter,
     * so it is approximated via `paint.filterQuality` (the closest knob); `maxAnisotropy` has no CMP
     * equivalent and is intentionally a no-op (recorded so the gap is visible, not silent). Fail-soft: a
     * missing bitmap (or a shader-build throw) leaves the paint unshaded and is recorded in [deferred] —
     * never throws the render path. Returns the new cursor.
     */
    private fun applyTexture(context: RemoteContext, paint: Paint, a: IntArray, start: Int, deferred: MutableSet<String>?): Int {
        var ret = start
        fun rd(): Int = if (ret < a.size) a[ret++] else { ret++; 0 }
        val bitmapId = rd()
        val tileModes = rd()
        val filter = rd()
        val tileX = tileMode(tileModes and 0xF)
        val tileY = tileMode((tileModes shr 16) and 0xF)
        val image = context.getBitmap(bitmapId)
        if (image == null) { deferred?.add("TEXTURE_NO_BITMAP"); return ret }
        try {
            paint.shader = ImageShader(image, tileX, tileY)
            // filterMode>0 ⇒ smooth sampling; maxAnisotropy (filter>>16) has no CMP knob → not applied.
            if ((filter and 0xF) > 0) paint.filterQuality = FilterQuality.Low
            if ((filter shr 16) > 0) deferred?.add("TEXTURE_ANISOTROPY")
        } catch (t: Throwable) {
            deferred?.add("TEXTURE_INVALID")
        }
        return ret
    }

    /**
     * REM-99: parse the PATH_EFFECT bundle payload ([count] float-encoded ints starting at [start]) into a
     * CMP [PathEffect] and set it on [paint]. Mirrors upstream `PaintPathEffects.parse` +
     * `AndroidPaintContext.getPathEffect`: a (possibly nested) effect tree of DASH / DISCRETE / PATH_DASH /
     * SUM / COMPOSE. CMP covers DASH ([PathEffect.dashPathEffect]), PATH_DASH ([PathEffect.stampedPathEffect])
     * and COMPOSE ([PathEffect.chainPathEffect]); DISCRETE and SUM have no CMP equivalent and are recorded in
     * [deferred] (and make a containing SUM/COMPOSE collapse to whichever side resolved). `count == 0` clears
     * the effect (upstream `setPathEffect(null)`). Fail-soft: any malformed/unsupported sub-tree leaves the
     * effect null and is logged — never throws the render path. The caller advances the cursor by [count]
     * (paint-byte-sync), independent of what this consumes.
     */
    private fun applyPathEffect(context: RemoteContext, paint: Paint, a: IntArray, start: Int, count: Int, deferred: MutableSet<String>?) {
        if (count <= 0) { paint.pathEffect = null; return }
        try {
            val parsed = parsePathEffect(context, a, start, start + count, deferred)
            paint.pathEffect = parsed?.effect
            // A null effect from a recognized-but-CMP-unreachable/degenerate sub-tree already logged its
            // specific reason (DISCRETE/SUM/*_DEGENERATE). Only the unparseable case (parse returned null)
            // needs the generic marker — never silently drop.
            if (parsed == null) deferred?.add("PATH_EFFECT_EMPTY")
        } catch (t: Throwable) {
            deferred?.add("PATH_EFFECT_INVALID")
        }
    }

    /** A parsed path effect plus the number of ints it consumed (including its 1-int type slot). */
    private class ParsedPe(val effect: PathEffect?, val len: Int)

    /**
     * Recursively parse one path effect at [off] (bounded by [end]). Returns null if [off] is out of range.
     * Float fields resolve NaN var-refs via [RemoteContext.getFloat] (static-time); int fields (type/len/
     * shapeId/style) are read raw. `len` lets SUM/COMPOSE locate their second child exactly as upstream
     * (`offset + first.mDataLength + 1`).
     */
    private fun parsePathEffect(context: RemoteContext, a: IntArray, off: Int, end: Int, deferred: MutableSet<String>?): ParsedPe? {
        if (off >= end || off >= a.size) return null
        fun f(idx: Int): Float {
            val raw = if (idx < a.size) Float.fromBits(a[idx]) else 0f
            return if (raw.isNaN()) context.getFloat(WireTypes.idFromNan(raw)) else raw
        }
        return when (a[off]) {
            PE_DASH -> {
                val phase = f(off + 1)
                val len = if (off + 2 < a.size) a[off + 2] else 0
                val intervals = FloatArray(if (len < 0) 0 else len) { f(off + 3 + it) }
                // Skiko/Android require ≥2 even-count intervals with a positive sum; otherwise no dashing.
                val effect = if (intervals.size >= 2 && intervals.any { it > 0f })
                    PathEffect.dashPathEffect(intervals, phase) else null
                if (effect == null) deferred?.add("PATH_EFFECT_DASH_DEGENERATE")
                ParsedPe(effect, 1 + 2 + (if (len < 0) 0 else len))
            }
            PE_DISCRETE -> {
                // CMP has no discrete path effect → honest deferred (consumes 2 floats: segmentLength, deviation).
                deferred?.add("PATH_EFFECT_DISCRETE")
                ParsedPe(null, 1 + 2)
            }
            PE_PATH_DASH -> {
                val shapeId = if (off + 1 < a.size) a[off + 1] else 0
                val advance = f(off + 2)
                val phase = f(off + 3)
                val style = if (off + 4 < a.size) a[off + 4] else 0
                val shape = PathGeometry.buildPath(context, shapeId, 0f, 1f, deferred)
                val effect = if (!shape.isEmpty && advance > 0f)
                    PathEffect.stampedPathEffect(shape, advance, phase, stampStyle(style)) else null
                if (effect == null) deferred?.add("PATH_EFFECT_PATHDASH_DEGENERATE")
                ParsedPe(effect, 1 + 4)
            }
            PE_SUM -> {
                val first = parsePathEffect(context, a, off + 1, end, deferred)
                val second = first?.let { parsePathEffect(context, a, off + 1 + it.len, end, deferred) }
                // CMP has no SumPathEffect (parallel apply). Keep whichever side resolved; if both, log the gap.
                if (first?.effect != null && second?.effect != null) deferred?.add("PATH_EFFECT_SUM")
                val effect = first?.effect ?: second?.effect
                ParsedPe(effect, 1 + (first?.len ?: 0) + (second?.len ?: 0))
            }
            PE_COMPOSE -> {
                val outer = parsePathEffect(context, a, off + 1, end, deferred)
                val inner = outer?.let { parsePathEffect(context, a, off + 1 + it.len, end, deferred) }
                val effect = if (outer?.effect != null && inner?.effect != null)
                    PathEffect.chainPathEffect(outer.effect, inner.effect)
                else (outer?.effect ?: inner?.effect)
                ParsedPe(effect, 1 + (outer?.len ?: 0) + (inner?.len ?: 0))
            }
            else -> { deferred?.add("PATH_EFFECT_UNKNOWN"); null }
        }
    }

    /** Upstream `PathDashPathEffect.Style` order: 0=TRANSLATE, 1=ROTATE, 2=MORPH. */
    private fun stampStyle(style: Int): StampedPathEffectStyle =
        when (style) {
            1 -> StampedPathEffectStyle.Rotate
            2 -> StampedPathEffectStyle.Morph
            else -> StampedPathEffectStyle.Translate
        }

    /** Upstream `Paint.Style` order: 0=FILL, 1=STROKE, 2=FILL_AND_STROKE (no exact CMP equivalent). */
    private fun paintingStyle(style: Int): PaintingStyle =
        when (style) {
            1 -> PaintingStyle.Stroke
            // CMP PaintingStyle has only Fill/Stroke; FILL_AND_STROKE (2) approximated as Fill. 🚩parity
            else -> PaintingStyle.Fill
        }

    /** Upstream `Paint.Cap` order: 0=BUTT, 1=ROUND, 2=SQUARE. */
    private fun strokeCap(cap: Int): StrokeCap =
        when (cap) {
            1 -> StrokeCap.Round
            2 -> StrokeCap.Square
            else -> StrokeCap.Butt
        }

    /** Upstream `Paint.Join` order: 0=MITER, 1=ROUND, 2=BEVEL. */
    private fun strokeJoin(join: Int): StrokeJoin =
        when (join) {
            1 -> StrokeJoin.Round
            2 -> StrokeJoin.Bevel
            else -> StrokeJoin.Miter
        }

    /** Upstream gradient tile modes: 0=CLAMP, 1=REPEAT, 2=MIRROR (`ComposePaintChanges.tileModes`). */
    private fun tileMode(mode: Int): TileMode =
        when (mode) {
            1 -> TileMode.Repeated
            2 -> TileMode.Mirror
            3 -> TileMode.Decal
            else -> TileMode.Clamp
        }

    /** Map upstream `BLEND_MODE_*` (0..30) to CMP [BlendMode]. */
    private fun blendMode(mode: Int): BlendMode =
        when (mode) {
            0 -> BlendMode.Clear
            1 -> BlendMode.Src
            2 -> BlendMode.Dst
            3 -> BlendMode.SrcOver
            4 -> BlendMode.DstOver
            5 -> BlendMode.SrcIn
            6 -> BlendMode.DstIn
            7 -> BlendMode.SrcOut
            8 -> BlendMode.DstOut
            9 -> BlendMode.SrcAtop
            10 -> BlendMode.DstAtop
            11 -> BlendMode.Xor
            12 -> BlendMode.Plus
            13 -> BlendMode.Modulate
            14 -> BlendMode.Screen
            15 -> BlendMode.Overlay
            16 -> BlendMode.Darken
            17 -> BlendMode.Lighten
            18 -> BlendMode.ColorDodge
            19 -> BlendMode.ColorBurn
            20 -> BlendMode.Hardlight
            21 -> BlendMode.Softlight
            22 -> BlendMode.Difference
            23 -> BlendMode.Exclusion
            24 -> BlendMode.Multiply
            25 -> BlendMode.Hue
            26 -> BlendMode.Saturation
            27 -> BlendMode.Color
            28 -> BlendMode.Luminosity
            30 -> BlendMode.Plus // PORTER_MODE_ADD ≈ additive
            else -> BlendMode.SrcOver // 29 = NULL / unknown
        }
}
