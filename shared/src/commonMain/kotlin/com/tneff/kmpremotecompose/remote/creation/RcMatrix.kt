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

import com.tneff.kmpremotecompose.remote.wire.WireTypes

/**
 * REM-115 (G4 Matrix-Triplet) — RPN operator constants for [matrixExpression] and helper-friendly
 * literal matrices for [matrixConstant]. Mirrors upstream
 * `androidx/compose/remote/core/operations/utilities/MatrixOperations.java` operator namespace at
 * `OFFSET = 0x320_000` (verified at `MatrixOperations.kt:33` in `player/core/`).
 *
 * Each operator is a public `Float` constant carrying a NaN-encoded id at `asNan(OFFSET + n)` —
 * mirroring exactly what upstream's RPN evaluator expects. Callers compose expressions by
 * interleaving these operator constants with operand floats (literals or `asNan(varId)` refs),
 * post-fix style:
 * ```
 * matrixExpression(
 *     30f, RcMatrix.ROT_X,       // rotateX(30°)
 *     5f, 10f, 0f, RcMatrix.TRANSLATE3,
 *     RcMatrix.MUL,               // matrix-multiply the two
 * )
 * ```
 *
 * Operator ids run `OFFSET+1..OFFSET+18` per the upstream IDs; future operators have reserved
 * slots up to `OFFSET+54` (`LAST_OP` upstream). Only the verified-active subset is exposed below.
 */
object RcMatrix {

    /** Matrix-operator id base (upstream `MatrixOperations.OFFSET`). */
    const val OFFSET: Int = 0x320_000

    /** Push the 4×4 identity matrix. */
    val IDENTITY: Float = WireTypes.asNan(OFFSET + 1)

    /** Rotation about the X axis (consumes 1 operand: angle in degrees). */
    val ROT_X: Float = WireTypes.asNan(OFFSET + 2)

    /** Rotation about the Y axis (consumes 1 operand: angle in degrees). */
    val ROT_Y: Float = WireTypes.asNan(OFFSET + 3)

    /** Rotation about the Z axis (consumes 1 operand: angle in degrees). */
    val ROT_Z: Float = WireTypes.asNan(OFFSET + 4)

    /** Translate along X (consumes 1 operand: distance). */
    val TRANSLATE_X: Float = WireTypes.asNan(OFFSET + 5)

    /** Translate along Y (consumes 1 operand: distance). */
    val TRANSLATE_Y: Float = WireTypes.asNan(OFFSET + 6)

    /** Translate along Z (consumes 1 operand: distance). */
    val TRANSLATE_Z: Float = WireTypes.asNan(OFFSET + 7)

    /** Translate 2D (consumes 2 operands: dx, dy). */
    val TRANSLATE2: Float = WireTypes.asNan(OFFSET + 8)

    /** Translate 3D (consumes 3 operands: dx, dy, dz). */
    val TRANSLATE3: Float = WireTypes.asNan(OFFSET + 9)

    /** Scale along X (consumes 1 operand: factor). */
    val SCALE_X: Float = WireTypes.asNan(OFFSET + 10)

    /** Scale along Y (consumes 1 operand: factor). */
    val SCALE_Y: Float = WireTypes.asNan(OFFSET + 11)

    /** Scale along Z (consumes 1 operand: factor). */
    val SCALE_Z: Float = WireTypes.asNan(OFFSET + 12)

    /** Scale 2D (consumes 2 operands: sx, sy). */
    val SCALE2: Float = WireTypes.asNan(OFFSET + 13)

    /** Scale 3D (consumes 3 operands: sx, sy, sz). */
    val SCALE3: Float = WireTypes.asNan(OFFSET + 14)

    /** Matrix-multiply the top two matrices on the stack. */
    val MUL: Float = WireTypes.asNan(OFFSET + 15)

    /** Build a perspective projection matrix (consumes operands per upstream PROJECTION op). */
    val PROJECTION: Float = WireTypes.asNan(OFFSET + 18)
}

/**
 * REM-115 (G4 Matrix-Triplet) — type constants for [matrixVectorMath]'s `type` slot
 * (`MatrixVectorMath.java:50-52`). Defaults to [MATRIX_VECTOR_AFFINE]; [MATRIX_VECTOR_PERSPECTIVE]
 * applies a 4×4 multiply + w-divide for projective transforms.
 */
const val MATRIX_VECTOR_AFFINE: Int = 0
const val MATRIX_VECTOR_PERSPECTIVE: Int = 1
