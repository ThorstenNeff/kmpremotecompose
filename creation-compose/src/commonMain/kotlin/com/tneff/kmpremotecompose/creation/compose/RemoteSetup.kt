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
import com.tneff.kmpremotecompose.remote.creation.ROOT_ALIGNMENT_CENTER
import com.tneff.kmpremotecompose.remote.creation.ROOT_SCALE_FILL_BOUNDS
import com.tneff.kmpremotecompose.remote.creation.ROOT_SCROLL_NONE
import com.tneff.kmpremotecompose.remote.creation.ROOT_SIZING_SCALE
import com.tneff.kmpremotecompose.remote.creation.RemoteComposeContext
import com.tneff.kmpremotecompose.remote.creation.setRootContentBehavior

/**
 * REM-128 — `@Composable` setup leaf for the once-per-document root behavior. Mirrors
 * `RemoteComposeContext.setRootContentBehavior(...)` — the only top-level setup call exercised by
 * `procedure_simple2` (Triple-Pin §3 fixture).
 *
 * Single emission per composition; placing it multiple times is allowed (the procedural helper
 * just emits the op twice), but normal use is once at the top of the captured content.
 */
@Composable
fun RemoteRootContentBehavior(
    scroll: Int = ROOT_SCROLL_NONE,
    alignment: Int = ROOT_ALIGNMENT_CENTER,
    sizing: Int = ROOT_SIZING_SCALE,
    mode: Int = ROOT_SCALE_FILL_BOUNDS,
) {
    ComposeNode<RemoteRootContentBehaviorNode, RemoteComposeApplier>(
        factory = { RemoteRootContentBehaviorNode(scroll, alignment, sizing, mode) },
        update = {
            set(scroll) { this.scroll = it }
            set(alignment) { this.alignment = it }
            set(sizing) { this.sizing = it }
            set(mode) { this.mode = it }
        },
    )
}

internal class RemoteRootContentBehaviorNode(
    var scroll: Int,
    var alignment: Int,
    var sizing: Int,
    var mode: Int,
) : RemoteComposeNode() {
    override fun render(context: RemoteComposeContext) {
        context.setRootContentBehavior(scroll, alignment, sizing, mode)
    }
}
