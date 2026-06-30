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
package com.tneff.kmpremotecompose.remote.core.operations.utilities

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * REM-176 — KMP port of upstream `IntegerExpressionEvaluator.java`. Evaluates a RPN integer
 * expression: every entry of [exp] is either a literal/value-id (will have been resolved by the
 * caller's `updateVariables` pass) or a NaN-encoded operator id (`>= OFFSET`). A bit set in [mask]
 * at position `i` means `exp[i]` is an operator; clear ⇒ it's a literal/value to push.
 *
 * **Pure**: no I/O, no platform dependency — all 25 ops are plain int arithmetic. Faithful to
 * upstream (`IntegerExpressionEvaluator.opEval` + `eval`) — the divisor-zero guards for DIV/MOD
 * and the sign-magic for SIGN are verbatim ports. Out-of-range operator codes return 0 (matches
 * upstream's no-default-case fallthrough at the end of `opEval`).
 */
object IntegerExpressionEvaluator {

    /** All operator codes are `OFFSET + n` (a NaN-payload in the Float-encoded form). */
    const val OFFSET: Int = 0x10000

    const val OP_ADD: Int = OFFSET + 1
    const val OP_SUB: Int = OFFSET + 2
    const val OP_MUL: Int = OFFSET + 3
    const val OP_DIV: Int = OFFSET + 4
    const val OP_MOD: Int = OFFSET + 5
    const val OP_SHL: Int = OFFSET + 6
    const val OP_SHR: Int = OFFSET + 7
    const val OP_USHR: Int = OFFSET + 8
    const val OP_OR: Int = OFFSET + 9
    const val OP_AND: Int = OFFSET + 10
    const val OP_XOR: Int = OFFSET + 11
    const val OP_COPY_SIGN: Int = OFFSET + 12
    const val OP_MIN: Int = OFFSET + 13
    const val OP_MAX: Int = OFFSET + 14
    const val OP_NEG: Int = OFFSET + 15
    const val OP_ABS: Int = OFFSET + 16
    const val OP_INCR: Int = OFFSET + 17
    const val OP_DECR: Int = OFFSET + 18
    const val OP_NOT: Int = OFFSET + 19
    const val OP_SIGN: Int = OFFSET + 20
    const val OP_CLAMP: Int = OFFSET + 21
    const val OP_TERNARY_CONDITIONAL: Int = OFFSET + 22
    const val OP_MAD: Int = OFFSET + 23
    const val OP_FIRST_VAR: Int = OFFSET + 24
    const val OP_SECOND_VAR: Int = OFFSET + 25
    const val OP_THIRD_VAR: Int = OFFSET + 26

    /**
     * Evaluate the RPN expression [exp] under [mask] (operator bitmap) against optional [vars]
     * (used by OP_FIRST_VAR / OP_SECOND_VAR / OP_THIRD_VAR for function-style binding). Returns
     * the value left at the top of the stack after the walk, or 0 if [exp] is empty / malformed.
     *
     * The stack is reused destructively (matching upstream); pass a copy if you need to preserve
     * [exp] across calls. **Bounds**: [exp].size ≤ upstream `Limits.MAX_EXPRESSION_SIZE = 32` — the
     * caller (`IntegerExpression.read`) already enforces this.
     */
    fun eval(mask: Int, exp: IntArray, vararg vars: Int): Int {
        if (exp.isEmpty()) return 0
        val stack = exp // destructive reuse, faithful to upstream
        var sp = -1
        for (i in stack.indices) {
            val v = stack[i]
            if (((1 shl i) and mask) != 0) {
                sp = opEval(stack, sp, v, vars)
            } else {
                sp++
                stack[sp] = v
            }
        }
        return if (sp < 0) 0 else stack[sp]
    }

    private fun opEval(stack: IntArray, sp: Int, id: Int, vars: IntArray): Int = when (id) {
        OP_ADD -> { stack[sp - 1] = stack[sp - 1] + stack[sp]; sp - 1 }
        OP_SUB -> { stack[sp - 1] = stack[sp - 1] - stack[sp]; sp - 1 }
        OP_MUL -> { stack[sp - 1] = stack[sp - 1] * stack[sp]; sp - 1 }
        // DIV/MOD with divisor=0 → 0 (upstream, line 186/190). Java semantics for negative
        // division: same as Kotlin int division — both truncate toward zero.
        OP_DIV -> { stack[sp - 1] = if (stack[sp] == 0) 0 else stack[sp - 1] / stack[sp]; sp - 1 }
        OP_MOD -> { stack[sp - 1] = if (stack[sp] == 0) 0 else stack[sp - 1] % stack[sp]; sp - 1 }
        OP_SHL -> { stack[sp - 1] = stack[sp - 1] shl stack[sp]; sp - 1 }
        OP_SHR -> { stack[sp - 1] = stack[sp - 1] shr stack[sp]; sp - 1 }
        OP_USHR -> { stack[sp - 1] = stack[sp - 1] ushr stack[sp]; sp - 1 }
        OP_OR -> { stack[sp - 1] = stack[sp - 1] or stack[sp]; sp - 1 }
        OP_AND -> { stack[sp - 1] = stack[sp - 1] and stack[sp]; sp - 1 }
        OP_XOR -> { stack[sp - 1] = stack[sp - 1] xor stack[sp]; sp - 1 }
        // COPY_SIGN via bit-magic (upstream verbatim): result = (a ^ (b >> 31)) - (b >> 31)
        // which gives abs(a) when b≥0 and -abs(a) when b<0.
        OP_COPY_SIGN -> {
            stack[sp - 1] = (stack[sp - 1] xor (stack[sp] shr 31)) - (stack[sp] shr 31)
            sp - 1
        }
        OP_MIN -> { stack[sp - 1] = min(stack[sp - 1], stack[sp]); sp - 1 }
        OP_MAX -> { stack[sp - 1] = max(stack[sp - 1], stack[sp]); sp - 1 }
        OP_NEG -> { stack[sp] = -stack[sp]; sp }
        OP_ABS -> { stack[sp] = abs(stack[sp]); sp }
        OP_INCR -> { stack[sp] = stack[sp] + 1; sp }
        OP_DECR -> { stack[sp] = stack[sp] - 1; sp }
        OP_NOT -> { stack[sp] = stack[sp].inv(); sp }
        // SIGN via bit-magic (upstream verbatim): x<0 → -1, x==0 → 0, x>0 → 1.
        OP_SIGN -> {
            stack[sp] = (stack[sp] shr 31) or (-stack[sp] ushr 31)
            sp
        }
        // CLAMP(min=stack[sp], max=stack[sp-1], val=stack[sp-2]) per upstream signature ordering.
        OP_CLAMP -> {
            stack[sp - 2] = min(max(stack[sp - 2], stack[sp]), stack[sp - 1])
            sp - 2
        }
        // TERNARY (called OP_IFELSE in some docs): selector at top, then-branch below, else below
        // that. condition > 0 ⇒ then, else else.
        OP_TERNARY_CONDITIONAL -> {
            stack[sp - 2] = if (stack[sp] > 0) stack[sp - 1] else stack[sp - 2]
            sp - 2
        }
        // MAD(a, b, c) = a*b + c   — upstream pops 3, computes c + a*b. Verbatim:
        // `stack[sp - 2] = stack[sp] + stack[sp - 1] * stack[sp - 2]`
        OP_MAD -> {
            stack[sp - 2] = stack[sp] + stack[sp - 1] * stack[sp - 2]
            sp - 2
        }
        OP_FIRST_VAR -> { stack[sp] = if (vars.isNotEmpty()) vars[0] else 0; sp }
        OP_SECOND_VAR -> { stack[sp] = if (vars.size >= 2) vars[1] else 0; sp }
        OP_THIRD_VAR -> { stack[sp] = if (vars.size >= 3) vars[2] else 0; sp }
        else -> sp // unknown operator → no-op (upstream falls through with no default case)
    }

    /**
     * Mirror upstream `IntegerExpression.isId(mask, i, value)`: a slot is an int-id reference iff
     * its mask bit is set AND the value is below the operator [OFFSET]. Operator codes are
     * `>= OFFSET`; literal values are mask-bit-clear (no isId test).
     */
    fun isId(mask: Int, i: Int, value: Int): Boolean =
        ((1 shl i) and mask) != 0 && value < OFFSET
}
