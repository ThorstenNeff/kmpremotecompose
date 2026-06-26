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

import androidx.compose.ui.graphics.Path

/**
 * **PROVISIONAL STUB — contract owned by dev-1's L2-S1 (Foundation).**
 *
 * REM-31 (L2-S2 Geometrie-Adapter) builds against this seam (build-against-stubs, like L1). It is the
 * raw document state-store surface the geometry delegate consumes — id → cached Path / Path-data /
 * bitmap / text, mirroring upstream `RemoteComposeState`'s raw accessors (NOT the player-side
 * `getPath(id,start,end)` build/cache helper, which is S2 — see [com.tneff.kmpremotecompose.remote.player.compose]).
 *
 * On S1 integration this interface is replaced by dev-1's real `RemoteContext`/state surface; the
 * accessor names below were proposed to and relayed by the PO (2026-06-26). If dev-1 finalizes
 * different names/signatures, only the integration seam adapts — the delegate's geometry logic is
 * unchanged.
 */
interface RcRenderState {
    /** True if [id] is bound to any cached object (bitmap / text / path). */
    fun containsId(id: Int): Boolean

    /** Returns the object cached under [id] (bitmap as `ImageBitmap`, text as `String`, …) or null. */
    fun getFromId(id: Int): Any?

    /** Returns the cached, already-built [Path] for [id], or null if only path-data is present. */
    fun getCachedPath(id: Int): Path?

    /** Winding rule for path [id]: `1` = even-odd, else non-zero (mirrors upstream `getPathWinding`). */
    fun getPathWinding(id: Int): Int

    /** Returns the raw float-array path-data for [id] (NaN-encoded command stream), or null. */
    fun getPathData(id: Int): FloatArray?

    /** Caches a built [Path] under [id]. */
    fun putPath(id: Int, path: Path)

    /** Stores raw float-array path-data under [id]. */
    fun putPathData(id: Int, data: FloatArray)
}
