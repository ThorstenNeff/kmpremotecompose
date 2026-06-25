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

import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawOps
import com.tneff.kmpremotecompose.remote.core.operations.layout.LayoutOps

/**
 * Single dev-2 registration entry point for the REM-5 (op-group B) operations.
 *
 * The central builtin registrar (dev-1, REM-6) calls this once — together with the group-A
 * registration — before decoding real documents. Per-op byte tests register/reset locally and do
 * not depend on it.
 */
object Rem5Ops {
    /** Register every REM-5 draw + layout/modifier op into the registry. */
    fun register() {
        DrawOps.register()
        LayoutOps.register()
    }
}
