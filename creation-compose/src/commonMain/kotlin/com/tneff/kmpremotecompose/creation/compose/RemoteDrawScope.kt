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
package com.tneff.kmpremotecompose.creation.compose

/**
 * REM-128 W3 — minimal draw scope for the Compose-creation surface. Methods are **thin shims to
 * the procedural helpers** that emit byte-proven ops; there is no independent emission here.
 *
 * Deliberately **not** `androidx.compose.ui.graphics.Canvas`: that would force a per-platform
 * `expect`/`actual` `RecordingCanvas` (upstream uses a 1×1-Bitmap-backed canvas — Android-only,
 * painful to port to iOS Skia) for **zero byte benefit**. REM-128 is a Compose-authoring path to
 * `.rc`, not a Compose renderer, so DrawScope-drop-in parity with CMP is explicitly not a goal
 * (TechSpec §2 W3).
 *
 * MVP surface: only [drawOval] (= what `procedure_simple2` exercises). New methods land per
 * follow-up slice as more `procedure_*` oracles get pinned via the Compose path.
 */
@RemoteComposable
interface RemoteDrawScope {

    /**
     * Mirrors `RemoteComposeContext.drawOval(left, top, right, bottom)`. Coordinates may be plain
     * floats or NaN-encoded variable refs (e.g. `WireTypes.asNan(RemoteContext.ID_WINDOW_WIDTH)`)
     * — the shim passes them through to the procedural helper.
     */
    fun drawOval(left: Number, top: Number, right: Number, bottom: Number)
}
