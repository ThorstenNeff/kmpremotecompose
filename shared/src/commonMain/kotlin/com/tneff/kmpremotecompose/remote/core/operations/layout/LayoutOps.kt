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
 * The base ops live in upstream's default map (V6 + V7_BASE) and register via the membership-validated
 * [Operations.registerInBase]. `ROOT_CONTENT_BEHAVIOR` is the exception — V6 base only, and for API ≥ 7
 * only under the **deprecated** overlays (not V7_BASE); it uses the raw V6 registration plus
 * [Operations.registerInOverlay] for the deprecated overlays (registerInBase would correctly reject it).
 */
object LayoutOps {

    /** Register all REM-5 layout/modifier readers. */
    fun register() {
        Operations.registerInBase(Operations.LAYOUT_ROOT, RootLayout)
        Operations.registerInBase(Operations.CONTAINER_END, ContainerEnd)
        Operations.registerInBase(Operations.COMPONENT_START, ComponentStart)
        Operations.registerInBase(Operations.MODIFIER_WIDTH, WidthModifier)
        Operations.registerInBase(Operations.MODIFIER_HEIGHT, HeightModifier)
        Operations.registerInBase(Operations.MODIFIER_CLICK, ClickModifier)
        Operations.registerInBase(Operations.MODIFIER_BACKGROUND, BackgroundModifier)
        Operations.registerInBase(Operations.LAYOUT_BOX, BoxLayout)
        Operations.registerInBase(Operations.LAYOUT_CONTENT, LayoutContent)
        Operations.registerInBase(Operations.LAYOUT_COLUMN, ColumnLayout)
        Operations.registerInBase(Operations.MODIFIER_PADDING, PaddingModifier) // REM-17 (F6)

        // ROOT_CONTENT_BEHAVIOR: V6 base only + (API ≥ 7) deprecated overlays — NOT V7_BASE.
        // No base helper covers a V6-only op, so register V6 directly + the overlays via registerInOverlay.
        Operations.register(Operations.Layer.V6, Operations.ROOT_CONTENT_BEHAVIOR, RootContentBehavior)
        Operations.registerInOverlay(Operations.Layer.V7_ANDROIDX_DEPRECATED, Operations.ROOT_CONTENT_BEHAVIOR, RootContentBehavior)
        Operations.registerInOverlay(Operations.Layer.V7_WIDGETS_DEPRECATED, Operations.ROOT_CONTENT_BEHAVIOR, RootContentBehavior)

        // CORE_TEXT: ANDROIDX + WIDGETS overlays (not base). REM-17 (F6).
        Operations.registerInOverlay(Operations.Layer.V7_ANDROIDX, Operations.CORE_TEXT, CoreText)
        Operations.registerInOverlay(Operations.Layer.V7_WIDGETS, Operations.CORE_TEXT, CoreText)
    }
}
