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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode

/**
 * REM-128 — `@Composable` factory for a draw-only node in a `captureSingleRemoteDocument` scope.
 * Records [content] at composition time and replays it at render time via [RemoteCanvasNode] —
 * each shim method on [RemoteDrawScope] is the corresponding procedural helper, so bytes come
 * from the byte-proven path (TechSpec §0).
 *
 * The MVP surface is draw-only (no children, no modifier) — enough to byte-equal the
 * `procedure_simple2` oracle (`setRootContentBehavior(...) + drawOval(...)`). Container
 * composables land in the S2 slice (TechSpec §9).
 */
@Composable
fun RemoteCanvas(content: @RemoteComposable RemoteDrawScope.() -> Unit) {
    ComposeNode<RemoteCanvasNode, RemoteComposeApplier>(
        factory = { RemoteCanvasNode(content) },
        update = { set(content) { drawIntent = it } },
    )
}
