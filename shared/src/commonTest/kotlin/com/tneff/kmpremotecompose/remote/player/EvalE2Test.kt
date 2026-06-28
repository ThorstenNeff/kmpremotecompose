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
package com.tneff.kmpremotecompose.remote.player

import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeDocument
import com.tneff.kmpremotecompose.remote.core.operations.FloatExpression
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.RpnFloatEvaluator
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-37 Eval-Engine E2 — the RPN float evaluator (MVP operator subset) + `FLOAT_EXPRESSION` apply.
 * Headless; faithful to upstream `AnimatedFloatExpression.eval`/`opEval`.
 */
class EvalE2Test {

    /** Operator [n] as its NaN id (OFFSET + n). */
    private fun op(n: Int): Float = WireTypes.asNan(RpnFloatEvaluator.OFFSET + n)
    private fun eval(vararg exp: Float): Float = RpnFloatEvaluator.eval(exp, exp.size, RemoteContext())

    @Test
    fun ed3aOperators_matchUpstreamFormulas() {
        assertEquals(8f, eval(2f, 3f, op(8)), "POW 2^3")
        assertEquals(-1f, eval(-5f, op(11)), "SIGN(-) [cube3d]")
        assertEquals(1f, eval(5f, op(11)), "SIGN(+)")
        assertEquals(0f, eval(0f, op(11)), "SIGN(0)")
        assertEquals(1f, eval(0f, op(13)), "EXP(0)")
        assertEquals(2f, eval(2.7f, op(14)), "FLOOR")
        assertEquals(2f, eval(100f, op(15)), "LOG10(100)")
        assertEquals(0f, eval(1f, op(16)), "LN(1)")
        assertEquals(3f, eval(2.5f, op(17)), "ROUND(2.5) [cube3d, half-up]")
        assertEquals(2f, eval(2.4f, op(17)), "ROUND(2.4)")
        assertEquals(57.29578f, eval(1f, op(29)), "DEG (×180/PI)")
        assertEquals(180f * 0.017453292f, eval(180f, op(30)), "RAD (×PI/180)")
    }

    @Test
    fun rem104_hypotAndSiblingOperators_matchUpstreamFormulas() {
        // REM-104: HYPOT drives the radial-gradient radius in countdown/demo_use_of_global
        // (radius = hypot(w/2, h/2)); the unimplemented op previously threw → radius 0 → false
        // "GRADIENT_DEGENERATE". Verified against upstream AnimatedFloatExpression.
        assertEquals(5f, eval(3f, 4f, op(47)), "HYPOT(3,4) = 5")
        assertEquals(353.553f, eval(250f, 250f, op(47)), 0.01f, "HYPOT(250,250) = countdown radius")
        assertEquals(25f, eval(3f, 4f, op(43)), "SQUARE_SUM(3,4) = 9+16")
        assertEquals(9f, eval(3f, op(45)), "SQUARE(3) = 9")
        assertEquals(14f, eval(7f, op(46), op(1)), "DUP then ADD = 7+7 = 14")
        // SWAP then SUB: stack [10, 3] → swap → [3, 10] → SUB → 3-10 = -7
        assertEquals(-7f, eval(10f, 3f, op(48), op(2)), "SWAP then SUB")
    }

    @Test
    fun rem109_ifelseTernary_matchesUpstream() {
        // REM-109: upstream TERNARY_CONDITIONAL [a, b, cond] → cond>0 ? b : a (graph/chart conditionals).
        assertEquals(20f, eval(10f, 20f, 1f, op(26)), "cond>0 → b")
        assertEquals(10f, eval(10f, 20f, 0f, op(26)), "cond==0 → a")
        assertEquals(10f, eval(10f, 20f, -5f, op(26)), "cond<0 → a")
        // cond from a sub-expression: (3-2)=1 > 0 → b
        assertEquals(200f, eval(100f, 200f, 3f, 2f, op(2), op(26)), "cond>0 from sub-expression → b")
    }

    @Test
    fun rem109_pingpong_isTriangleWave() {
        // REM-109: PINGPONG [v, max] → max2=2·max; t=v%max2; t<max ? t : max2-t.
        assertEquals(1f, eval(1f, 3f, op(54)), "rising")
        assertEquals(2f, eval(4f, 3f, op(54)), "falling (4 → 6-4)")
        assertEquals(1f, eval(7f, 3f, op(54)), "wraps past 2·max")
        assertEquals(3f, eval(3f, 3f, op(54)), "peak at max")
    }

    @Test
    fun rem109_trigOperators_matchUpstream() {
        // REM-109: TAN/ACOS unary, ATAN2 binary [y, x].
        assertEquals(0f, eval(0f, op(20)), 1e-6f, "TAN(0)")
        assertEquals(1f, eval(0.7853982f, op(20)), 1e-5f, "TAN(π/4)=1")
        assertEquals(0f, eval(1f, op(22)), 1e-6f, "ACOS(1)")
        assertEquals(1.5707964f, eval(0f, op(22)), 1e-5f, "ACOS(0)=π/2")
        assertEquals(0.7853982f, eval(1f, 1f, op(24)), 1e-5f, "ATAN2(1,1)=π/4")
    }

    @Test
    fun offsetBoundary_isVariableNotOperator() {
        // `> OFFSET` (assist parity fix): id == OFFSET itself is not an operator → treated as a var ref.
        assertEquals(0f, eval(op(0)), "asNan(OFFSET) resolves as an (unset) variable → 0, no throw")
    }

    @Test
    fun mvpOperators_matchUpstreamFormulas() {
        assertEquals(5f, eval(2f, 3f, op(1)), "ADD")
        assertEquals(2f, eval(5f, 3f, op(2)), "SUB")
        assertEquals(6f, eval(2f, 3f, op(3)), "MUL")
        assertEquals(3f, eval(6f, 2f, op(4)), "DIV")
        assertEquals(1f, eval(7f, 3f, op(5)), "MOD")
        assertEquals(2f, eval(2f, 5f, op(6)), "MIN")
        assertEquals(5f, eval(2f, 5f, op(7)), "MAX")
        assertEquals(3f, eval(9f, op(9)), "SQRT")
        assertEquals(4f, eval(-4f, op(10)), "ABS")
        assertEquals(0f, eval(0f, op(18)), "SIN(0)")
        assertEquals(1f, eval(0f, op(19)), "COS(0)")
        // CLAMP(value, hi, lo) → min(max(value, lo), hi): clamp 5 into [0,3] = 3.
        assertEquals(3f, eval(5f, 3f, 0f, op(27)), "CLAMP high")
        assertEquals(1f, eval(1f, 3f, 0f, op(27)), "CLAMP in-range")
    }

    @Test
    fun nestedExpression_andVariableRefs_evaluate() {
        // (2 + 3) * 4 = 20
        assertEquals(20f, eval(2f, 3f, op(1), 4f, op(3)))

        // a variable ref resolves from the store: var(10) + 4 = 14
        val ctx = RemoteContext()
        val varId = (2 shl 20) or 7 // data-variable region
        ctx.loadFloat(varId, 10f)
        assertEquals(14f, RpnFloatEvaluator.eval(floatArrayOf(WireTypes.asNan(varId), 4f, op(1)), 3, ctx))
    }

    @Test
    fun floatExpression_apply_evaluatesAndLoadsStore() {
        val ctx = RemoteContext()
        FloatExpression(id = 100, value = floatArrayOf(2f, 3f, op(1))).apply(ctx) // 2+3
        assertEquals(5f, ctx.getFloat(100))
    }

    @Test
    fun floatExpression_nonMvpOperator_degradesToZero_noCrash() {
        val ctx = RemoteContext()
        // LERP (OFFSET+49) is still outside the subset (E-D3b) → evaluator throws → apply falls back to 0f.
        FloatExpression(id = 101, value = floatArrayOf(2f, 3f, op(49))).apply(ctx)
        assertEquals(0f, ctx.getFloat(101))
    }

    @Test
    fun applyPhase_evaluatesExpressionDuringWalk() {
        val ctx = RemoteContext()
        val doc = RemoteComposeDocument(listOf<Operation>(
            FloatExpression(id = 200, value = floatArrayOf(10f, 5f, op(2))), // 10-5
        ))
        RemoteComposePlayer(ctx).paint(doc, NoOpPaintContext(ctx))
        assertEquals(5f, ctx.getFloat(200), "FloatExpression.apply ran in Phase A")
    }
}
