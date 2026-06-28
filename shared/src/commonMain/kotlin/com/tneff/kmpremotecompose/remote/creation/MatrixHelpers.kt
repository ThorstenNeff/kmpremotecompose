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
package com.tneff.kmpremotecompose.remote.creation

import com.tneff.kmpremotecompose.remote.core.operations.MatrixConstant
import com.tneff.kmpremotecompose.remote.core.operations.MatrixExpression
import com.tneff.kmpremotecompose.remote.core.operations.MatrixRestore
import com.tneff.kmpremotecompose.remote.core.operations.MatrixRotate
import com.tneff.kmpremotecompose.remote.core.operations.MatrixSave
import com.tneff.kmpremotecompose.remote.core.operations.MatrixScale
import com.tneff.kmpremotecompose.remote.core.operations.MatrixSkew
import com.tneff.kmpremotecompose.remote.core.operations.MatrixTranslate
import com.tneff.kmpremotecompose.remote.core.operations.MatrixVectorMath
import com.tneff.kmpremotecompose.remote.wire.WireTypes

/**
 * Matrix-state helpers (REM-87 / E3). None of these are id-bearing — they manipulate the player's
 * canvas-matrix stack.
 */

/** `MATRIX_SAVE` — push the current canvas matrix. Pair with [matrixRestore]. */
fun RemoteComposeContext.matrixSave() {
    add(MatrixSave())
}

/** `MATRIX_RESTORE` — pop the canvas matrix saved by the matching [matrixSave]. */
fun RemoteComposeContext.matrixRestore() {
    add(MatrixRestore())
}

/** `MATRIX_TRANSLATE` — translate the canvas by ([dx], [dy]). */
fun RemoteComposeContext.matrixTranslate(dx: Number, dy: Number) {
    add(MatrixTranslate(dx.toFloat(), dy.toFloat()))
}

/**
 * `MATRIX_SCALE` — scale the canvas by ([scaleX], [scaleY]) about pivot ([centerX], [centerY]).
 * Defaults pivot to `(0, 0)` (upstream's default).
 */
fun RemoteComposeContext.matrixScale(
    scaleX: Number,
    scaleY: Number,
    centerX: Number = 0f,
    centerY: Number = 0f,
) {
    add(
        MatrixScale(
            scaleX.toFloat(), scaleY.toFloat(),
            centerX.toFloat(), centerY.toFloat(),
        ),
    )
}

/**
 * `MATRIX_ROTATE` — rotate the canvas by [rotate] degrees about pivot ([pivotX], [pivotY]).
 * Defaults pivot to `(0, 0)`.
 */
fun RemoteComposeContext.matrixRotate(
    rotate: Number,
    pivotX: Number = 0f,
    pivotY: Number = 0f,
) {
    add(MatrixRotate(rotate.toFloat(), pivotX.toFloat(), pivotY.toFloat()))
}

/** `MATRIX_SKEW` — skew the canvas by ([skewX], [skewY]). */
fun RemoteComposeContext.matrixSkew(skewX: Number, skewY: Number) {
    add(MatrixSkew(skewX.toFloat(), skewY.toFloat()))
}

/**
 * Ergonomic scope helper: emit a `MATRIX_SAVE`, run [block], then emit a `MATRIX_RESTORE`. Mirrors
 * upstream's `painter.save { … }` shape; the `restore` runs even if [block] throws (try/finally) so
 * the matrix stack stays balanced after exceptions during scripted recording.
 */
inline fun RemoteComposeContext.matrixSaved(block: RemoteComposeContext.() -> Unit) {
    matrixSave()
    try {
        block()
    } finally {
        matrixRestore()
    }
}

// ── REM-115 G4 Matrix-Triplet (V7_BASE always-on) ──────────────────────────────
//
// All three are id-bearing: they allocate from the region-0 plain pool via [ids.nextId] and return
// the resulting matrix id as a NaN-encoded `Float` (`asNan(matrixId)`) so the caller can pass it
// directly to [matrixVectorMath] or any other consumer that takes a matrix ref. Mirror upstream
// `RemoteComposeWriter.matrixExpression` (`RemoteComposeWriter.java:4509-4513`) and
// `RemoteComposeBuffer.addMatrixConst/Expression/VectorMath` (`RemoteComposeBuffer.java:2876-2901`).
//
// Profile: V7_BASE_EXTRA — these three ops are rejected on flat-form (apiLevel<7) documents. Use a
// non-baseline profile (e.g. `PROFILE_ANDROIDX | PROFILE_EXPERIMENTAL`) to emit them.

/**
 * `MATRIX_CONSTANT` — bind a literal matrix [values] (1..16 floats, row-major for the standard
 * 4×4 case) under a freshly allocated region-0 id. Returns `asNan(matrixId)` — the NaN-encoded ref
 * to pass into [matrixVectorMath] or downstream matrix consumers. Mirrors upstream
 * `MatrixConstant.apply` (`MatrixConstant.java:109-118`); the `type` slot is reserved/unused
 * (always 0 upstream, exposed here for forward-compatibility).
 *
 * Wire: opcode + int matrixId + int type + int count + count×float (= 13 + 4*count bytes).
 *
 * The 4×4 identity matrix is the most common literal — use [identityMatrix]/`[matrixConstant(...)]`
 * with `floatArrayOf(1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1)` for that.
 */
fun RemoteComposeContext.matrixConstant(values: FloatArray, type: Int = 0): Float {
    require(values.size in 0..MatrixConstant.MAX_MATRIX_LENGTH) {
        "matrix values.size = ${values.size} exceeds upstream limit MAX_MATRIX_LENGTH=${MatrixConstant.MAX_MATRIX_LENGTH}"
    }
    val matrixId = ids.nextId()
    add(MatrixConstant(matrixId, type, values))
    return WireTypes.asNan(matrixId)
}

/**
 * `MATRIX_EXPRESSION` — build a matrix from an RPN [expression] of [RcMatrix] operator constants
 * interleaved with operand floats. Allocates a region-0 id for the result, emits the op, and
 * returns `asNan(matrixId)`. Mirrors upstream `RemoteComposeWriter.matrixExpression`
 * (`RemoteComposeWriter.java:4509-4513`).
 *
 * Wire: opcode + int matrixId + int type + int count + count×float (= 13 + 4*count bytes).
 *
 * Operand floats may carry NaN-encoded variable refs (e.g. `asNan(varId)` for a runtime-updated
 * angle). The [RcMatrix] operators are themselves NaN-encoded — the wire stores both kinds as
 * raw float bits, the player's [MatrixOperations.eval] resolves them at apply time.
 *
 * Example (rotate 30° about X, then translate (5, 10, 0)):
 * ```
 * val m = matrixExpression(floatArrayOf(
 *     30f, RcMatrix.ROT_X,
 *     5f, 10f, 0f, RcMatrix.TRANSLATE3,
 *     RcMatrix.MUL,
 * ))
 * ```
 */
fun RemoteComposeContext.matrixExpression(expression: FloatArray, type: Int = 0): Float {
    require(expression.size in 0..MatrixExpression.MAX_EXPRESSION_SIZE) {
        "matrix expression.size = ${expression.size} exceeds upstream limit " +
            "MAX_EXPRESSION_SIZE=${MatrixExpression.MAX_EXPRESSION_SIZE}"
    }
    val matrixId = ids.nextId()
    add(MatrixExpression(matrixId, type, expression))
    return WireTypes.asNan(matrixId)
}

/**
 * `MATRIX_VECTOR_MATH` — apply matrix [matrixId] (NaN-encoded ref returned by [matrixConstant] or
 * [matrixExpression]) to the [inputs] vector (1..4 floats), depositing the transformed components
 * into [outputCount] freshly allocated region-0 ids. Returns the `IntArray` of allocated output
 * ids (the caller wraps them with `asNan` if they want to embed the results in subsequent
 * expressions / draws).
 *
 * Mirrors upstream `RemoteComposeWriter.addMatrixMultiply`
 * (`RemoteComposeWriter.java:653-665`) + `RemoteComposeBuffer.addMatrixVectorMath`
 * (`RemoteComposeBuffer.java:2876-2901`) → `MatrixVectorMath.apply`.
 *
 * [type] is [MATRIX_VECTOR_AFFINE] (default) for affine 3×4 multiply, or
 * [MATRIX_VECTOR_PERSPECTIVE] for the perspective 4×4 multiply + w-divide.
 *
 * Wire: opcode + short type + int matrixId + int outCount + outCount×int + int inCount +
 * inCount×float (= 15 + 4*outCount + 4*inCount bytes).
 *
 * @param outputCount Number of output ids to allocate (1..4). Defaults to `inputs.size` for the
 *   common case where the output vector has the same dimensionality as the input.
 */
fun RemoteComposeContext.matrixVectorMath(
    matrixId: Float,
    inputs: FloatArray,
    outputCount: Int = inputs.size,
    type: Int = MATRIX_VECTOR_AFFINE,
): IntArray {
    require(inputs.size in 1..4) { "inputs.size = ${inputs.size} must be in 1..4" }
    require(outputCount in 1..4) { "outputCount = $outputCount must be in 1..4" }
    val matrixIdBare = WireTypes.idFromNan(matrixId)
    val outputs = IntArray(outputCount) { ids.nextId() }
    add(MatrixVectorMath(type = type, matrixId = matrixIdBare, outputs = outputs, inputs = inputs))
    return outputs
}
