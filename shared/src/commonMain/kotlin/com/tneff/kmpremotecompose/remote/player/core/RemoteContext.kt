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
 * Player-side runtime state for one document render pass (REM-30, L2-S1 foundation).
 *
 * Layer 1 turns `.rc` bytes into an ordered op list ([com.tneff.kmpremotecompose.remote.core.document.RemoteComposeDocument]).
 * Layer 2 *renders* that list: the [RemoteComposePlayer] walks the ops and each [PaintOperation]
 * resolves its referenced ids/variables against this context and then calls the [paintContext].
 *
 * This is the **foundation subset** of the upstream `RemoteContext`: the id→object store the player
 * walk needs, the player-evaluated density (PROJECT_CONTEXT §5: density is resolved on the player,
 * never hardcoded — see [DensityProvider]), and the small set of flags/queries the [PaintContext]
 * convenience methods delegate to. Variable evaluation, animation clocking and `declareId` runtime
 * semantics are deliberately out of scope for S1 and land with the geometry/text slices.
 *
 * **Single id space (faithful to upstream).** All decoded objects — paths, bitmaps, text and raw
 * path-command data — share one `id → object` registry. The typed accessors below are views over it,
 * so [getFromId]/[containsId] see everything the typed `put*` calls stored. The CMP graphics types
 * ([Path], [ImageBitmap]) are kept as values per TECHSPEC §1.1 (path caching = id→`Path` in commonMain).
 */
class RemoteContext {

    /** The paint sink for the current pass. Set by [RemoteComposePlayer] before the op walk. */
    var paintContext: PaintContext? = null

    /** The one id → decoded-object registry. Typed accessors are views over this map. */
    private val idObjects: MutableMap<Int, Any> = mutableMapOf()

    // --- generic id accessors (S2 geometry adapter resolves draw ops against these) -------------

    /** The decoded object registered under [id], or null. Upstream `getFromId`. */
    fun getFromId(id: Int): Any? = idObjects[id]

    /** True if any object is registered under [id]. Upstream `containsId`. */
    fun containsId(id: Int): Boolean = idObjects.containsKey(id)

    /** Register an arbitrary decoded object under [id]. */
    fun putObject(id: Int, value: Any) { idObjects[id] = value }

    // --- typed views over the id registry -------------------------------------------------------

    /** A cached built [Path] for [id] (from `DATA_PATH`/`PATH_CREATE`), or null. */
    fun getPath(id: Int): Path? = idObjects[id] as? Path

    fun putPath(id: Int, path: Path) { idObjects[id] = path }

    /** Raw path-command floats for [id], before they are turned into a [Path] (dev-2's `FloatsToPath`). */
    fun getPathData(id: Int): FloatArray? = idObjects[id] as? FloatArray

    fun putPathData(id: Int, data: FloatArray) { idObjects[id] = data }

    fun getBitmap(id: Int): ImageBitmap? = idObjects[id] as? ImageBitmap

    fun putBitmap(id: Int, bitmap: ImageBitmap) { idObjects[id] = bitmap }

    /** The decoded string for [id] (from `DATA_TEXT` and friends), or null. */
    fun getText(id: Int): String? = idObjects[id] as? String

    fun putText(id: Int, text: String) { idObjects[id] = text }

    // --- density (player-evaluated, never hardcoded; PROJECT_CONTEXT §5) ------------------------

    /**
     * The platform density in effect for this pass. Defaults to `1f` and is overwritten once via
     * [setDensity] at player init from the platform value ([DensityProvider]) or, in a CMP context,
     * from `LocalDensity`. Ops work in logical pixels; scaling is the player's job.
     */
    var density: Float = 1f
        private set

    fun setDensity(value: Float) { density = value }

    /** `DOC_DENSITY_BEHAVIOR` from the header (Layer 1). 0 = default; resolution stays player-side. */
    var densityBehavior: Int = 0

    // --- flags / version queries the PaintContext convenience methods delegate to ---------------

    var basicDebug: Boolean = false
    var visualDebug: Boolean = false
    var animationEnabled: Boolean = true

    /** Document major/minor/patch version, used by [supportsVersion]. Defaults to api-7 base. */
    var versionMajor: Int = 7
    var versionMinor: Int = 0
    var versionPatch: Int = 0

    /** Feature bitmask declared by the document (`useFeature` queries it). */
    var features: Long = 0L

    fun isBasicDebug(): Boolean = basicDebug

    fun isVisualDebug(): Boolean = visualDebug

    fun isAnimationEnabled(): Boolean = animationEnabled

    /** True if the document was written with at least the given MAJOR.MINOR.PATCH version. */
    fun supportsVersion(major: Int, minor: Int, patch: Int): Boolean {
        if (versionMajor != major) return versionMajor > major
        if (versionMinor != minor) return versionMinor > minor
        return versionPatch >= patch
    }

    fun useFeature(feature: Short): Boolean = (features and (1L shl feature.toInt())) != 0L

    /** Earliest time (seconds) the player is asked to repaint; -1 = no pending wake. */
    var wakeInSeconds: Float = -1f
        private set

    /** Request a repaint in [seconds]; keeps the soonest pending wake. */
    fun wakeIn(seconds: Float) {
        wakeInSeconds = if (wakeInSeconds < 0f) seconds else minOf(wakeInSeconds, seconds)
    }

    /**
     * The time (seconds) this pass evaluates time-driven variables (`ANIMATED_FLOAT`,
     * `continuousSeconds`, …) at — the **frame-render ↔ time-source seam** (PO 2026-06-26).
     *
     * The player sets it per pass from an injected value ([RemoteComposePlayer.paint]); it is never
     * hardcoded inside the walk. The MVP renders a single static frame at `t = 0`, but a continuous
     * animation loop attaches by simply calling `paint(...)` again with an advancing time — the walk
     * and op-dispatch stay unchanged. (Variable evaluation itself is a later slice; this is the seam.)
     */
    var frameTimeSeconds: Float = 0f
        private set

    /** Reset per-pass transient state and bind this pass's [frameTimeSeconds] (player calls it). */
    fun resetPass(frameTimeSeconds: Float = 0f) {
        wakeInSeconds = -1f
        this.frameTimeSeconds = frameTimeSeconds
    }
}
