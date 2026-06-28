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
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

/**
 * The RPN float-expression evaluator (REM-37, Eval-Engine E2) — port of upstream
 * `AnimatedFloatExpression.eval`. A stack machine over a postfix `exp`: each element is a literal
 * (pushed), a **data-variable** NaN ref (resolved from the [RemoteContext] store and pushed), or a
 * **math operator** NaN id (`>= OFFSET`, pops operands and pushes the result).
 *
 * **Operator subset (E2 MVP + E-D3a survey extension):** ADD/SUB/MUL/DIV/MOD, MIN/MAX/CLAMP, SQRT/ABS,
 * SIN/COS, POW, SIGN, EXP, FLOOR, LOG, LN, ROUND, DEG, RAD (`SIGN`/`ROUND` unlock the cube3d rotation
 * exprs). Operators outside the subset throw [IllegalArgumentException] — the caller
 * ([com.tneff.kmpremotecompose.remote.core.operations.FloatExpression]) degrades that expression to a
 * documented fallback rather than rendering a silently-wrong value. The full 65-operator set is E-D3.
 *
 * Formulas/arity mirror upstream `opEval` verbatim (binary ops consume 2 → `sp-1`; unary → `sp`;
 * CLAMP consumes 3 → `sp-2`). Trig uses `Double` math then narrows to `Float`, as upstream does.
 */
object RpnFloatEvaluator {

    /** Math-operator id base (upstream `AnimatedFloatExpression.OFFSET`). ids `>= OFFSET` are operators. */
    const val OFFSET: Int = 0x310_000

    private const val OP_ADD = OFFSET + 1
    private const val OP_SUB = OFFSET + 2
    private const val OP_MUL = OFFSET + 3
    private const val OP_DIV = OFFSET + 4
    private const val OP_MOD = OFFSET + 5
    private const val OP_MIN = OFFSET + 6
    private const val OP_MAX = OFFSET + 7
    private const val OP_POW = OFFSET + 8
    private const val OP_SQRT = OFFSET + 9
    private const val OP_ABS = OFFSET + 10
    private const val OP_SIGN = OFFSET + 11
    private const val OP_EXP = OFFSET + 13
    private const val OP_FLOOR = OFFSET + 14
    private const val OP_LOG = OFFSET + 15
    private const val OP_LN = OFFSET + 16
    private const val OP_ROUND = OFFSET + 17
    private const val OP_SIN = OFFSET + 18
    private const val OP_COS = OFFSET + 19
    private const val OP_CLAMP = OFFSET + 27
    private const val OP_DEG = OFFSET + 29
    private const val OP_RAD = OFFSET + 30

    // REM-109 (slice 1): the highest-impact missing operators (graph/chart class). IFELSE is upstream's
    // ternary; A_SPLINE samples a FLOAT_LIST via a monotonic cubic spline (the curve behind line/area
    // charts). Previously both threw → fail-soft → variable 0 → blank/silent-wrong render.
    private const val OP_IFELSE = OFFSET + 26 // upstream TERNARY_CONDITIONAL: [a, b, cond] → cond>0 ? b : a
    private const val OP_A_SPLINE = OFFSET + 38 // [arrayId, t] → MonotonicSpline(array).getPos(t)

    // REM-109 (slice 2): the remaining E-D3 operators by impact. PINGPONG = triangle wave (used by
    // text-transform / paths_demos for back-and-forth animation phase).
    private const val OP_PINGPONG = OFFSET + 54 // [v, max] → triangle wave in [0, max]
    private const val OP_TAN = OFFSET + 20
    private const val OP_ACOS = OFFSET + 22
    private const val OP_ATAN2 = OFFSET + 24 // [y, x] → atan2(y, x)
    private const val OP_RAND = OFFSET + 39 // push a random float in [0,1)
    private const val OP_RAND_SEED = OFFSET + 40 // [seed] → reseed the RNG (0 = fresh), pops seed
    private const val OP_LERP = OFFSET + 49 // [a, b, t] → a + (b-a)·t
    private const val OP_SMOOTH_STEP = OFFSET + 50 // [val, max, min] → Hermite smoothstep in [0,1]

    // REM-109: shared RNG for RAND/RAND_SEED (mirrors upstream's static `sRandom`). Reseeded by RAND_SEED
    // so a doc that seeds gets reproducible randomness; default otherwise.
    private var rng: Random = Random.Default

    // REM-104: stack/geometry ops (upstream AnimatedFloatExpression). HYPOT computes a radial-gradient
    // radius = hypot(w/2, h/2) in countdown/demo_use_of_global; the unimplemented operator previously threw,
    // leaving the radius unset (→ 0 → false "degenerate gradient"). Siblings SQUARE/SQUARE_SUM/DUP/SWAP are
    // the same cheap cluster, added together for operator-class completeness.
    private const val OP_SQUARE_SUM = OFFSET + 43 // x*x + y*y
    private const val OP_SQUARE = OFFSET + 45 // x*x
    private const val OP_DUP = OFFSET + 46 // duplicate top
    private const val OP_HYPOT = OFFSET + 47 // sqrt(x*x + y*y)
    private const val OP_SWAP = OFFSET + 48 // swap top two

    // Array/collection ops (REM-59; upstream A_*): operate on a FLOAT_LIST via its array-id on the stack.
    private const val OP_A_DEREF = OFFSET + 32
    private const val OP_A_MAX = OFFSET + 33
    private const val OP_A_MIN = OFFSET + 34
    private const val OP_A_SUM = OFFSET + 35
    private const val OP_A_AVG = OFFSET + 36
    private const val OP_A_LEN = OFFSET + 37

    // upstream radian/degree conversion factors.
    private const val FP_TO_RAD = 57.29578f // 180/PI (DEG: radians → degrees)
    private const val FP_TO_DEG = 0.017453292f // PI/180 (RAD: degrees → radians)

    /**
     * Evaluate the first [len] elements of [exp] against the [context] variable store. Returns the
     * single remaining stack value, or `0f` for an empty expression.
     */
    fun eval(exp: FloatArray, len: Int, context: RemoteContext): Float {
        if (len <= 0) return 0f
        val stack = FloatArray(len)
        var sp = -1
        for (i in 0 until len) {
            val v = exp[i]
            if (v.isNaN()) {
                val id = WireTypes.fromNaN(v)
                if (id > OFFSET) { // upstream `pos > OFFSET` (operators start at OFFSET+1; OFFSET itself is undefined)
                    sp = opEval(stack, sp, id, context)
                } else if (WireTypes.isDataVariable(v) && context.getFloatArray(WireTypes.fromNaN(v)) != null) {
                    // an array-id with a stored FLOAT_LIST (region 2) — push the RAW NaN so an array op
                    // (A_DEREF/A_LEN/…) can `fromNaN` it back to the id (REM-59). A scalar data var (no
                    // stored array) still resolves via getFloat below.
                    stack[++sp] = v
                } else {
                    // normal/system variable reference → resolved value from the store
                    stack[++sp] = context.getFloat(WireTypes.idFromNan(v))
                }
            } else {
                stack[++sp] = v
            }
        }
        return if (sp >= 0) stack[sp] else 0f
    }

    private fun opEval(stack: FloatArray, sp: Int, id: Int, context: RemoteContext): Int = when (id) {
        // Array ops (REM-59) — the array-id is the RAW NaN on the stack (`fromNaN` → the FLOAT_LIST id).
        OP_A_DEREF -> { // [arrayId, index] → array[index]
            val arr = context.getFloatArray(WireTypes.fromNaN(stack[sp - 1]))
            stack[sp - 1] = arr?.getOrNull(stack[sp].toInt()) ?: 0f
            sp - 1
        }
        OP_A_LEN -> { stack[sp] = (context.getFloatArray(WireTypes.fromNaN(stack[sp]))?.size ?: 0).toFloat(); sp }
        OP_A_MAX -> { stack[sp] = context.getFloatArray(WireTypes.fromNaN(stack[sp]))?.maxOrNull() ?: 0f; sp }
        OP_A_MIN -> { stack[sp] = context.getFloatArray(WireTypes.fromNaN(stack[sp]))?.minOrNull() ?: 0f; sp }
        OP_A_SUM -> { stack[sp] = context.getFloatArray(WireTypes.fromNaN(stack[sp]))?.sum() ?: 0f; sp }
        OP_A_AVG -> {
            val a = context.getFloatArray(WireTypes.fromNaN(stack[sp]))
            stack[sp] = if (a != null && a.isNotEmpty()) a.average().toFloat() else 0f
            sp
        }
        // REM-109: [arrayId, t] → spline-interpolated array value (upstream getSplineValue builds the
        // spline with even time points 0..1, so t is normalised). Empty/missing array → 0 (fail-soft).
        OP_A_SPLINE -> {
            val arr = context.getFloatArray(WireTypes.fromNaN(stack[sp - 1]))
            stack[sp - 1] = if (arr != null && arr.isNotEmpty()) MonotonicSpline(null, arr).getPos(stack[sp]) else 0f
            sp - 1
        }
        OP_ADD -> { stack[sp - 1] = stack[sp - 1] + stack[sp]; sp - 1 }
        OP_SUB -> { stack[sp - 1] = stack[sp - 1] - stack[sp]; sp - 1 }
        OP_MUL -> { stack[sp - 1] = stack[sp - 1] * stack[sp]; sp - 1 }
        OP_DIV -> { stack[sp - 1] = stack[sp - 1] / stack[sp]; sp - 1 }
        OP_MOD -> { stack[sp - 1] = stack[sp - 1] % stack[sp]; sp - 1 }
        OP_MIN -> { stack[sp - 1] = min(stack[sp - 1], stack[sp]); sp - 1 }
        OP_MAX -> { stack[sp - 1] = max(stack[sp - 1], stack[sp]); sp - 1 }
        OP_CLAMP -> { stack[sp - 2] = min(max(stack[sp - 2], stack[sp]), stack[sp - 1]); sp - 2 }
        // REM-109: upstream TERNARY_CONDITIONAL — [a, b, cond] → cond>0 ? b : a (result at sp-2).
        OP_IFELSE -> { stack[sp - 2] = if (stack[sp] > 0f) stack[sp - 1] else stack[sp - 2]; sp - 2 }
        // REM-109: PINGPONG [v, max] → triangle wave; upstream: tmp = v % (2max); tmp<max ? tmp : 2max-tmp.
        OP_PINGPONG -> {
            val max2 = stack[sp] * 2
            val t = stack[sp - 1] % max2
            stack[sp - 1] = if (t < stack[sp]) t else max2 - t
            sp - 1
        }
        OP_POW -> { stack[sp - 1] = stack[sp - 1].pow(stack[sp]); sp - 1 }
        OP_SQRT -> { stack[sp] = sqrt(stack[sp]); sp }
        OP_ABS -> { stack[sp] = abs(stack[sp]); sp }
        OP_SIGN -> { stack[sp] = sign(stack[sp]); sp }
        OP_EXP -> { stack[sp] = exp(stack[sp].toDouble()).toFloat(); sp }
        OP_FLOOR -> { stack[sp] = floor(stack[sp]); sp }
        OP_LOG -> { stack[sp] = log10(stack[sp].toDouble()).toFloat(); sp }
        OP_LN -> { stack[sp] = ln(stack[sp].toDouble()).toFloat(); sp }
        OP_ROUND -> { stack[sp] = floor(stack[sp] + 0.5f); sp } // upstream Math.round = floor(x+0.5)
        OP_DEG -> { stack[sp] = stack[sp] * FP_TO_RAD; sp }
        OP_RAD -> { stack[sp] = stack[sp] * FP_TO_DEG; sp }
        OP_SIN -> { stack[sp] = sin(stack[sp].toDouble()).toFloat(); sp }
        OP_COS -> { stack[sp] = cos(stack[sp].toDouble()).toFloat(); sp }
        // REM-109: trig — TAN/ACOS unary, ATAN2 binary [y, x] (clock_demo2/experimental_solar_gmt/compass).
        OP_TAN -> { stack[sp] = tan(stack[sp]); sp }
        OP_ACOS -> { stack[sp] = acos(stack[sp]); sp }
        OP_ATAN2 -> { stack[sp - 1] = atan2(stack[sp - 1], stack[sp]); sp - 1 }
        // REM-109: RAND pushes a random [0,1); RAND_SEED reseeds (seed 0 = fresh, else deterministic).
        OP_RAND -> { stack[sp + 1] = rng.nextFloat(); sp + 1 }
        OP_RAND_SEED -> {
            val seed = stack[sp]
            rng = if (seed == 0f) Random.Default else Random(seed.toRawBits())
            sp - 1
        }
        // REM-109: LERP [a, b, t] → a+(b-a)·t (upstream linear interpolation).
        OP_LERP -> { stack[sp - 2] = stack[sp - 2] + (stack[sp - 1] - stack[sp - 2]) * stack[sp]; sp - 2 }
        // REM-109: SMOOTH_STEP [val, max, min] → 0 below min, 1 above max, else Hermite v²(3-2v).
        OP_SMOOTH_STEP -> {
            val value = stack[sp - 2]
            val hi = stack[sp - 1]
            val lo = stack[sp]
            stack[sp - 2] = when {
                value < lo -> 0f
                value > hi -> 1f
                else -> { val v = (value - lo) / (hi - lo); v * v * (3 - 2 * v) }
            }
            sp - 2
        }
        // REM-104: HYPOT and its sibling stack/geometry ops (upstream AnimatedFloatExpression).
        OP_HYPOT -> { stack[sp - 1] = hypot(stack[sp - 1], stack[sp]); sp - 1 }
        OP_SQUARE_SUM -> { stack[sp - 1] = stack[sp - 1] * stack[sp - 1] + stack[sp] * stack[sp]; sp - 1 }
        OP_SQUARE -> { stack[sp] = stack[sp] * stack[sp]; sp }
        OP_DUP -> { stack[sp + 1] = stack[sp]; sp + 1 }
        OP_SWAP -> { val t = stack[sp]; stack[sp] = stack[sp - 1]; stack[sp - 1] = t; sp }
        else -> throw IllegalArgumentException(
            "unsupported eval operator id=0x${id.toString(16)} (E2 MVP: ADD..MOD/MIN/MAX/CLAMP/SQRT/ABS/SIN/COS; E-D3 extends)",
        )
    }
}
