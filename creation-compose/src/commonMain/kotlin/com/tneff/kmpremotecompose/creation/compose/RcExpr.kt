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
package com.tneff.kmpremotecompose.creation.compose

import androidx.compose.runtime.Composable
import com.tneff.kmpremotecompose.remote.creation.RcExpression
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.jvm.JvmInline

/**
 * REM-144 S2 — Higher-level RPN-DSL over [RemoteFloatExpression]'s `vararg Float` API. Lets
 * callers express expressions like `CONTINUOUS_SEC % 2f` directly instead of hand-assembling
 * `floatArrayOf(asNan(1), 2f, RcExpression.MOD)`.
 *
 * The byte output is **byte-identical** to the equivalent vararg-Float call — same NaN bits,
 * same RPN operand order, same operator ids. Stage-2-anchored by reusing `c_modifier_visibility.rc`
 * whose embedded RPN is exactly `CONTINUOUS_SEC % 2` (see REM-144 §1.3).
 *
 * Usage:
 * ```
 * val expr = RcExpression.CONTINUOUS_SEC.rcVar() % 2f.rcLit()           // CONTINUOUS_SEC % 2
 * val center = RcExpression.WINDOW_WIDTH.rcVar() / 2f.rcLit()           // WINDOW_WIDTH / 2
 * val osc = (RcExpression.TIME_IN_SEC.rcVar() * 2f.rcLit()).sin()       // sin(TIME_IN_SEC * 2)
 *
 * RemoteFloatExpression(slot, expr)  // overload accepting RcExpr — wired to byte-proven path.
 * ```
 *
 * **W7 NaN-bit-preservation:** [RcExpr] wraps a [FloatArray] (raw IEEE 754 storage) inside an
 * `@JvmInline value class` so no boxing happens on the JVM; the NaN payload of operands (e.g. the
 * signaling NaN of `RcExpression.MOD = asNan(0x310005) = 0xFFB10005`) is preserved bit-exactly.
 * Belt-and-suspenders test: `RemoteModifierEqualsTest.rcExpr_preservesRawNaNBitsThroughValueClass`.
 *
 * **Operator coverage in this slice (Q7 close, ~10 common ops):** arithmetic (`+`, `-`, `*`, `/`,
 * `%`), unary numeric (`sqrt`, `abs`), trig (`sin`, `cos`), 3-arg (`clamp`). Full coverage (all 32
 * `RcExpression.*` operators) is a mechanical follow-up if needed (T5+).
 */
@JvmInline
value class RcExpr internal constructor(val rpn: FloatArray) {

    /** Returns a NEW RcExpr with this expression's RPN followed by [other]'s RPN and `[op]`. */
    private fun binaryOp(other: RcExpr, op: Float): RcExpr {
        val out = FloatArray(rpn.size + other.rpn.size + 1)
        rpn.copyInto(out, 0)
        other.rpn.copyInto(out, rpn.size)
        out[out.size - 1] = op
        return RcExpr(out)
    }

    /** Returns a NEW RcExpr with this expression's RPN followed by `[op]` (1-arg unary). */
    private fun unaryOp(op: Float): RcExpr {
        val out = FloatArray(rpn.size + 1)
        rpn.copyInto(out, 0)
        out[out.size - 1] = op
        return RcExpr(out)
    }

    /** Returns a NEW RcExpr appending [other]'s RPN and `[op]`. For 3-arg ops like CLAMP. */
    private fun ternaryOp(b: RcExpr, c: RcExpr, op: Float): RcExpr {
        val out = FloatArray(rpn.size + b.rpn.size + c.rpn.size + 1)
        rpn.copyInto(out, 0)
        b.rpn.copyInto(out, rpn.size)
        c.rpn.copyInto(out, rpn.size + b.rpn.size)
        out[out.size - 1] = op
        return RcExpr(out)
    }

    // ----- Binary arithmetic operators -----

    /** RPN: `[this, other, ADD]` → pushes `this + other`. */
    operator fun plus(other: RcExpr): RcExpr = binaryOp(other, RcExpression.ADD)

    /** RPN: `[this, other, SUB]` → pushes `this - other`. */
    operator fun minus(other: RcExpr): RcExpr = binaryOp(other, RcExpression.SUB)

    /** RPN: `[this, other, MUL]` → pushes `this * other`. */
    operator fun times(other: RcExpr): RcExpr = binaryOp(other, RcExpression.MUL)

    /** RPN: `[this, other, DIV]` → pushes `this / other`. */
    operator fun div(other: RcExpr): RcExpr = binaryOp(other, RcExpression.DIV)

    /** RPN: `[this, other, MOD]` → pushes `this % other`. */
    operator fun rem(other: RcExpr): RcExpr = binaryOp(other, RcExpression.MOD)

    // ----- Unary numeric -----

    /** RPN: `[this, SQRT]` → pushes `sqrt(this)`. */
    fun sqrt(): RcExpr = unaryOp(RcExpression.SQRT)

    /** RPN: `[this, ABS]` → pushes `abs(this)`. */
    fun abs(): RcExpr = unaryOp(RcExpression.ABS)

    /** RPN: `[this, SIN]` → pushes `sin(this)` (radians). */
    fun sin(): RcExpr = unaryOp(RcExpression.SIN)

    /** RPN: `[this, COS]` → pushes `cos(this)` (radians). */
    fun cos(): RcExpr = unaryOp(RcExpression.COS)

    // ----- 3-arg -----

    /**
     * `clamp(this, lo, hi)`. RPN: `[this, lo, hi, CLAMP]` (upstream order: x, lo, hi).
     * See [RcExpression.CLAMP] for the operator definition.
     */
    fun clamp(lo: RcExpr, hi: RcExpr): RcExpr = ternaryOp(lo, hi, RcExpression.CLAMP)
}

// ---- Extension shorthands (Q6 close: shorthand wins over verbose factory) --------------------

/**
 * REM-144 S2 — wrap a literal [Float] as a 1-element RcExpr. The float bits are stored verbatim
 * (no NaN sanitisation), so a NaN-encoded variable ref via `WireTypes.asNan(id)` ALSO round-trips
 * through `.rcLit()` if the caller chooses — but the conventional path for variable refs is
 * [rcVar] for system-vars / [rcId] for explicit ids.
 */
fun Float.rcLit(): RcExpr = RcExpr(floatArrayOf(this))

/**
 * REM-144 S2 — wrap an already-NaN-encoded variable / operator Float (e.g. an `RcExpression.*`
 * system-var constant like `CONTINUOUS_SEC`, or `WireTypes.asNan(someId)`) as a 1-element RcExpr.
 * Names the intent at the call site.
 */
fun Float.rcVar(): RcExpr = RcExpr(floatArrayOf(this))

/**
 * REM-144 S2 — wrap a region-0 integer id (e.g. a `RemoteFloatSlot.id` after the primitive has
 * bound it) as a NaN-encoded variable ref. NOT for system ids (those have predefined
 * `RcExpression.*` constants).
 */
fun Int.rcId(): RcExpr = RcExpr(floatArrayOf(WireTypes.asNan(this)))

// ---- @Composable overload accepting RcExpr ---------------------------------------------------

/**
 * REM-144 S2 — `@Composable` overload of [RemoteFloatExpression] accepting an [RcExpr]
 * (higher-level RPN-DSL). Delegates to the byte-proven `FloatArray` overload by handing it the
 * value class's underlying `rpn` array — no behavioural divergence from the vararg / FloatArray
 * paths; just a more ergonomic call site.
 */
@Composable
fun RemoteFloatExpression(slot: RemoteFloatSlot, expression: RcExpr) {
    RemoteFloatExpression(slot, value = expression.rpn)
}
