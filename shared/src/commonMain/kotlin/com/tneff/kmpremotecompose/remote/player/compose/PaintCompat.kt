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
package com.tneff.kmpremotecompose.remote.player.compose

import androidx.compose.ui.graphics.Paint

/**
 * Snapshot copy of a Compose [Paint] for the paint save/restore stack (CMP `Paint` has no built-in
 * copy). Mirrors upstream `ComposePaintContext`'s `paint.copy()` util by duplicating every mutable
 * attribute the geometry adapter touches.
 */
internal fun Paint.copyOf(): Paint {
    val src = this
    return Paint().apply {
        alpha = src.alpha
        isAntiAlias = src.isAntiAlias
        color = src.color
        blendMode = src.blendMode
        style = src.style
        strokeWidth = src.strokeWidth
        strokeCap = src.strokeCap
        strokeJoin = src.strokeJoin
        strokeMiterLimit = src.strokeMiterLimit
        filterQuality = src.filterQuality
        shader = src.shader
        colorFilter = src.colorFilter
        pathEffect = src.pathEffect
    }
}
