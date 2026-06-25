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

/**
 * The single entry point that registers every built-in operation group into the [Operations]
 * registry before a real document is decoded or encoded.
 *
 * It aggregates the per-group registrars — [DataOps] (REM-4 group A) and [Rem5Ops] (REM-5 group B) —
 * so there is exactly one trigger; the reader/writer facades call this. Idempotency lives in
 * [Operations] (so [Operations.resetReaders] can clear it for tests), keeping this object a pure
 * aggregator and avoiding a registry → group dependency cycle.
 */
object Builtins {
    fun register() {
        if (!Operations.tryBeginBuiltinRegistration()) return
        DataOps.register() // group A — document/data ops (REM-4)
        Rem5Ops.register() // group B — draws + layout/modifier ops (REM-5)
    }
}
