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
package com.tneff.kmpremotecompose.remote.player.core

import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * REM-127 — samples a `PathExpression`'s `X(t)`/`Y(t)` (or polar `r(t)`) over `[min,max]` into a
 * cubic-marker path-data array (upstream `PathGenerator`, behavior not paste — PROJECT_CONTEXT §5).
 *
 * **Output layout is the §8.3 hard contract:** it is exactly what `FloatsToPath.genPath` /
 * [PathDataResolver] consume — `MOVE` = `[marker, x, y]` (3 slots), `CUBIC` =
 * `[marker, startX, startY, c1x, c1y, c2x, c2y, endX, endY]` (9 slots; consumer skips the start pair),
 * `CLOSE` = `[marker]`. Markers come from [PathDataResolver] (single source, REM-121). The caller
 * supplies an [eval] `(expr, t) -> Float` so this stays free of `RemoteContext`.
 *
 * Modes: SPLINE (C1 weighted-tangent) + LINEAR are corpus-present. **MONOTONIC is corpus-absent →
 * a loud throw** (no speculative code — D1/D5 discipline). Array-deref path expressions are absent too,
 * so the evaluator's `ca == null` path is sufficient.
 */
internal object PathGenerator {

    const val SPLINE = 0
    const val MONOTONIC = 2
    const val LINEAR = 4

    /** Output length for [count] sampled points (upstream `getReturnLength`): moveTo + per-seg cubicTo. */
    fun getReturnLength(count: Int, loop: Boolean): Int = 3 + if (loop) count * 9 + 1 else (count - 1) * 9

    /** Cartesian: sample `x=X(t)`, `y=Y(t)` for `t` in `[min,max]` then build the path. */
    fun getPath(
        expressionX: FloatArray,
        expressionY: FloatArray,
        min: Float,
        max: Float,
        count: Int,
        mode: Int,
        loop: Boolean,
        eval: (FloatArray, Float) -> Float,
    ): FloatArray {
        val x = FloatArray(count)
        val y = FloatArray(count)
        val step = if (loop) (max - min) / count else (max - min) / (count - 1)
        for (i in 0 until count) {
            val t = min + i * step
            x[i] = eval(expressionX, t)
            y[i] = eval(expressionY, t)
        }
        return asPath(x, y, mode, loop)
    }

    /**
     * Polar (upstream `getPolarPath`): `r = R(t)`, angle `= t`, centred at `coord[0],coord[1]` →
     * `x = cx + r·cos(t)`, `y = cy + r·sin(t)`. The corpus encodes the centre as the 2-element Y array
     * (every polar `PathExpression` has `lenY = 2`), so `coord` is the resolved Y array, used literally.
     */
    fun getPolarPath(
        expressionRad: FloatArray,
        coord: FloatArray,
        start: Float,
        end: Float,
        count: Int,
        mode: Int,
        loop: Boolean,
        eval: (FloatArray, Float) -> Float,
    ): FloatArray {
        val x = FloatArray(count)
        val y = FloatArray(count)
        val cx = coord.getOrElse(0) { 0f }
        val cy = coord.getOrElse(1) { 0f }
        val step = if (loop) (end - start) / count else (end - start) / (count - 1)
        for (i in 0 until count) {
            val t = start + i * step
            val r = eval(expressionRad, t)
            x[i] = cx + r * cos(t)
            y[i] = cy + r * sin(t)
        }
        return asPath(x, y, mode, loop)
    }

    private fun asPath(x: FloatArray, y: FloatArray, mode: Int, loop: Boolean): FloatArray = when (mode) {
        LINEAR -> buildPath(x, y, loop, spline = false)
        MONOTONIC -> throw IllegalArgumentException(
            "PathExpression MONOTONIC mode is corpus-absent and not implemented (REM-127 loud-guard)",
        )
        else -> buildPath(x, y, loop, spline = true)
    }

    /** Emit moveTo + per-segment cubicTo (spline tangents, or straight-line control points for LINEAR). */
    private fun buildPath(x: FloatArray, y: FloatArray, loop: Boolean, spline: Boolean): FloatArray {
        val n = x.size
        val b = Builder(getReturnLength(n, loop))
        if (n == 0) return b.finish()
        b.moveTo(x[0], y[0])
        if (n == 1) return b.finish()
        val segs = if (loop) n else n - 1

        if (!spline) {
            for (i0 in 0 until segs) {
                val i1 = (i0 + 1) % n
                b.cubicTo(x[i0], y[i0], x[i1], y[i1], x[i1], y[i1]) // straight line as a cubic
            }
        } else {
            val h = FloatArray(segs)
            val dxSeg = FloatArray(segs)
            val dySeg = FloatArray(segs)
            for (i0 in 0 until segs) {
                val i1 = (i0 + 1) % n
                val sx = x[i1] - x[i0]
                val sy = y[i1] - y[i0]
                var dist = hypot(sx.toDouble(), sy.toDouble()).toFloat()
                if (dist == 0f) dist = 1e-12f
                h[i0] = dist; dxSeg[i0] = sx / dist; dySeg[i0] = sy / dist
            }
            val tans = if (loop) segs else segs + 1
            val dxTan = FloatArray(tans)
            val dyTan = FloatArray(tans)
            smoothTangents(dxTan, dxSeg, h, loop)
            smoothTangents(dyTan, dySeg, h, loop)
            for (i0 in 0 until segs) {
                val i1 = (i0 + 1) % n
                val hi = h[i0]
                val c1x = x[i0] + dxTan[i0] * hi / 3f
                val c1y = y[i0] + dyTan[i0] * hi / 3f
                val c2x = x[i1] - dxTan[i1] * hi / 3f
                val c2y = y[i1] - dyTan[i1] * hi / 3f
                b.cubicTo(c1x, c1y, c2x, c2y, x[i1], y[i1])
            }
        }
        if (loop) b.close()
        return b.finish()
    }

    /** Simple C1 spline tangents: segment-length-weighted average of adjacent slopes (upstream). */
    private fun smoothTangents(d: FloatArray, delta: FloatArray, h: FloatArray, loop: Boolean) {
        val segs = delta.size
        val n = if (loop) segs else segs + 1
        if (loop) {
            for (i in 0 until n) {
                val im1 = (i - 1 + segs) % segs
                val ip0 = i % segs
                d[i] = (h[im1] * delta[ip0] + h[ip0] * delta[im1]) / (h[im1] + h[ip0])
            }
        } else {
            d[0] = delta[0]
            d[n - 1] = delta[segs - 1]
            for (i in 1 until n - 1) {
                val hm1 = h[i - 1]
                val hi = h[i]
                d[i] = (hm1 * delta[i] + hi * delta[i - 1]) / (hm1 + hi)
            }
        }
    }

    /** Fills a fixed-size buffer with marker+coord slots in the consumer's exact layout (REM-121). */
    private class Builder(size: Int) {
        private val out = FloatArray(size)
        private var n = 0
        private var cx = 0f
        private var cy = 0f
        private val moveNan = WireTypes.asNan(PathDataResolver.MOVE)
        private val cubicNan = WireTypes.asNan(PathDataResolver.CUBIC)
        private val closeNan = WireTypes.asNan(PathDataResolver.CLOSE)

        fun moveTo(x: Float, y: Float) { out[n++] = moveNan; out[n++] = x; out[n++] = y; cx = x; cy = y }

        fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, ex: Float, ey: Float) {
            out[n++] = cubicNan
            out[n++] = cx; out[n++] = cy // start pair (consumer skips it; present for slot-exactness)
            out[n++] = c1x; out[n++] = c1y
            out[n++] = c2x; out[n++] = c2y
            out[n++] = ex; out[n++] = ey
            cx = ex; cy = ey
        }

        fun close() { out[n++] = closeNan }

        fun finish(): FloatArray = if (n == out.size) out else out.copyOf(n)
    }
}
