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

import com.tneff.kmpremotecompose.remote.core.operations.layout.RootContentBehavior

/**
 * Lifecycle / root-level helpers that emit prolog-class ops not auto-emitted by [document].
 *
 * **W#2 default (E5 byte-contract).** The no-arg call below produces `scroll=0, alignment=34,
 * sizing=2, mode=6` — exactly the bundle every `procedure_gradient1`/`center_text1`/`look_up1`/
 * `text_path_effects` oracle emits as op #3 (assist-decoded in `docs/TECHSPEC-E5-id-order-reference.md`).
 * Reproducing that default is mandatory for byte-equality against those four; helpers that target
 * other fixtures (e.g. `procedure_simple2` with `SCALE_FIT`) pass `mode = ROOT_SCALE_FIT` explicitly.
 *
 * **Profile note.** `ROOT_CONTENT_BEHAVIOR` is V6-base only — in V7 it lives only under the
 * deprecated overlays (`Operations.kt`). [RemoteComposeWriter]'s profile gate accepts it under our
 * flat-form documents (api 6) and rejects it under map-form base profile (api 7). A call to
 * `setRootContentBehavior` therefore only round-trips on baseline-profile / flat-form documents.
 */

// Scroll values (mirrors upstream `RootContentBehavior.NONE/SCROLL_HORIZONTAL/SCROLL_VERTICAL`).
const val ROOT_SCROLL_NONE: Int = 0
const val ROOT_SCROLL_HORIZONTAL: Int = 1
const val ROOT_SCROLL_VERTICAL: Int = 2

// Alignment values (mirrors upstream `RootContentBehavior.ALIGNMENT_*`).
// Center = horizontal-center (32) + vertical-center (2).
const val ROOT_ALIGNMENT_TOP: Int = 1
const val ROOT_ALIGNMENT_VERTICAL_CENTER: Int = 2
const val ROOT_ALIGNMENT_BOTTOM: Int = 4
const val ROOT_ALIGNMENT_START: Int = 16
const val ROOT_ALIGNMENT_HORIZONTAL_CENTER: Int = 32
const val ROOT_ALIGNMENT_END: Int = 64
const val ROOT_ALIGNMENT_CENTER: Int = ROOT_ALIGNMENT_HORIZONTAL_CENTER + ROOT_ALIGNMENT_VERTICAL_CENTER

// Sizing values (mirrors upstream `RootContentBehavior.SIZING_LAYOUT/SIZING_SCALE`).
const val ROOT_SIZING_NONE: Int = 0
const val ROOT_SIZING_LAYOUT: Int = 1
const val ROOT_SIZING_SCALE: Int = 2

// Mode values for SIZING_SCALE (mirrors upstream `RootContentBehavior.SCALE_*`).
const val ROOT_SCALE_INSIDE: Int = 1
const val ROOT_SCALE_FILL_WIDTH: Int = 2
const val ROOT_SCALE_FILL_HEIGHT: Int = 3
const val ROOT_SCALE_FIT: Int = 4
const val ROOT_SCALE_CROP: Int = 5
const val ROOT_SCALE_FILL_BOUNDS: Int = 6

/**
 * Emit `ROOT_CONTENT_BEHAVIOR`. Defaults match the W#2 oracle (`0, 34, 2, 6` = NONE / CENTER /
 * SIZING_SCALE / SCALE_FILL_BOUNDS) — see file header.
 */
fun RemoteComposeContext.setRootContentBehavior(
    scroll: Int = ROOT_SCROLL_NONE,
    alignment: Int = ROOT_ALIGNMENT_CENTER,
    sizing: Int = ROOT_SIZING_SCALE,
    mode: Int = ROOT_SCALE_FILL_BOUNDS,
) {
    add(RootContentBehavior(scroll, alignment, sizing, mode))
}
