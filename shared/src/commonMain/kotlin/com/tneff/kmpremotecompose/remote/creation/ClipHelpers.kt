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
package com.tneff.kmpremotecompose.remote.creation

import com.tneff.kmpremotecompose.remote.core.operations.draw.ClipPath
import com.tneff.kmpremotecompose.remote.core.operations.draw.ClipRect

/**
 * Clip helpers (REM-87 / E3). Neither is id-bearing.
 *
 * `CLIP_PATH` packs the path id and a region op into a single int (`pack = (regionOp shl 24) |
 * (pathId and 0xFFFFF)`); the wire is opcode + that int.
 */

/** `CLIP_RECT` — clip the canvas to the rectangle ([x1], [y1], [x2], [y2]). */
fun RemoteComposeContext.clipRect(x1: Number, y1: Number, x2: Number, y2: Number) {
    add(ClipRect(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat()))
}

/**
 * `CLIP_PATH` — clip the canvas to the path registered at [pathId]. [regionOp] selects the boolean
 * region op against the existing clip; defaults to 0 (replace/intersect — upstream's default).
 */
fun RemoteComposeContext.clipPath(pathId: Int, regionOp: Int = 0) {
    add(ClipPath(packed = (regionOp shl 24) or (pathId and 0xFFFFF)))
}
