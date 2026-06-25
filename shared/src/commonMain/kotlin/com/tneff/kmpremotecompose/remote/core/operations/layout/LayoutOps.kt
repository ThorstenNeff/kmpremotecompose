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
        Operations.registerInBase(Operations.VALUE_STRING_CHANGE_ACTION, ValueStringChangeAction) // REM-20 (F7)
        Operations.registerInBase(Operations.ACCESSIBILITY_SEMANTICS, CoreSemantics) // REM-20 (F7)
        // REM-20 P2 group-B (multi-doc first): CANVAS+CANVAS_CONTENT (~82 docs), ROW(10), CLIP_RECT(4),
        // COLLAPSIBLE_ROW(2). LAYOUT_CANVAS_CONTENT (207) surfaced via corpus probe as the real 82-doc lever.
        Operations.registerInBase(Operations.LAYOUT_CANVAS, CanvasLayout)
        Operations.registerInBase(Operations.LAYOUT_CANVAS_CONTENT, CanvasContent)
        Operations.registerInBase(Operations.LAYOUT_ROW, RowLayout)
        Operations.registerInBase(Operations.MODIFIER_CLIP_RECT, ClipRectModifier)
        Operations.registerInBase(Operations.LAYOUT_COLLAPSIBLE_ROW, CollapsibleRowLayout)
        // REM-23 P2 group-B round 2: LOOP_START (215), LAYOUT_STATE (217), CANVAS_OPERATIONS (173).
        Operations.registerInBase(Operations.LOOP_START, LoopStart)
        Operations.registerInBase(Operations.LAYOUT_STATE, StateLayout)
        Operations.registerInBase(Operations.CANVAS_OPERATIONS, CanvasOperations)
        Operations.registerInBase(Operations.MODIFIER_VISIBILITY, VisibilityModifier) // REM-25 (R4)

        // ROOT_CONTENT_BEHAVIOR: V6 base only + (API ≥ 7) deprecated overlays — NOT V7_BASE.
        // No base helper covers a V6-only op, so register V6 directly + the overlays via registerInOverlay.
        Operations.register(Operations.Layer.V6, Operations.ROOT_CONTENT_BEHAVIOR, RootContentBehavior)
        Operations.registerInOverlay(Operations.Layer.V7_ANDROIDX_DEPRECATED, Operations.ROOT_CONTENT_BEHAVIOR, RootContentBehavior)
        Operations.registerInOverlay(Operations.Layer.V7_WIDGETS_DEPRECATED, Operations.ROOT_CONTENT_BEHAVIOR, RootContentBehavior)

        // CORE_TEXT: ANDROIDX + WIDGETS overlays (not base). REM-17 (F6).
        Operations.registerInOverlay(Operations.Layer.V7_ANDROIDX, Operations.CORE_TEXT, CoreText)
        Operations.registerInOverlay(Operations.Layer.V7_WIDGETS, Operations.CORE_TEXT, CoreText)

        // LAYOUT_COMPUTE: ANDROIDX + WIDGETS *experimental* overlays. REM-20.
        Operations.registerInOverlay(Operations.Layer.V7_ANDROIDX_EXPERIMENTAL, Operations.LAYOUT_COMPUTE, LayoutCompute)
        Operations.registerInOverlay(Operations.Layer.V7_WIDGETS_EXPERIMENTAL, Operations.LAYOUT_COMPUTE, LayoutCompute)

        // REM-20 P2 group-B sub-batch 2 — base ops (DEFAULT_SET → V6 + V7_BASE).
        Operations.registerInBase(Operations.LAYOUT_IMAGE, ImageLayout)
        Operations.registerInBase(Operations.LAYOUT_TEXT, TextLayout)
        Operations.registerInBase(Operations.LAYOUT_FIT_BOX, FitBoxLayout)
        Operations.registerInBase(Operations.LAYOUT_COLLAPSIBLE_COLUMN, CollapsibleColumnLayout)
        Operations.registerInBase(Operations.MODIFIER_BORDER, BorderModifier)
        Operations.registerInBase(Operations.MODIFIER_ROUNDED_CLIP_RECT, RoundedClipRectModifier)
        Operations.registerInBase(Operations.MODIFIER_WIDTH_IN, WidthInModifier)
        Operations.registerInBase(Operations.MODIFIER_HEIGHT_IN, HeightInModifier)
        Operations.registerInBase(Operations.MODIFIER_ZINDEX, ZIndexModifier)
        Operations.registerInBase(Operations.MODIFIER_TOUCH_DOWN, TouchDownModifier)
        Operations.registerInBase(Operations.MODIFIER_TOUCH_UP, TouchUpModifier)
        Operations.registerInBase(Operations.MODIFIER_TOUCH_CANCEL, TouchCancelModifier)
        Operations.registerInBase(Operations.VALUE_INTEGER_CHANGE_ACTION, ValueIntegerChangeAction)
        Operations.registerInBase(Operations.CLICK_AREA, ClickArea)
        Operations.registerInBase(Operations.MODIFIER_SCROLL, ScrollModifier)
        Operations.registerInBase(Operations.MODIFIER_COLLAPSIBLE_PRIORITY, CollapsiblePriorityModifier)
        // REM-24 R3: TOUCH_EXPRESSION (157, base — touch family).
        Operations.registerInBase(Operations.TOUCH_EXPRESSION, TouchExpression)

        // Experimental-overlay ops: MODIFIER_ALIGN_BY (237, REM-20 SB2) + LAYOUT_FLOW (240, REM-24).
        // LAYOUT_FLOW now 7 fields per current ./androidx source (human decision: source wins, fixture
        // c_flow.rc is being adapted to 7 fields by test-1).
        Operations.registerInOverlay(Operations.Layer.V7_ANDROIDX_EXPERIMENTAL, Operations.MODIFIER_ALIGN_BY, AlignByModifier)
        Operations.registerInOverlay(Operations.Layer.V7_WIDGETS_EXPERIMENTAL, Operations.MODIFIER_ALIGN_BY, AlignByModifier)
        Operations.registerInOverlay(Operations.Layer.V7_ANDROIDX_EXPERIMENTAL, Operations.LAYOUT_FLOW, FlowLayout)
        Operations.registerInOverlay(Operations.Layer.V7_WIDGETS_EXPERIMENTAL, Operations.LAYOUT_FLOW, FlowLayout)
    }
}
