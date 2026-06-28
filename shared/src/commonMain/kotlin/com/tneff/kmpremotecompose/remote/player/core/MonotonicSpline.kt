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

import kotlin.math.hypot

/**
 * REM-109: a 1-D monotonic cubic-Hermite spline — re-implemented from upstream
 * `AnimatedFloatExpression`'s `MonotonicSpline` (PROJECT_CONTEXT §5: behaviour reference, not paste).
 * Backs the `A_SPLINE` RPN operator: [getPos] interpolates the sample array [y] at a parameter `t`.
 *
 * The `A_SPLINE` op builds this with **even time points 0..1** (upstream `new MonotonicSpline(null, f)`),
 * so [getPos] expects `t` normalised to `[0, 1]` across the array, with linear extrapolation outside it.
 * Tangents use the Fritsch–Carlson monotonicity clamp (the `h > 9` rescale), matching upstream exactly.
 *
 * [getSlope] intentionally mirrors upstream's quirk (the loop breaks after the first segment) so the
 * extrapolation slope matches the reference byte-for-byte rather than being "fixed".
 */
internal class MonotonicSpline(time: FloatArray?, private val y: FloatArray) {

    private val t: FloatArray = time ?: FloatArray(y.size) { i -> i / (y.size - 1).toFloat() }
    private val tangent: FloatArray
    private val extrapolate = true

    /** The sample array this spline was fit to — used to invalidate a cached spline when the data changes. */
    val array: FloatArray get() = y

    init {
        val n = t.size
        val slope = FloatArray(if (n > 1) n - 1 else 0)
        val tan = FloatArray(n)
        for (i in 0 until n - 1) {
            val dt = t[i + 1] - t[i]
            slope[i] = (y[i + 1] - y[i]) / dt
            tan[i] = if (i == 0) slope[i] else (slope[i - 1] + slope[i]) * 0.5f
        }
        if (n >= 2) tan[n - 1] = slope[n - 2]

        for (i in 0 until n - 1) {
            if (slope[i] == 0f) {
                tan[i] = 0f
                tan[i + 1] = 0f
            } else {
                val a = tan[i] / slope[i]
                val b = tan[i + 1] / slope[i]
                val h = hypot(a, b)
                if (h > 9.0) {
                    val s = 3f / h
                    tan[i] = s * a * slope[i]
                    tan[i + 1] = s * b * slope[i]
                }
            }
        }
        tangent = tan
    }

    /** The interpolated value at parameter [pos] (normalised 0..1 for the `A_SPLINE` use). */
    fun getPos(pos: Float): Float {
        val n = t.size
        if (n == 0) return 0f
        if (n == 1) return y[0]
        if (extrapolate) {
            if (pos <= t[0]) return y[0] + (pos - t[0]) * getSlope(t[0])
            if (pos >= t[n - 1]) return y[n - 1] + (pos - t[n - 1]) * getSlope(t[n - 1])
        } else {
            if (pos <= t[0]) return y[0]
            if (pos >= t[n - 1]) return y[n - 1]
        }
        for (i in 0 until n - 1) {
            if (pos < t[i + 1]) {
                val h = t[i + 1] - t[i]
                val x = (pos - t[i]) / h
                return interpolate(h, x, y[i], y[i + 1], tangent[i], tangent[i + 1])
            }
        }
        return 0f
    }

    /** Slope at [pos]; replicates upstream's first-segment-only behaviour (the unconditional break). */
    fun getSlope(pos: Float): Float {
        val n = t.size
        var p = pos
        if (p <= t[0]) p = t[0] else if (p >= t[n - 1]) p = t[n - 1]
        var v = 0f
        for (i in 0 until n - 1) {
            if (p <= t[i + 1]) {
                val h = t[i + 1] - t[i]
                val x = (p - t[i]) / h
                v = diff(h, x, y[i], y[i + 1], tangent[i], tangent[i + 1]) / h
            }
            break // upstream: unconditional — only the first segment is ever considered
        }
        return v
    }

    private companion object {
        /** Cubic Hermite spline. */
        fun interpolate(h: Float, x: Float, y1: Float, y2: Float, t1: Float, t2: Float): Float {
            val x2 = x * x
            val x3 = x2 * x
            return -2 * x3 * y2 + 3 * x2 * y2 + 2 * x3 * y1 - 3 * x2 * y1 + y1 +
                h * t2 * x3 + h * t1 * x3 - h * t2 * x2 - 2 * h * t1 * x2 + h * t1 * x
        }

        /** Cubic Hermite spline slope (differentiated). */
        fun diff(h: Float, x: Float, y1: Float, y2: Float, t1: Float, t2: Float): Float {
            val x2 = x * x
            return -6 * x2 * y2 + 6 * x * y2 + 6 * x2 * y1 - 6 * x * y1 +
                3 * h * t2 * x2 + 3 * h * t1 * x2 - 2 * h * t2 * x - 4 * h * t1 * x + h * t1
        }
    }
}
