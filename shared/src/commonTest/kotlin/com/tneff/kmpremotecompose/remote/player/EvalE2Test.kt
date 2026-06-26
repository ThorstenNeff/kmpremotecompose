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
        // POW (OFFSET+8) is not in the MVP subset → evaluator throws → apply falls back to 0f.
        FloatExpression(id = 101, value = floatArrayOf(2f, 3f, op(8))).apply(ctx)
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
