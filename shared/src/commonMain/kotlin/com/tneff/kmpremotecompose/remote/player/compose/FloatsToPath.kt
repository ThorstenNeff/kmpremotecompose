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

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * REM-31 (L2-S2) — converts the NaN-encoded float-array path representation (Layer-1 `DATA_PATH`) into
 * a Compose [Path]. Re-implemented from upstream `FloatsToPath`/`PathData`; the reference is for
 * behavior, not paste (PROJECT_CONTEXT §5).
 *
 * The float stream is a sequence of commands: a NaN-encoded command id ([MOVE]/[LINE]/…) followed by
 * its coordinate floats (upstream pads each command's leading slot, so coordinate reads are offset by
 * the command marker — see the per-command index math).
 *
 * **CONIC (PO decision 2026-06-26, option b):** the common `androidx.compose.ui.graphics.Path` API has
 * no `conicTo`; a conic is a rational quadratic that quad/cubic cannot express exactly. We subdivide
 * the conic into quadratics **in commonMain** ([addConic]) — the same `ConvertConicToQuads` that Skia
 * does internally — so Android and iOS render from one identical impl (no native sub-pixel divergence
 * at the Maestro parity gate). The [addConic] seam is kept isolated so conic quality can be sharpened
 * later without touching the adapter.
 */
internal object FloatsToPath {

    // Path command markers (NaN-encoded in the stream). Values verified against upstream PathData.
    const val MOVE: Int = 10
    const val LINE: Int = 11
    const val QUADRATIC: Int = 12
    const val CONIC: Int = 13
    const val CUBIC: Int = 14
    const val CLOSE: Int = 15
    const val DONE: Int = 16

    /** Skia's default conic→quad flatness tolerance and recursion cap (`SkConic`). */
    private const val CONIC_QUAD_TOLERANCE: Float = 0.25f
    private const val MAX_CONIC_TO_QUAD_POW2: Int = 5

    /**
     * Populate [retPath] from [floatPath]. When [start] > 0 or [stop] < 1, only the corresponding
     * fractional segment of the assembled path is emitted (via [PathMeasure], mirroring upstream).
     */
    fun genPath(retPath: Path, floatPath: FloatArray, start: Float, stop: Float) {
        val path = Path()
        // current pen position — needed as the conic's start control point.
        var curX = 0f
        var curY = 0f
        var i = 0
        while (i < floatPath.size) {
            when (WireTypes.idFromNan(floatPath[i])) {
                MOVE -> {
                    i++
                    curX = floatPath[i + 0]
                    curY = floatPath[i + 1]
                    path.moveTo(curX, curY)
                    i += 2
                }

                LINE -> {
                    i += 3
                    curX = floatPath[i + 0]
                    curY = floatPath[i + 1]
                    path.lineTo(curX, curY)
                    i += 2
                }

                QUADRATIC -> {
                    i += 3
                    path.quadraticTo(
                        floatPath[i + 0],
                        floatPath[i + 1],
                        floatPath[i + 2],
                        floatPath[i + 3],
                    )
                    curX = floatPath[i + 2]
                    curY = floatPath[i + 3]
                    i += 4
                }

                CONIC -> {
                    i += 3
                    // control (x1,y1), end (x2,y2), weight
                    addConic(
                        path,
                        curX,
                        curY,
                        floatPath[i + 0],
                        floatPath[i + 1],
                        floatPath[i + 2],
                        floatPath[i + 3],
                        floatPath[i + 4],
                    )
                    curX = floatPath[i + 2]
                    curY = floatPath[i + 3]
                    i += 5
                }

                CUBIC -> {
                    i += 3
                    path.cubicTo(
                        floatPath[i + 0],
                        floatPath[i + 1],
                        floatPath[i + 2],
                        floatPath[i + 3],
                        floatPath[i + 4],
                        floatPath[i + 5],
                    )
                    curX = floatPath[i + 4]
                    curY = floatPath[i + 5]
                    i += 6
                }

                CLOSE -> {
                    path.close()
                    i++
                }

                DONE -> i++
                // Unknown marker — stop to avoid walking off into garbage (fail-soft, render side).
                else -> break
            }
        }

        retPath.reset()
        if (start > 0f || stop < 1f) {
            if (start < stop) {
                val measure = PathMeasure()
                measure.setPath(path, false)
                val len = measure.length
                val scaleStart = (max(start, 0f) * len)
                val scaleStop = (min(stop, 1f) * len)
                measure.getSegment(scaleStart, scaleStop, retPath, true)
            }
        } else {
            retPath.addPath(path)
        }
    }

    /**
     * Append a conic (rational quadratic) from the current point (`x0,y0`) via control (`x1,y1`) to
     * (`x2,y2`) with [weight], approximated by quadratics — the **isolated CONIC seam** (PO option b).
     * Mirrors `SkConic::computeQuadPOW2` + `chopIntoQuadsPOW2`: pick a subdivision depth from the
     * flatness tolerance, recursively chop the conic, emit each leaf as one [Path.quadraticTo].
     */
    private fun addConic(
        path: Path,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        weight: Float,
    ) {
        if (weight <= 0f || weight.isNaN()) {
            // Degenerate weight — fall back to a plain quadratic through the control point.
            path.quadraticTo(x1, y1, x2, y2)
            return
        }
        val pow2 = computeQuadPow2(x0, y0, x1, y1, x2, y2, weight)
        subdivide(path, x0, y0, x1, y1, x2, y2, weight, pow2)
    }

    /** `SkConic::computeQuadPOW2` — subdivision depth (0..[MAX_CONIC_TO_QUAD_POW2]) for the tolerance. */
    private fun computeQuadPow2(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        w: Float,
    ): Int {
        val a = w - 1f
        val k = a / (4f * (2f + a))
        val x = k * (x0 - 2f * x1 + x2)
        val y = k * (y0 - 2f * y1 + y2)
        var error = sqrt(x * x + y * y)
        var pow2 = 0
        while (pow2 < MAX_CONIC_TO_QUAD_POW2) {
            if (error <= CONIC_QUAD_TOLERANCE) break
            error *= 0.25f
            pow2++
        }
        return pow2
    }

    /**
     * Recursively chop the conic [level] times (`SkConic::chop`) and emit `2^level` quadratics. At
     * `level == 0` the conic's own control + end points form the quad (its weight is ~1 by then).
     */
    private fun subdivide(
        path: Path,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        w: Float,
        level: Int,
    ) {
        if (level == 0) {
            path.quadraticTo(x1, y1, x2, y2)
            return
        }
        // SkConic::chop — split at the conic midpoint into two conics of weight newW.
        val scale = 1f / (1f + w)
        val newW = sqrt((1f + w) / 2f)
        val wp1x = w * x1
        val wp1y = w * y1
        // conic midpoint m = (p0 + 2*w*p1 + p2) / (2*(1+w))
        val mScale = 1f / (2f * (1f + w))
        val mx = (x0 + 2f * wp1x + x2) * mScale
        val my = (y0 + 2f * wp1y + y2) * mScale
        // left  = (p0, (p0 + w*p1)*scale, m, newW)
        subdivide(path, x0, y0, (x0 + wp1x) * scale, (y0 + wp1y) * scale, mx, my, newW, level - 1)
        // right = (m, (w*p1 + p2)*scale, p2, newW)
        subdivide(path, mx, my, (wp1x + x2) * scale, (wp1y + y2) * scale, x2, y2, newW, level - 1)
    }
}
