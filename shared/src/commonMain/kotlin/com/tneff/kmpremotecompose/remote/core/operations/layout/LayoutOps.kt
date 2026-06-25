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
package com.tneff.kmpremotecompose.remote.core.operations.layout

import com.tneff.kmpremotecompose.remote.core.operations.Operations

/**
 * Registration entry point for the REM-5 layout / container / modifier operations.
 *
 * All of these live in upstream's `fillDefaultVersionMap` (present in the API-6 map and the API-7
 * base map), so they register into [Operations.Layer.V6] **and** [Operations.Layer.V7_BASE].
 */
object LayoutOps {

    private val baseLayers = listOf(Operations.Layer.V6, Operations.Layer.V7_BASE)

    /** Register all REM-5 layout/modifier readers into the base layers. */
    fun register() {
        for (layer in baseLayers) {
            Operations.register(layer, Operations.LAYOUT_ROOT, RootLayout)
            Operations.register(layer, Operations.CONTAINER_END, ContainerEnd)
            Operations.register(layer, Operations.COMPONENT_START, ComponentStart)
            Operations.register(layer, Operations.MODIFIER_WIDTH, WidthModifier)
            Operations.register(layer, Operations.MODIFIER_HEIGHT, HeightModifier)
            Operations.register(layer, Operations.MODIFIER_CLICK, ClickModifier)
        }
    }
}
