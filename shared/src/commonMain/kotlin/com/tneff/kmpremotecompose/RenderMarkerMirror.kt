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

/**
 * REM-83 (W2): mirror the Maestro hook state to DOM `data-*` attributes on the wasm `<canvas>` so a
 * DOM web-driver (Maestro-chromium / Playwright) can read it. The hook *testTag*s
 * (`rc-canvas`/`rc-rendered`/`rc-error`/`rc-doc`/`rc-draw-count`) live **inside** the Skiko-painted
 * `<canvas>` and are invisible to the DOM — this is the DOM-visible side-channel.
 *
 * **Honest-render gate preserved (test-2 contract §1):** [rendered] is the SAME boolean the
 * `rc-rendered` testTag gates on (`committed && drawCount > 0`) — the mirror only reports a real, painted
 * frame; it never sets `data-rc-rendered=true` ahead of a paint. This is a read-only side-channel: it
 * cannot green a frame that the player didn't actually draw.
 *
 * No-op on every non-web target (Android/iOS/Desktop have no DOM); the real write lives in
 * [wasmJs][mirrorRenderMarkersToDom]. Called once per committed composition from `RemoteComposeApp`.
 *
 * @param rendered `committed && drawCount > 0` — the honest-render gate.
 * @param error the composed error string (decode / render / "rendered empty"), or null.
 * @param docName the actually-rendered fixture name (matches the `rc-doc` testTag).
 * @param drawCount paint primitives drawn this pass (matches `rc-draw-count`).
 */
expect fun mirrorRenderMarkersToDom(rendered: Boolean, error: String?, docName: String, drawCount: Int)
