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
 * REM-83 (W2): write the hook state onto the Compose `<canvas>` (created by `ComposeViewport`) as
 * `data-*` attributes — the DOM-visible mirror of the in-canvas testTags. Targets the first `<canvas>`;
 * fail-soft if none exists yet (early composition before the canvas mounts). `data-rc-rendered` carries
 * the honest-render gate verbatim; `data-rc-error` is removed when there is no error.
 */
actual fun mirrorRenderMarkersToDom(rendered: Boolean, error: String?, docName: String, drawCount: Int) {
    setRcDomMarkers(rendered, error ?: "", docName, drawCount)
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun setRcDomMarkers(rendered: Boolean, error: String, docName: String, drawCount: Int): Unit = js(
    """{
        var el = document.querySelector('canvas');
        if (!el) return;
        el.setAttribute('data-rc-canvas', '1');
        el.setAttribute('data-rc-rendered', rendered ? 'true' : 'false');
        el.setAttribute('data-rc-draw-count', String(drawCount));
        el.setAttribute('data-rc-doc', docName);
        if (error.length > 0) { el.setAttribute('data-rc-error', error); }
        else { el.removeAttribute('data-rc-error'); }
    }"""
)
