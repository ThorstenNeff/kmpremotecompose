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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * REM-37 cube3d — the stack-based **matrix** RPN evaluator (port of upstream
 * `operations/utilities/MatrixOperations` + `Matrix`). Builds a 4×4 matrix from an expression of literal
 * floats and matrix-**operator** NaN ids (region `0x320000+`, distinct from the float-op `0x310000`
 * region), and applies it to 3-vectors — the 3D model/projection math behind `MATRIX_EXPRESSION` /
 * `MATRIX_VECTOR_MATH`. Matrices are **row-major** `FloatArray(16)`, `index(r,c) = r*4 + c`.
 */
internal object MatrixOperations {

    /** Matrix-operator id base (upstream `MatrixOperations.OFFSET`); ids in `OFFSET+1..OFFSET+54`. */
    const val OFFSET: Int = 0x320_000
    private const val LAST_OP = OFFSET + 54

    private const val IDENTITY = 1
    private const val ROT_X = 2
    private const val ROT_Y = 3
    private const val ROT_Z = 4
    private const val TRANSLATE_X = 5
    private const val TRANSLATE_Y = 6
    private const val TRANSLATE_Z = 7
    private const val TRANSLATE2 = 8
    private const val TRANSLATE3 = 9
    private const val SCALE_X = 10
    private const val SCALE_Y = 11
    private const val SCALE_Z = 12
    private const val SCALE2 = 13
    private const val SCALE3 = 14
    private const val MUL = 15
    private const val PROJECTION = 18

    /** True if [v] is a matrix-operator NaN id (so [MatrixExpression] must not resolve it as a variable). */
    fun isOperator(v: Float): Boolean = v.isNaN() && WireTypes.idFromNan(v) in (OFFSET + 1)..LAST_OP

    /**
     * Evaluate the matrix expression [exp] (literals + operator NaNs) to a row-major `FloatArray(16)`.
     * A matrix stack starts with one identity; operators read their operands from the literal slots that
     * precede them (`exp[sp-1]`, `exp[sp-2]`, …), mirroring upstream `MatrixOperations.eval`/`opEval`.
     */
    fun eval(exp: FloatArray): FloatArray {
        val stack = Array(10) { FloatArray(16) }
        var top = 0
        identity(stack[0])
        for (i in exp.indices) {
            val v = exp[i]
            if (v.isNaN()) {
                top = opEval(stack, top, i, exp, WireTypes.idFromNan(v) - OFFSET)
            }
        }
        return stack[0]
    }

    private fun opEval(stack: Array<FloatArray>, top: Int, sp: Int, exp: FloatArray, op: Int): Int {
        val m = stack[top]
        when (op) {
            IDENTITY -> { identity(stack[top + 1]); return top + 1 }
            ROT_X -> rotateX(m, exp[sp - 1])
            ROT_Y -> rotateY(m, exp[sp - 1])
            ROT_Z -> rotateZ(m, exp[sp - 1])
            TRANSLATE_X -> translate(m, exp[sp - 1], 0f, 0f)
            TRANSLATE_Y -> translate(m, 0f, exp[sp - 1], 0f)
            TRANSLATE_Z -> translate(m, 0f, 0f, exp[sp - 1])
            TRANSLATE2 -> translate(m, exp[sp - 2], exp[sp - 1], 0f)
            TRANSLATE3 -> translate(m, exp[sp - 3], exp[sp - 2], exp[sp - 1])
            SCALE_X -> setScale(m, exp[sp - 1], 1f, 1f)
            SCALE_Y -> setScale(m, 1f, exp[sp - 1], 1f)
            SCALE_Z -> setScale(m, 1f, 1f, exp[sp - 1])
            SCALE2 -> setScale(m, exp[sp - 2], exp[sp - 1], 1f)
            SCALE3 -> setScale(m, exp[sp - 3], exp[sp - 2], exp[sp - 1])
            PROJECTION -> projection(m, exp[sp - 4], exp[sp - 3], exp[sp - 2], exp[sp - 1])
            MUL -> if (top > 0) {
                multiply(stack[top - 1], stack[top], stack[top - 1])
                return top - 1
            }
        }
        return top
    }

    // ---- 4×4 row-major matrix algebra (verbatim from upstream Matrix) -----------------------------

    private fun idx(r: Int, c: Int) = r * 4 + c

    private fun identity(m: FloatArray) {
        for (i in 0 until 16) m[i] = 0f
        m[0] = 1f; m[5] = 1f; m[10] = 1f; m[15] = 1f
    }

    /** `dest = a × b` (row-major). [dest] may alias [a] (written via a scratch copy). */
    private fun multiply(a: FloatArray, b: FloatArray, dest: FloatArray) {
        val out = FloatArray(16)
        for (i in 0 until 4) for (j in 0 until 4) {
            var sum = 0f
            for (k in 0 until 4) sum += a[i * 4 + k] * b[k * 4 + j]
            out[i * 4 + j] = sum
        }
        out.copyInto(dest)
    }

    private fun rotateX(m: FloatArray, degrees: Float) {
        val a = degrees.toRadians(); val cs = cos(a); val sn = sin(a)
        val r = FloatArray(16).also { identity(it) }
        r[idx(1, 1)] = cs; r[idx(1, 2)] = -sn; r[idx(2, 1)] = sn; r[idx(2, 2)] = cs
        multiply(m, r, m)
    }

    private fun rotateY(m: FloatArray, degrees: Float) {
        val a = degrees.toRadians(); val cs = cos(a); val sn = sin(a)
        val r = FloatArray(16).also { identity(it) }
        r[idx(0, 0)] = cs; r[idx(0, 2)] = sn; r[idx(2, 0)] = -sn; r[idx(2, 2)] = cs
        multiply(m, r, m)
    }

    private fun rotateZ(m: FloatArray, degrees: Float) {
        val a = degrees.toRadians(); val cs = cos(a); val sn = sin(a)
        val r = FloatArray(16).also { identity(it) }
        r[idx(0, 0)] = cs; r[idx(0, 1)] = -sn; r[idx(1, 0)] = sn; r[idx(1, 1)] = cs
        multiply(m, r, m)
    }

    private fun translate(m: FloatArray, x: Float, y: Float, z: Float) {
        val t = FloatArray(16).also { identity(it) }
        t[idx(0, 3)] = x; t[idx(1, 3)] = y; t[idx(2, 3)] = z
        multiply(m, t, m)
    }

    private fun setScale(m: FloatArray, x: Float, y: Float, z: Float) {
        m[idx(0, 0)] *= x; m[idx(1, 1)] *= y; m[idx(2, 2)] *= z
    }

    /** Right-multiply [m] by a perspective matrix (verbatim index layout from upstream `Matrix.projection`). */
    private fun projection(m: FloatArray, fovDegrees: Float, aspect: Float, near: Float, far: Float) {
        val f = 1f / tan(fovDegrees.toRadians() / 2f)
        val rangeInv = 1f / (near - far)
        val p = FloatArray(16)
        p[0] = f / aspect
        p[5] = f
        p[10] = (far + near) * rangeInv
        p[11] = -1f
        p[14] = 2f * far * near * rangeInv
        multiply(m, p, m)
    }

    // ---- vector transforms (MatrixVectorMath type 0 / type 1) -------------------------------------

    /** Type 0 — affine transform: `out[j] = Σ m[i+j*4]·in[i] + m[3+j*4]` (verbatim upstream). */
    fun multiplyVec(m: FloatArray, input: FloatArray, out: FloatArray) {
        for (j in out.indices) {
            var tmp = 0f
            for (i in input.indices) tmp += m[i + j * 4] * input[i]
            out[j] = tmp + m[3 + j * 4]
        }
    }

    /** Type 1 — perspective transform: extend to w=1, multiply 4×4, divide by w (verbatim upstream). */
    fun evalPerspective(m: FloatArray, input: FloatArray, out: FloatArray) {
        val inVec = FloatArray(4); inVec[3] = 1f
        for (i in input.indices) if (i < 4) inVec[i] = input[i]
        val outVec = FloatArray(4)
        for (j in 0 until 4) {
            var tmp = 0f
            for (i in 0 until 4) tmp += m[i + j * 4] * inVec[i]
            outVec[j] = tmp
        }
        for (i in out.indices) out[i] = outVec[i] / outVec[3]
    }

    private fun Float.toRadians(): Float = this * 0.017453292f
}
