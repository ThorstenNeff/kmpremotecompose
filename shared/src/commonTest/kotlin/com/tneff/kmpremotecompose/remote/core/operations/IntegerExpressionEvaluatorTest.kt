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
package com.tneff.kmpremotecompose.remote.core.operations

import com.tneff.kmpremotecompose.remote.core.operations.utilities.IntegerExpressionEvaluator
import com.tneff.kmpremotecompose.remote.core.operations.utilities.IntegerExpressionEvaluator.OP_ADD
import com.tneff.kmpremotecompose.remote.core.operations.utilities.IntegerExpressionEvaluator.OP_DIV
import com.tneff.kmpremotecompose.remote.core.operations.utilities.IntegerExpressionEvaluator.OP_MOD
import com.tneff.kmpremotecompose.remote.core.operations.utilities.IntegerExpressionEvaluator.OP_MUL
import com.tneff.kmpremotecompose.remote.core.operations.utilities.IntegerExpressionEvaluator.OP_SUB
import com.tneff.kmpremotecompose.remote.core.operations.utilities.IntegerExpressionEvaluator.OP_INCR
import com.tneff.kmpremotecompose.remote.core.operations.utilities.IntegerExpressionEvaluator.OP_NEG
import com.tneff.kmpremotecompose.remote.core.operations.utilities.IntegerExpressionEvaluator.OP_ABS
import com.tneff.kmpremotecompose.remote.core.operations.utilities.IntegerExpressionEvaluator.OP_SIGN
import com.tneff.kmpremotecompose.remote.core.operations.utilities.IntegerExpressionEvaluator.OP_CLAMP
import com.tneff.kmpremotecompose.remote.core.operations.utilities.IntegerExpressionEvaluator.OP_TERNARY_CONDITIONAL
import com.tneff.kmpremotecompose.remote.core.operations.utilities.IntegerExpressionEvaluator.OP_MIN
import com.tneff.kmpremotecompose.remote.core.operations.utilities.IntegerExpressionEvaluator.OP_MAX
import kotlin.test.Test
import kotlin.test.assertEquals

class IntegerExpressionEvaluatorTest {

    /** RPN bit-mask helper: returns a mask with bits set at the given indices (operators). */
    private fun mask(vararg ops: Int): Int = ops.fold(0) { acc, i -> acc or (1 shl i) }

    @Test
    fun add() {
        // 3 5 +  → 8
        assertEquals(8, IntegerExpressionEvaluator.eval(mask(2), intArrayOf(3, 5, OP_ADD)))
    }

    @Test
    fun sub() {
        assertEquals(-2, IntegerExpressionEvaluator.eval(mask(2), intArrayOf(3, 5, OP_SUB)))
    }

    @Test
    fun mul() {
        assertEquals(15, IntegerExpressionEvaluator.eval(mask(2), intArrayOf(3, 5, OP_MUL)))
    }

    @Test
    fun div() {
        // 17 5 /  → 3   (Kotlin int div truncates toward 0, matches upstream Java)
        assertEquals(3, IntegerExpressionEvaluator.eval(mask(2), intArrayOf(17, 5, OP_DIV)))
        // -17 5 /  → -3
        assertEquals(-3, IntegerExpressionEvaluator.eval(mask(2), intArrayOf(-17, 5, OP_DIV)))
        // 17 0 /  → 0  (upstream divisor-zero guard)
        assertEquals(0, IntegerExpressionEvaluator.eval(mask(2), intArrayOf(17, 0, OP_DIV)))
    }

    @Test
    fun mod() {
        assertEquals(2, IntegerExpressionEvaluator.eval(mask(2), intArrayOf(17, 5, OP_MOD)))
        // 17 0 %  → 0  (upstream divisor-zero guard)
        assertEquals(0, IntegerExpressionEvaluator.eval(mask(2), intArrayOf(17, 0, OP_MOD)))
    }

    @Test
    fun chain_three_ops() {
        // (3 + 5) * 2 - 1
        // RPN: 3 5 + 2 * 1 -  →  16 - 1 = 15
        val result = IntegerExpressionEvaluator.eval(
            mask(2, 4, 6),
            intArrayOf(3, 5, OP_ADD, 2, OP_MUL, 1, OP_SUB),
        )
        assertEquals(15, result)
    }

    @Test
    fun negate_abs_sign() {
        assertEquals(-7, IntegerExpressionEvaluator.eval(mask(1), intArrayOf(7, OP_NEG)))
        assertEquals(7, IntegerExpressionEvaluator.eval(mask(1), intArrayOf(-7, OP_ABS)))
        // SIGN: x<0 → -1, x==0 → 0, x>0 → 1
        assertEquals(-1, IntegerExpressionEvaluator.eval(mask(1), intArrayOf(-7, OP_SIGN)))
        assertEquals(0, IntegerExpressionEvaluator.eval(mask(1), intArrayOf(0, OP_SIGN)))
        assertEquals(1, IntegerExpressionEvaluator.eval(mask(1), intArrayOf(7, OP_SIGN)))
    }

    @Test
    fun incr_min_max() {
        // INCR: unary, pops one, pushes one — but the eval signature only takes 1 op-position so
        // we test "push N then INCR".
        assertEquals(8, IntegerExpressionEvaluator.eval(mask(1), intArrayOf(7, OP_INCR)))
        assertEquals(3, IntegerExpressionEvaluator.eval(mask(2), intArrayOf(7, 3, OP_MIN)))
        assertEquals(7, IntegerExpressionEvaluator.eval(mask(2), intArrayOf(7, 3, OP_MAX)))
    }

    @Test
    fun clamp() {
        // CLAMP(val=10, max=20, min=0) per upstream signature ordering at line 254:
        //   stack[sp-2] = min(max(stack[sp-2], stack[sp]), stack[sp-1])
        // i.e. with stack = [val, max, min] → val clamped to [stack[sp]=min, stack[sp-1]=max]
        // Test: push 10, 20, 0, CLAMP → 10 (in range)
        assertEquals(10, IntegerExpressionEvaluator.eval(mask(3), intArrayOf(10, 20, 0, OP_CLAMP)))
        // Push 25, 20, 0, CLAMP → 20 (clamped to max)
        assertEquals(20, IntegerExpressionEvaluator.eval(mask(3), intArrayOf(25, 20, 0, OP_CLAMP)))
        // Push -5, 20, 0, CLAMP → 0 (clamped to min)
        assertEquals(0, IntegerExpressionEvaluator.eval(mask(3), intArrayOf(-5, 20, 0, OP_CLAMP)))
    }

    @Test
    fun ternary() {
        // TERNARY: condition at top, then-branch below, else two-below
        //   stack[sp-2] = (stack[sp] > 0) ? stack[sp-1] : stack[sp-2]
        // Push else, then, cond, TERNARY:
        assertEquals(42, IntegerExpressionEvaluator.eval(mask(3), intArrayOf(99, 42, 1, OP_TERNARY_CONDITIONAL)))
        assertEquals(99, IntegerExpressionEvaluator.eval(mask(3), intArrayOf(99, 42, 0, OP_TERNARY_CONDITIONAL)))
        assertEquals(99, IntegerExpressionEvaluator.eval(mask(3), intArrayOf(99, 42, -1, OP_TERNARY_CONDITIONAL)))
    }

    @Test
    fun epoch_days_chain_solar_gmt_recipe() {
        // The exact RPN in experimental_solar_gmt.rc op #123 — id=32 / 86400.
        // Given an epoch seed value, the evaluator must produce floor(epoch / 86400) — Kotlin int
        // division truncates toward 0 for positive values, which matches the upstream "days-since-
        // Unix-epoch" semantic. Date-specific verification (which calendar day a given epoch maps
        // to) belongs in the jvmTest oracle (Rem176OracleTest) which has java.time.
        val epoch = 1751529600  // any sample value
        val daysSinceEpoch = IntegerExpressionEvaluator.eval(
            mask(2),
            intArrayOf(epoch, 86400, OP_DIV),
        )
        assertEquals(epoch / 86400, daysSinceEpoch)
    }
}
