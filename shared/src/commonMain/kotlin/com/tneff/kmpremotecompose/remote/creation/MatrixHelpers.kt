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

import com.tneff.kmpremotecompose.remote.core.operations.MatrixRestore
import com.tneff.kmpremotecompose.remote.core.operations.MatrixRotate
import com.tneff.kmpremotecompose.remote.core.operations.MatrixSave
import com.tneff.kmpremotecompose.remote.core.operations.MatrixScale
import com.tneff.kmpremotecompose.remote.core.operations.MatrixSkew
import com.tneff.kmpremotecompose.remote.core.operations.MatrixTranslate

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
