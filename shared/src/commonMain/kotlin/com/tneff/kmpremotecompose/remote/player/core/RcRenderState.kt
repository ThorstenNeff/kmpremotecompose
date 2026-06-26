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

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path

/**
 * **PROVISIONAL STUB — replaced at S1 integration by dev-1's concrete `RemoteContext` (REM-30,
 * `b3e6261`).** Build-against-stubs seam for REM-31 (L2-S2). The method names below are the **exact
 * subset of `RemoteContext`** the geometry delegate consumes (verified against dev-1's final contract),
 * so the post-merge reconcile is a type-name swap (`RcRenderState` → `RemoteContext`) + import only —
 * the delegate's call sites and method bodies are unchanged.
 *
 * Raw storage only: the player-side `getPath(id, start, end)` construction + `start`/`end` trim
 * (`FloatsToPath`/`PathMeasure`) is the **delegate's** job (S2), not the context's
 * (see [com.tneff.kmpremotecompose.remote.player.compose.PathGeometry]).
 *
 * Path winding has a home in the final contract (dev-1 `bc58d69`, PO opt-1): [getPathWinding] mirrors
 * upstream `RemoteComposeState.getPathWinding` (`1` = even-odd).
 */
interface RcRenderState {
    /** Cached, already-built [Path] for [id] (dev-1's separate `pathCache`), or null. */
    fun getPath(id: Int): Path?

    /** Cache a built [Path] under [id] (into the path cache, distinct from the data store). */
    fun putPath(id: Int, path: Path)

    /** Raw float-array path-data for [id] (NaN-encoded command stream), or null. */
    fun getPathData(id: Int): FloatArray?

    /** Store raw float-array path-data under [id] (invalidates any cached built [Path] for [id]). */
    fun putPathData(id: Int, data: FloatArray)

    /** Winding rule for path [id]: `1` = even-odd, else non-zero. Default 0 (`getPathWinding`). */
    fun getPathWinding(id: Int): Int

    /** The decoded [ImageBitmap] for [id], or null. */
    fun getBitmap(id: Int): ImageBitmap?
}
