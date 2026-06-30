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
package com.tneff.kmpremotecompose

import androidx.compose.ui.Modifier
import com.tneff.kmpremotecompose.remote.player.core.RcInteractionCallbacks

/**
 * REM-108 (Epic-F, REM-154) **S0 stub** — the public, capability-staffed Compose-`Modifier` that marks a
 * RemoteCompose render surface as **interactive** (TechSpec §9-Q7). It is the clean external entry point a
 * Fremd-Team applies to the `rc-canvas`, instead of reaching into `RemoteComposeApp`'s internal gesture
 * wiring.
 *
 * **S0 contract only — returns the receiver unchanged (NoOp).** Establishing the public surface here lets
 * the S1/S2 slices build against a stable signature without a behaviour change landing now. In **S2** this
 * fills in the capability-unified gesture layer — CMP `detectTapGestures` + `detectDragGestures` (which CMP
 * unifies across touch / mouse / pointer per target) — feeding `RemoteComposePlayer.touchDown/Drag/Up/Cancel`
 * and dispatching hits through the Option-B action-walk, surfacing results via [callbacks].
 *
 * **§0 capability-floor:** with no pointer (or a static/`live==false` surface) this degrades to a plain
 * static frame — never a hard fail. **§2 / render-invariance:** the S0 stub adds no gesture node and no
 * draw, so a render is byte- and pixel-identical whether or not it is applied (the zero-shift gate).
 *
 * @param callbacks the app's interaction sink; default [RcInteractionCallbacks.NoOp] (the floor).
 */
fun Modifier.rcInteractive(
    callbacks: RcInteractionCallbacks = RcInteractionCallbacks.NoOp,
): Modifier = this
