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
import kotlin.math.floor

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
 * **Id stores (faithful to upstream `RemoteComposeState`).** Most decoded objects — bitmaps, text,
 * raw path-command data and generic data items — share one `id → object` registry ([idObjects],
 * mirroring `mIntDataMap`/`mObjectMap`); their ids are type-unique, so a single map is safe and the
 * typed accessors are views over it. **Path is the exception:** upstream keeps `mPathMap` (the cached
 * [Path] object) and `mPathData` (the raw `float[]`) as **separate** maps because one path id carries
 * **both at once** — so [pathCache] is its own map here. Collapsing them would let [putPath] clobber
 * the `float[]` under the same id and corrupt path reuse / `combinePath` / trim. Mirroring upstream,
 * [putPathData] also **invalidates** any stale cached [Path] for that id.
 */
class RemoteContext {

    /** The paint sink for the current pass. Set by [RemoteComposePlayer] before the op walk. */
    var paintContext: PaintContext? = null

    /** General id → decoded-object registry (upstream `mIntDataMap`/`mObjectMap`). */
    private val idObjects: MutableMap<Int, Any> = mutableMapOf()

    /** Cached built [Path] per id (upstream `mPathMap`), kept separate from the raw path `float[]`. */
    private val pathCache: MutableMap<Int, Path> = mutableMapOf()

    /** Per-path winding (upstream `mPathWinding`, an `IntIntMap`); absent ⇒ 0. */
    private val pathWinding: MutableMap<Int, Int> = mutableMapOf()

    // --- generic id accessors (S2 geometry adapter resolves draw ops against these) -------------

    /** The decoded object registered under [id], or null. Upstream `getFromId`. */
    fun getFromId(id: Int): Any? = idObjects[id]

    /** True if a decoded object is registered under [id]. Upstream `containsId`. */
    fun containsId(id: Int): Boolean = idObjects.containsKey(id)

    /** Register an arbitrary decoded object under [id]. */
    fun putObject(id: Int, value: Any) { idObjects[id] = value }

    // --- path: cache (mPathMap) vs raw data (mPathData) — two stores, one id --------------------

    /** A cached built [Path] for [id] (upstream `mPathMap.get`), or null. */
    fun getPath(id: Int): Path? = pathCache[id]

    /** Cache the built [Path] for [id] (upstream `mPathMap.put`). Does **not** touch the raw data. */
    fun putPath(id: Int, path: Path) { pathCache[id] = path }

    /** Raw path-command floats for [id] (upstream `mPathData.get`), turned into a [Path] by dev-2's `FloatsToPath`. */
    fun getPathData(id: Int): FloatArray? = idObjects[id] as? FloatArray

    /** Store raw path floats for [id] and **invalidate** any stale cached [Path] (upstream `mPathData.put` + `mPathMap.remove`). */
    fun putPathData(id: Int, data: FloatArray) {
        idObjects[id] = data
        pathCache.remove(id)
    }

    /**
     * The winding for path [id] (upstream `mPathWinding.get`); **0** if unset. dev-2 maps winding==1
     * to `PathFillType.EvenOdd` when building the [Path].
     */
    fun getPathWinding(id: Int): Int = pathWinding[id] ?: 0

    /** Set the winding for path [id] (upstream `mPathWinding.put`). */
    fun putPathWinding(id: Int, winding: Int) { pathWinding[id] = winding }

    // --- variable / eval stores (REM-36 Eval-Engine E1; upstream mFloatMap/mIntegerMap/mColorMap) -

    private val floatStore: MutableMap<Int, Float> = mutableMapOf()
    private val intStore: MutableMap<Int, Int> = mutableMapOf()
    private val colorStore: MutableMap<Int, Int> = mutableMapOf()
    // REM-37 cube3d: 4×4 matrices (row-major FloatArray(16)) keyed by matrixId — upstream stores these as
    // objects (putObject/getObject); a dedicated store keeps the type explicit. Produced by MATRIX_EXPRESSION,
    // consumed by MATRIX_VECTOR_MATH.
    private val matrixStore: MutableMap<Int, FloatArray> = mutableMapOf()

    /**
     * The resolved float for variable [id], or **`0f`** when unresolved — source-grounded against
     * upstream `RemoteComposeState.getFloat` → `IntFloatMap.get`, which returns `0` for a missing key
     * (the `Float.NaN` array-fill is defensive init, not the get default). E3 ops fall back to the raw
     * value for non-variable inputs; an *unresolved* data variable degrades to `0f`, not NaN.
     */
    fun getFloat(id: Int): Float = floatStore[id] ?: 0f

    /** Store the evaluated float for variable [id] (upstream `loadFloat`/`mFloatMap.put`). */
    fun loadFloat(id: Int, value: Float) { floatStore[id] = value }

    /** The resolved int for variable [id], or `0` when unresolved (upstream `IntMap.get` default). */
    fun getInt(id: Int): Int = intStore[id] ?: 0

    fun loadInt(id: Int, value: Int) { intStore[id] = value }

    /**
     * The resolved ARGB color for variable [id], or **`0`** (transparent) when unresolved — **fail-soft,
     * never throws** (mirrors the `getFloat` default-0 semantics). A `colorId` background-ref with no
     * value stays transparent rather than degenerating the render.
     */
    fun getColor(id: Int): Int = colorStore[id] ?: 0

    fun loadColor(id: Int, value: Int) { colorStore[id] = value }

    /**
     * Inject a host theme palette (REM-36 b1) — `colorId → ARGB`. Conceptually host-theme, like the
     * platform density ([DensityProvider]): the render vehicle / test seeds the real (light/dark)
     * palette before a pass for theme `colorId` refs that have no in-doc color op.
     *
     * **NOT auto-applied** by the player. The earlier auto-seed regressed ~69 color/plot/sensor docs
     * (a non-zero default for an unset `colorId` changed their color resolution); this seam is
     * opt-in only, so the default render of those docs is unchanged (`getColor` stays `0`).
     */
    fun seedThemePalette(palette: Map<Int, Int>) {
        for ((id, argb) in palette) loadColor(id, argb)
    }

    /** The 4×4 row-major matrix (FloatArray(16)) for [id], or null if not yet produced (REM-37 cube3d). */
    fun getMatrix(id: Int): FloatArray? = matrixStore[id]

    /** Store a produced 4×4 matrix under [id] (MATRIX_EXPRESSION → MATRIX_VECTOR_MATH). */
    fun loadMatrix(id: Int, value: FloatArray) { matrixStore[id] = value }

    // --- float-array collections (REM-59; upstream CollectionsAccess) — FLOAT_LIST data for chart/loop ---
    private val floatArrayStore: MutableMap<Int, FloatArray> = mutableMapOf()

    /** The float-array (FLOAT_LIST) for [id], or null. Consumed by the RPN array ops (A_DEREF/A_LEN/…). */
    fun getFloatArray(id: Int): FloatArray? = floatArrayStore[id]

    /** Store a FLOAT_LIST's values under [id] (upstream collections). */
    fun loadFloatArray(id: Int, value: FloatArray) { floatArrayStore[id] = value }

    /**
     * Seed the **system variables** into the float store (REM-36 E-Seed) — done **before** the player's
     * variable phase so ops that reference window/density (e.g. `drawOval(0,0,FLOAT_WINDOW_WIDTH,…)`)
     * resolve to real sizes instead of the `0f` store default (which collapses them to degenerate,
     * blank shapes). Source-grounded against upstream `RemoteContext`/`RemoteComposeState`: window =
     * the render **viewport** box (header doc-dims when no explicit viewport), density = the player
     * density. ids are system-variable region (0): [ID_WINDOW_WIDTH]/[ID_WINDOW_HEIGHT]/[ID_DENSITY].
     *
     * **Static time-seed (REM-36 E-Seed):** the clock/time vars (upstream `TimeVariables`) are seeded
     * from [timeSeconds] — the player's [frameTimeSeconds] seam. The MVP renders a single **static
     * frame** (`t = 0` ⇒ a valid 12:00:00 clock); the continuous animation loop (advancing the time per
     * frame) is **E-D1**. Components mirror upstream units (continuous seconds; wall-clock sec/min/hr);
     * `OFFSET_TO_UTC = 0` for the static frame. This opens the ~70 clock docs at their static frame.
     */
    fun seedSystemVariables(windowWidth: Float, windowHeight: Float, timeSeconds: Float = 0f) {
        loadFloat(ID_WINDOW_WIDTH, windowWidth)
        loadFloat(ID_WINDOW_HEIGHT, windowHeight)
        loadFloat(ID_DENSITY, density)
        // Time/clock vars from the frame-time seam (static MVP frame; E-D1 advances per frame).
        // Derive h:m:s from [timeSeconds] (elapsed), then mirror upstream `RemoteClock` exactly:
        // TIME_IN_SEC = sec-within-hour (min*60+sec), TIME_IN_MIN = min-within-day (hr*60+min),
        // CONTINUOUS_SEC = sec-within-hour + millis. t=0 ⇒ all 0 (a valid 12:00:00 clock).
        val t = if (timeSeconds.isFinite() && timeSeconds > 0f) timeSeconds else 0f
        val whole = floor(t).toInt()
        val secondOfMinute = whole % 60
        val minuteOfHour = (whole / 60) % 60
        val hourOfDay = (whole / 3600) % 24
        val millisFraction = t - whole
        loadFloat(ID_CONTINUOUS_SEC, minuteOfHour * 60f + secondOfMinute + millisFraction)
        loadFloat(ID_TIME_IN_SEC, minuteOfHour * 60f + secondOfMinute)
        loadFloat(ID_TIME_IN_MIN, hourOfDay * 60f + minuteOfHour)
        loadFloat(ID_TIME_IN_HR, hourOfDay.toFloat())
        loadFloat(ID_OFFSET_TO_UTC, 0f)
    }

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

    /**
     * Number of paint primitives drawn this pass — the **honest-render gate** (REM-8, test-2 contract):
     * each `paint.*` draw primitive calls [incrementDrawCount]; the host's `rc-rendered` hook fires only
     * when this is ≥ 1 after a pass, and a decode-ok pass that draws nothing (`drawCount == 0`) surfaces
     * `rc-error "rendered empty"` instead of false-greening on a blank canvas. Reset to 0 per pass in
     * [resetPass]. (Added by REM-8 — additive; touches no `write()`/`read()`. dev-1's S3 text primitives
     * increment it too once wired.)
     */
    var drawCount: Int = 0
        private set

    /** Record that one paint primitive drew this pass (called by the draw adapters). */
    fun incrementDrawCount() { drawCount++ }

    /** Reset per-pass transient state and bind this pass's [frameTimeSeconds] (player calls it). */
    fun resetPass(frameTimeSeconds: Float = 0f) {
        wakeInSeconds = -1f
        drawCount = 0
        this.frameTimeSeconds = frameTimeSeconds
    }

    companion object {
        // System-variable ids (region 0), source-grounded against upstream `RemoteContext`.
        /** Continuous animation seconds (upstream `ID_CONTINUOUS_SEC`). */
        const val ID_CONTINUOUS_SEC = 1
        /** Wall-clock seconds within the minute (upstream `ID_TIME_IN_SEC`). */
        const val ID_TIME_IN_SEC = 2
        /** Wall-clock minutes within the hour (upstream `ID_TIME_IN_MIN`). */
        const val ID_TIME_IN_MIN = 3
        /** Wall-clock hour (upstream `ID_TIME_IN_HR`). */
        const val ID_TIME_IN_HR = 4
        /** Render-viewport width (upstream `ID_WINDOW_WIDTH`). */
        const val ID_WINDOW_WIDTH = 5
        /** Render-viewport height (upstream `ID_WINDOW_HEIGHT`). */
        const val ID_WINDOW_HEIGHT = 6
        /** Offset to UTC, in seconds (upstream `ID_OFFSET_TO_UTC`). */
        const val ID_OFFSET_TO_UTC = 10
        /** Player density (upstream `ID_DENSITY`). */
        const val ID_DENSITY = 27
        /** Default font size (upstream `ID_FONT_SIZE`). */
        const val ID_FONT_SIZE = 33

        /**
         * An **opt-in** visible default theme palette (REM-36 b1) — a conservative mid-grey for
         * `colorId 1` so a theme `colorId` fixture (c_modifier_background_id) renders non-transparent.
         * **Placeholder** (not a source-grounded theme value); the host/test passes the real palette to
         * [seedThemePalette]. Not auto-applied (the prior auto-seed regressed ~69 docs).
         */
        val DEFAULT_THEME_PALETTE: Map<Int, Int> = mapOf(1 to 0xFF888888.toInt())
    }
}
