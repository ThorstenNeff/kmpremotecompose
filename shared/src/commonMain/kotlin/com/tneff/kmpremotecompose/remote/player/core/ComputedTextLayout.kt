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

import androidx.compose.ui.text.TextLayoutResult

/**
 * A pre-computed multi-line text layout (upstream `RcPlatformServices.ComputedTextLayout`):
 * `PaintContext.layoutComplexText` produces one, `drawComplexText` renders it.
 *
 * **Design note (REM-32, flagged to PO):** the L1 spec called for this to be `expect`/`actual`
 * (Android `StaticLayout` vs iOS Skia `Paragraph`). That split is unnecessary under the CMP-adapter
 * thesis: Compose Multiplatform's [TextLayoutResult] is already multiplatform — iOS is Skia
 * (`Paragraph`)-backed via Skiko, exactly the spec's own Skiko correction. So this is one **common**
 * class wrapping [TextLayoutResult]; no per-platform layout type is needed. (Reversible if the PO
 * wants the `expect`/`actual` form for another reason.)
 */
class ComputedTextLayout(
    /** The CMP layout result (Skia-backed on iOS). */
    val layout: TextLayoutResult,
) {
    val width: Float get() = layout.size.width.toFloat()
    val height: Float get() = layout.size.height.toFloat()
    val lineCount: Int get() = layout.lineCount
}
