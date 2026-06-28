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
package com.tneff.kmpremotecompose.remote.core.operations.layout

import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.OperationReader
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.RpnFloatEvaluator
import com.tneff.kmpremotecompose.remote.player.core.VariableSupport
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.math.abs

/**
 * `TOUCH_EXPRESSION` (opcode [Operations.TOUCH_EXPRESSION]) — a touch-driven value expression with
 * stop and easing specs.
 *
 * Wire layout (mirrors upstream `TouchExpression.apply`/`read`): opcode byte + int `id` + float `value`
 * + float `min` + float `max` + float `velocityId` + int `touchEffects` + int `expLen` + `expLen`×float
 * + int `stopLogic` (packs `touchMode << 16 | stopLen`) + `stopLen`×float + int `easingLen` +
 * `easingLen`×float. [stopLogic] is carried verbatim so the packed `touchMode` high bits round-trip;
 * all float values may be NaN-encoded ids (raw bits preserved).
 */
class TouchExpression(
    val id: Int,
    val value: Float,
    val min: Float,
    val max: Float,
    val velocityId: Float,
    val touchEffects: Int,
    val exp: FloatArray,
    val stopLogic: Int,
    val stops: FloatArray,
    val easing: FloatArray,
) : Operation, VariableSupport {

    // ---- REM-108 (Epic-F) S2: render-only touch state. NONE of this is serialized — `write`/`read`/
    // `equals`/`hashCode` operate ONLY on the wire fields above, so the byte layout is unchanged (§2/Q4).
    // `currentValue` is the persistent source of truth for the live value (the op instance lives in the
    // remembered document across frames); `apply` loads it each eval pass, touch* mutate it. ----
    /** Stop behaviour on touch-up (`stopLogic` high bits); the low bits were the [stops] length. */
    private val stopMode: Int = stopLogic ushr 16
    /** Upstream `mMode`: 1 = absolute (ABSOLUTE_POS), else 0 = delta (accumulate drag). */
    private val mode: Int = if (stopMode == STOP_ABSOLUTE_POS) 1 else 0
    /** Wrap (circular) when `min` is the NaN sentinel with payload id 0 (upstream condition). */
    private val wrapMode: Boolean = min.isNaN() && WireTypes.idFromNan(min) == 0
    private var outMin: Float = if (wrapMode) 0f else min
    private var outMax: Float = max
    private var outDef: Float = value
    private var currentValue: Float = Float.NaN
    private var touchActive: Boolean = false
    private var valueAtDown: Float = 0f
    private var downRaw: Float = 0f

    /** Resolve NaN-var-ref bounds (e.g. `max` = a window dimension) against the store, per pass. */
    override fun updateVariables(context: RemoteContext) {
        if (max.isNaN()) outMax = context.getFloat(WireTypes.idFromNan(max))
        if (min.isNaN() && WireTypes.idFromNan(min) != 0) outMin = context.getFloat(WireTypes.idFromNan(min))
        if (value.isNaN()) outDef = context.getFloat(WireTypes.idFromNan(value))
    }

    /** Load the current touch value into the store (producer). Re-evaluates while a drag is active. */
    override fun apply(context: RemoteContext) {
        if (currentValue.isNaN()) currentValue = if (wrapMode) 0f else outDef
        if (touchActive) {
            val raw = safeEval(context)
            // Fail-closed: if the expression can't evaluate (an RPN operator the MVP evaluator doesn't yet
            // support — see [safeEval]), keep the current value rather than crash the render. The doc then
            // renders at its last/default value (graceful capability degradation), and test/PO get a flag.
            if (raw != null) currentValue = clampOrWrap(if (mode == 0) valueAtDown + (raw - downRaw) else raw)
        }
        context.loadFloat(id, wrapVal(currentValue))
    }

    /** REM-108 touch-down: capture the value + raw expression at press (for delta mode). */
    fun touchDown(context: RemoteContext) {
        touchActive = true
        if (currentValue.isNaN()) currentValue = if (wrapMode) 0f else outDef
        valueAtDown = currentValue
        downRaw = safeEval(context) ?: 0f
    }

    /**
     * Evaluate the expression, returning null if the RPN evaluator can't (an operator beyond the current
     * E2-MVP coverage). REM-108 cross-dependency: complex touch docs (e.g. `touch_wrap`'s 13-op expression)
     * need the evaluator's E-D3 operator extension — until then they degrade to their default (no crash).
     */
    private fun safeEval(context: RemoteContext): Float? =
        try {
            RpnFloatEvaluator.eval(exp, exp.size, context)
        } catch (t: Throwable) {
            null
        }

    /** REM-108 touch-drag: re-evaluate the value from the live touch position. */
    fun touchDrag(context: RemoteContext) { if (touchActive) apply(context) }

    /** REM-108 touch-up: settle to the stop position (snap; the velocity glide is deferred). */
    fun touchUp() {
        if (!touchActive) return
        touchActive = false
        if (stopMode == STOP_INSTANTLY) return // stay where it is
        currentValue = getStopPosition(currentValue)
    }

    /** REM-108 touch-cancel: abandon the drag, keep the current value. */
    fun touchCancel() { touchActive = false }

    private fun clampOrWrap(v: Float): Float = if (wrapMode) wrapVal(v) else clamp(v)

    private fun clamp(v: Float): Float {
        var r = v
        if (!outMin.isNaN()) r = maxOf(r, outMin)
        if (!outMax.isNaN()) r = minOf(r, outMax)
        return r
    }

    private fun wrapVal(pos: Float): Float {
        if (!wrapMode || outMax == 0f || outMax.isNaN()) return pos
        var p = pos % outMax
        if (p < 0) p += outMax
        return p
    }

    /** The resting value on touch-up for the corpus-supported stop-modes (0–6). Slope omitted (snap). */
    private fun getStopPosition(pos: Float): Float {
        val min = if (wrapMode) 0f else outMin
        val target = if (wrapMode) wrapVal(pos) else clamp(pos)
        return when (stopMode) {
            STOP_ENDS -> if (pos > (outMax + min) / 2f) outMax else min
            STOP_INSTANTLY, STOP_ABSOLUTE_POS -> pos
            STOP_NOTCHES_EVEN, STOP_NOTCHES_SINGLE_EVEN -> {
                val spacing = if (stops.isNotEmpty()) stops[0].toInt() else 1
                if (spacing <= 0) return target
                val notchMax = if (stops.size > 1) stops[1] else outMax
                val step = (notchMax - min) / spacing
                if (step == 0f || step.isNaN()) return target
                var notch = min + step * (0.5f + (target - outMin) / step).toInt()
                if (!wrapMode) notch = clamp(notch)
                notch
            }
            STOP_NOTCHES_PERCENTS -> nearest(target) { i -> outMin + stops[i] * (outMax - outMin) }
            STOP_NOTCHES_ABSOLUTE -> nearest(target) { i -> stops[i] }
            else -> target // STOP_GENTLY (0)
        }
    }

    /** Nearest of `stops.size` candidate positions to [target] (falls back to [target] if no stops). */
    private inline fun nearest(target: Float, pos: (Int) -> Float): Float {
        if (stops.isEmpty()) return target
        var best = pos(0); var bestD = abs(best - target)
        for (i in 1 until stops.size) { val p = pos(i); val d = abs(p - target); if (d < bestD) { bestD = d; best = p } }
        return best
    }

    override val opcode: Int get() = Operations.TOUCH_EXPRESSION

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeFloat(value)
        buffer.writeFloat(min)
        buffer.writeFloat(max)
        buffer.writeFloat(velocityId)
        buffer.writeInt(touchEffects)
        buffer.writeInt(exp.size)
        for (v in exp) buffer.writeFloat(v)
        buffer.writeInt(stopLogic)
        for (v in stops) buffer.writeFloat(v)
        buffer.writeInt(easing.size)
        for (v in easing) buffer.writeFloat(v)
    }

    override fun dump(): String =
        "TOUCH_EXPRESSION id=$id value=$value min=$min max=$max velocityId=$velocityId " +
            "touchEffects=$touchEffects exp=${exp.size} stopLogic=$stopLogic stops=${stops.size} easing=${easing.size}"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is TouchExpression &&
                id == other.id &&
                value.toRawBits() == other.value.toRawBits() && min.toRawBits() == other.min.toRawBits() &&
                max.toRawBits() == other.max.toRawBits() && velocityId.toRawBits() == other.velocityId.toRawBits() &&
                touchEffects == other.touchEffects && stopLogic == other.stopLogic &&
                rawEquals(exp, other.exp) && rawEquals(stops, other.stops) && rawEquals(easing, other.easing)
            )

    override fun hashCode(): Int {
        var h = id
        h = 31 * h + value.toRawBits()
        h = 31 * h + min.toRawBits()
        h = 31 * h + max.toRawBits()
        h = 31 * h + velocityId.toRawBits()
        h = 31 * h + touchEffects
        h = 31 * h + rawHash(exp)
        h = 31 * h + stopLogic
        h = 31 * h + rawHash(stops)
        h = 31 * h + rawHash(easing)
        return h
    }

    companion object : OperationReader {
        // Stop-modes (upstream TouchExpression.STOP_*). Corpus uses 0–6; 7 is corpus-absent (REM-108 §1).
        const val STOP_GENTLY = 0
        const val STOP_INSTANTLY = 1
        const val STOP_ENDS = 2
        const val STOP_NOTCHES_EVEN = 3
        const val STOP_NOTCHES_PERCENTS = 4
        const val STOP_NOTCHES_ABSOLUTE = 5
        const val STOP_ABSOLUTE_POS = 6
        const val STOP_NOTCHES_SINGLE_EVEN = 7

        private fun rawEquals(a: FloatArray, b: FloatArray): Boolean {
            if (a.size != b.size) return false
            for (i in a.indices) if (a[i].toRawBits() != b[i].toRawBits()) return false
            return true
        }

        private fun rawHash(a: FloatArray): Int {
            var h = 1
            for (v in a) h = 31 * h + v.toRawBits()
            return h
        }

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val id = buffer.readInt()
            val value = buffer.readFloat()
            val min = buffer.readFloat()
            val max = buffer.readFloat()
            val velocityId = buffer.readFloat()
            val touchEffects = buffer.readInt()
            val expLen = buffer.readInt() and 0xFFFF
            val exp = FloatArray(expLen) { buffer.readFloat() }
            val stopLogic = buffer.readInt()
            val stops = FloatArray(stopLogic and 0xFFFF) { buffer.readFloat() }
            val easingLen = buffer.readInt()
            val easing = FloatArray(easingLen) { buffer.readFloat() }
            operations += TouchExpression(id, value, min, max, velocityId, touchEffects, exp, stopLogic, stops, easing)
        }
    }
}
