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

/**
 * An operation that participates in the **variable/eval phase** (REM-36, Eval-Engine E1 — **the
 * contract**). Upstream `VariableSupport`. The player runs this phase **before** the paint phase
 * ([RemoteComposePlayer.paint] Phase A), so draw ops read already-resolved values.
 *
 * Two roles, both per pass:
 *  - [updateVariables] — resolve this op's NaN-encoded references against the [RemoteContext] store
 *    into mutable **render-only** resolved fields (e.g. `var rScaleX`), falling back to the raw value
 *    for non-variable inputs. These fields are **not serialized** (`write`/`read` stay on the raw
 *    fields) → byte-safe.
 *  - [apply] — *evaluate* and write a result back into the store (e.g. `DATA_FLOAT` loads its value via
 *    [RemoteContext.loadFloat]; an expression op computes then loads). A producer op implements
 *    [apply]; a consumer op (a draw op resolving its coords) implements [updateVariables].
 *
 * MVP (Stufe-min, E1): no dirty/listener tracking — the player evaluates **all** `VariableSupport`
 * ops every frame (simpler; the frame is static). Reactive dirty propagation is deferred (E-D1).
 */
interface VariableSupport {

    /** Resolve NaN-id references for this op into its render-only resolved fields (consumer side). */
    fun updateVariables(context: RemoteContext) {}

    /** Evaluate and write this op's result into the [context] store (producer side). */
    fun apply(context: RemoteContext) {}
}
