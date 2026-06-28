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

import com.tneff.kmpremotecompose.remote.core.operations.IntegerExpression

/**
 * Integer-expression helpers (REM-119, G4-text-Trim fold-in).
 *
 * Mirrors upstream `RemoteComposeBuffer.addIntegerExpression(id, mask, value)` — wire shape:
 * opcode + int id + int mask + int count + count×int.
 *
 * **⚠ Render-apply GAP (flagged for Render-Backlog).** Our `IntegerExpression` op-class only
 * implements `write`/`dump` — no `apply` (no `VariableSupport`). Upstream evaluates the RPN via
 * `IntegerExpressionEvaluator` at render time; our player currently does not. This helper makes
 * the **creation surface** byte-faithful (so "create on a server" works) but **rendering** of
 * an int-expr-bound id will see whatever default the player applies (likely 0 / uninitialized).
 * Pattern mirrors `MatrixConstant` — creation-only, render-side deferred.
 *
 * Corpus presence: 1 / 173 docs (`experimental_solar_gmt.rc`).
 */

/**
 * `INTEGER_EXPRESSION` — bind a freshly allocated region-0 id to an RPN int expression
 * (`value` array, with [mask] marking which entries are ids vs literals). Returns the
 * allocated id.
 *
 * Bound by upstream `IntegerExpression.MAX_SIZE = 32` — fail-closed at the caller.
 */
fun RemoteComposeContext.addIntegerExpression(mask: Int, value: IntArray): Int {
    require(value.size in 0..IntegerExpression.MAX_SIZE) {
        "value.size = ${value.size} must be in 0..${IntegerExpression.MAX_SIZE} per upstream limit"
    }
    val id = ids.nextId()
    add(IntegerExpression(id, mask, value))
    return id
}
