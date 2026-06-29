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
package com.tneff.kmpremotecompose.remote.player.particles

import com.tneff.kmpremotecompose.remote.player.NoOpPaintContext
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.math.cos
import kotlin.math.sin

/**
 * REM-143 §5b #5 — the black-box, **matrix-aware** draw-capture PaintContext. The corpus particle bodies
 * place each particle via a per-particle matrix (`MATRIX_TRANSLATE`/`SCALE`/`ROTATE`) and then draw a
 * *local* shape (DRAW_PATH/DRAW_LINE/…), so the particle's screen position is NOT the draw's local coords —
 * it is where the current matrix maps the local origin (0,0). This tracks the affine transform stack
 * (save/restore/translate/scale/rotate/skew) and records that transformed origin at every body draw =
 * the effective particle anchor. It tests what the sim ACTUALLY draws (output), NOT a white-box
 * `op.mParticles` readback (which would share the sim path → false-green).
 *
 * A fresh instance is used per frame, so [draws] holds exactly that frame's body anchors. Non-particle
 * (background) draws are captured too; the gate matches each reconstruction position to SOME captured
 * anchor, so static background anchors are harmless.
 */
class RecordingParticlePaintContext(context: RemoteContext) : NoOpPaintContext(context) {

    /** A captured draw anchor: where the current matrix mapped the local origin (the particle position). */
    data class Draw(val cx: Float, val cy: Float)

    val draws: MutableList<Draw> = mutableListOf()

    // --- current affine [a c e; b d f] mapping (x,y) -> (a·x+c·y+e, b·x+d·y+f); origin (0,0) -> (e,f). ---
    private var a = 1f; private var b = 0f; private var c = 0f; private var d = 1f; private var e = 0f; private var f = 0f
    private val stack = ArrayDeque<FloatArray>()

    /**
     * Record a draw's anchor = the current matrix applied to the draw's local reference point [(x,y)].
     * MUST be position-dependent on the draw coords: a matrix-translate body draws a LOCAL shape (point
     * ~origin) so its anchor is the translate (the particle pos); a direct-coordinate body (maze
     * `drawCircle(particleX, particleY)`, identity matrix) carries the particle pos in (x,y) itself.
     * Recording only the origin (e,f) would be position-INDEPENDENT for the latter → vacuous match.
     */
    private fun record(x: Float, y: Float) { draws += Draw(a * x + c * y + e, b * x + d * y + f) }

    private fun mulTranslate(tx: Float, ty: Float) { e += a * tx + c * ty; f += b * tx + d * ty }
    private fun mulScale(sx: Float, sy: Float) { a *= sx; b *= sx; c *= sy; d *= sy }
    private fun mulRotate(deg: Float) {
        val r = deg * 0.017453292f; val cs = cos(r); val sn = sin(r)
        val na = a * cs + c * sn; val nb = b * cs + d * sn; val nc = -a * sn + c * cs; val nd = -b * sn + d * cs
        a = na; b = nb; c = nc; d = nd
    }

    override fun matrixSave() { stack.addLast(floatArrayOf(a, b, c, d, e, f)) }
    override fun matrixRestore() { stack.removeLastOrNull()?.let { a = it[0]; b = it[1]; c = it[2]; d = it[3]; e = it[4]; f = it[5] } }
    override fun matrixTranslate(translateX: Float, translateY: Float) { mulTranslate(translateX, translateY) }
    override fun matrixScale(scaleX: Float, scaleY: Float, centerX: Float, centerY: Float) {
        mulTranslate(centerX, centerY); mulScale(scaleX, scaleY); mulTranslate(-centerX, -centerY)
    }
    override fun matrixRotate(rotate: Float, pivotX: Float, pivotY: Float) {
        mulTranslate(pivotX, pivotY); mulRotate(rotate); mulTranslate(-pivotX, -pivotY)
    }
    override fun matrixSkew(skewX: Float, skewY: Float) { /* rare in particle bodies; origin-preserving for capture */ }
    override fun scale(scaleX: Float, scaleY: Float) { mulScale(scaleX, scaleY) }
    override fun translate(translateX: Float, translateY: Float) { mulTranslate(translateX, translateY) }

    // --- body draws: record the transformed draw anchor (circle centre, line start, path/shape origin). ---
    override fun drawCircle(centerX: Float, centerY: Float, radius: Float) { record(centerX, centerY) }
    override fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float) { record(x1, y1) }
    override fun drawPath(id: Int, start: Float, end: Float) { record(0f, 0f) }
    override fun drawTweenPath(path1Id: Int, path2Id: Int, tween: Float, start: Float, end: Float) { record(0f, 0f) }
    override fun drawRoundRect(left: Float, top: Float, right: Float, bottom: Float, radiusX: Float, radiusY: Float) { record((left + right) / 2f, (top + bottom) / 2f) }
    override fun drawOval(left: Float, top: Float, right: Float, bottom: Float) { record((left + right) / 2f, (top + bottom) / 2f) }
    override fun drawBitmap(id: Int, left: Float, top: Float, right: Float, bottom: Float) { record((left + right) / 2f, (top + bottom) / 2f) }
    override fun drawBitmap(
        imageId: Int,
        srcLeft: Int, srcTop: Int, srcRight: Int, srcBottom: Int,
        dstLeft: Int, dstTop: Int, dstRight: Int, dstBottom: Int,
        cdId: Int,
    ) { record((dstLeft + dstRight) / 2f, (dstTop + dstBottom) / 2f) }
}
