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
package com.tneff.kmpremotecompose.remote.core.operations.draw

import com.tneff.kmpremotecompose.remote.core.operations.Operations

/**
 * Registration entry point for the REM-5 (op-group B) draw operations.
 *
 * The base draw primitives are profile-independent and present in both the API-6 map and the API-7
 * base map upstream, so they register into [Operations.Layer.V6] **and** [Operations.Layer.V7_BASE].
 *
 * NOTE (coordination, flagged to PO): REM-3 ships the registry but no central "register all builtin
 * ops" trigger yet, and `OpFrameworkTest` drives registration explicitly + [Operations.resetReaders].
 * This [register] is dev-2's op-group entry point; the eventual central registrar (shared convention
 * with dev-1's REM-4 group A) should call both groups' `register()` once before decoding real
 * documents. Per-op byte tests do not depend on it — they register/reset locally.
 */
object DrawOps {

    private val baseLayers = listOf(Operations.Layer.V6, Operations.Layer.V7_BASE)

    /** Register all REM-5 draw-op readers into the base layers. Idempotent per layer map. */
    fun register() {
        for (layer in baseLayers) {
            Operations.register(layer, Operations.DRAW_CIRCLE, DrawCircle)
            Operations.register(layer, Operations.DRAW_RECT, DrawRect)
            Operations.register(layer, Operations.DRAW_LINE, DrawLine)
            Operations.register(layer, Operations.DRAW_OVAL, DrawOval)
        }
    }
}
