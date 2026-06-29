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

import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawPath
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawTweenPath
import com.tneff.kmpremotecompose.remote.core.operations.draw.PathAppend
import com.tneff.kmpremotecompose.remote.core.operations.draw.PathCreate
import com.tneff.kmpremotecompose.remote.core.operations.draw.PathData
import com.tneff.kmpremotecompose.remote.core.operations.draw.PathTween
import com.tneff.kmpremotecompose.remote.wire.WireTypes

/**
 * Procedural path builder (REM-86 / E2). A path is a single `DATA_PATH`-style id stored on the
 * player by [pathCreate], then mutated by `pathAppend*` ops that append (NaN-marker + operands)
 * tuples to the path's float stream. [drawPath]/[drawTweenPath] consume the path id to render.
 *
 * **id-allocation (E5 byte-contract).** [pathCreate] is the **only** id-bearing op in this file —
 * it pulls the next id from [IdAllocator] (the plain region-0 pool, monotonic). `pathAppend*` and
 * `draw*` are NOT id-bearing — they reference an existing path id; re-references (e.g. drawing the
 * same path twice) do not allocate.
 *
 * **Wire-format note (verified against upstream `RemoteComposeWriter.pathAppendLineTo` / `QuadTo`).**
 * `LINE`/`QUADRATIC`/`CUBIC` carry **two zero-float padding slots** between the NaN marker and the
 * control/end coords (`[LINE_NAN, 0, 0, x, y]`); upstream's reader skips them. `MOVE` and `CLOSE`/
 * `RESET` do not have this padding (`MOVE` has 2 trailing coords, `CLOSE`/`RESET` are marker-only).
 * Reproducing the exact padding is mandatory for byte-equality.
 */

// Path command markers (mirrors upstream `PathAppend.{MOVE,LINE,QUADRATIC,CONIC,CUBIC,CLOSE,RESET}`).
// Public so power-users can construct raw [PathAppend] payloads when an op outside this helper set is
// needed (e.g. conic, until E2 adds it). NaN-encoded via [WireTypes.asNan] on emit.
private const val PATH_MOVE: Int = 10
private const val PATH_LINE: Int = 11
private const val PATH_QUADRATIC: Int = 12
private const val PATH_CUBIC: Int = 14
private const val PATH_CLOSE: Int = 15
private const val PATH_RESET: Int = 17

/**
 * Allocate a path id and emit `PATH_CREATE` with the initial pen position ([startX], [startY]).
 * Returns the id so callers can refer to it from `pathAppend*` / [drawPath] / [drawTweenPath].
 */
fun RemoteComposeContext.pathCreate(startX: Number, startY: Number): Int {
    val id = ids.nextId()
    add(PathCreate(id, startX.toFloat(), startY.toFloat()))
    return id
}

/** `PATH_ADD` (MOVE marker) — move the pen to ([x], [y]) without drawing. */
fun RemoteComposeContext.pathAppendMoveTo(pathId: Int, x: Number, y: Number) {
    add(PathAppend(pathId, floatArrayOf(WireTypes.asNan(PATH_MOVE), x.toFloat(), y.toFloat())))
}

/**
 * `PATH_ADD` (LINE marker) — draw a line to ([x], [y]). Emits `[LINE_NAN, 0, 0, x, y]`; the two zero
 * floats are upstream's padding slots — see file header.
 */
fun RemoteComposeContext.pathAppendLineTo(pathId: Int, x: Number, y: Number) {
    add(
        PathAppend(
            pathId,
            floatArrayOf(WireTypes.asNan(PATH_LINE), 0f, 0f, x.toFloat(), y.toFloat()),
        ),
    )
}

/**
 * `PATH_ADD` (QUADRATIC marker) — quadratic Bézier to ([x2], [y2]) via control ([x1], [y1]). Emits
 * `[QUAD_NAN, 0, 0, x1, y1, x2, y2]`.
 */
fun RemoteComposeContext.pathAppendQuadTo(
    pathId: Int,
    x1: Number,
    y1: Number,
    x2: Number,
    y2: Number,
) {
    add(
        PathAppend(
            pathId,
            floatArrayOf(
                WireTypes.asNan(PATH_QUADRATIC),
                0f, 0f,
                x1.toFloat(), y1.toFloat(),
                x2.toFloat(), y2.toFloat(),
            ),
        ),
    )
}

/**
 * `PATH_ADD` (CUBIC marker) — cubic Bézier to ([x3], [y3]) via controls ([x1], [y1]) and
 * ([x2], [y2]). Emits `[CUBIC_NAN, 0, 0, x1, y1, x2, y2, x3, y3]`.
 */
fun RemoteComposeContext.pathAppendCubicTo(
    pathId: Int,
    x1: Number,
    y1: Number,
    x2: Number,
    y2: Number,
    x3: Number,
    y3: Number,
) {
    add(
        PathAppend(
            pathId,
            floatArrayOf(
                WireTypes.asNan(PATH_CUBIC),
                0f, 0f,
                x1.toFloat(), y1.toFloat(),
                x2.toFloat(), y2.toFloat(),
                x3.toFloat(), y3.toFloat(),
            ),
        ),
    )
}

/** `PATH_ADD` (CLOSE marker) — close the current sub-path. Single-marker payload. */
fun RemoteComposeContext.pathAppendClose(pathId: Int) {
    add(PathAppend(pathId, floatArrayOf(WireTypes.asNan(PATH_CLOSE))))
}

/**
 * `PATH_ADD` (RESET marker) — clear the path's geometry without freeing the id. Player-side reader
 * recognises this marker and resets the stored float-path; the id remains usable for further appends.
 */
fun RemoteComposeContext.pathAppendReset(pathId: Int) {
    add(PathAppend(pathId, floatArrayOf(WireTypes.asNan(PATH_RESET))))
}

/**
 * REM-103 — one-shot `DATA_PATH` emitter from a raw [floats] stream. Complements the incremental
 * [pathCreate] + [pathAppendMoveTo]/.../[pathAppendClose] family: emits ONE `PathData` op carrying
 * the entire geometry as a `count`-prefixed float bit-stream (mirror upstream
 * `RemoteComposeWriter.addPathData(float[])` — `RemoteComposeWriter.java:1690-1727` →
 * `RemoteComposeBuffer.addPathData(id, data)` → `PathData.apply(buffer, id, data)` at
 * `RemoteComposeBuffer.java:883-890`).
 *
 * **When to use this vs. the incremental form.** The text-path-effects corpus oracle bakes its
 * geometry as a single `DATA_PATH` op (count = 2141 floats). The incremental builder produces a
 * different op-form (`PATH_CREATE` + a sequence of `PATH_ADD`) — byte-divergent against that
 * oracle. Use this helper when reproducing a pre-baked geometry (Skia-style `Path.toFloatArray()`
 * outputs). Use [pathCreate] + `pathAppend*` when scripting geometry op-by-op in the DSL.
 *
 * **Wire / id encoding.** Allocates a region-0 id via [IdAllocator.nextId]. The optional
 * [winding] is OR'd into the high byte of the wire id (`id | (winding shl 24)` — mirror
 * `RemoteComposeBuffer.java:895` overload `addPathData(id, data, winding)`); the returned id is the
 * **bare** id (low 24 bits, no winding) — that's the value to pass to [drawPath] or other path
 * consumers. Floats are written as `Float.toRawBits()`, preserving NaN-encoded variable ids
 * exactly (matches [PathData]'s wire which writes ints).
 *
 * **Fail-closed (REM-103-followup).** Mirrors upstream `RemoteComposeBuffer.java:895-898`:
 * winding ≠ 0 requires apiLevel ≥ 7. Flat-form documents (apiLevel = 6 — `Profile.Baseline`) can
 * not encode the winding-in-high-byte convention; a player on flat-form would either reject or
 * mis-decode the wire id. Without the throw the DSL would silently produce a doc the upstream
 * player can't read. Fail at write-time so the caller can pick map-form (apiLevel 7) or drop
 * the winding override.
 */
fun RemoteComposeContext.addPathData(floats: FloatArray, winding: Int = 0): Int {
    require(winding == 0 || writer.apiLevel >= 7) {
        "winding=$winding requires apiLevel >= 7 (current = ${writer.apiLevel}, flat-form). " +
            "Use document(profile = ...) with a non-baseline profile to switch to map-form, " +
            "or call addPathData without the winding override."
    }
    val id = ids.nextId()
    val wireId = if (winding != 0) id or (winding shl 24) else id
    val intBits = IntArray(floats.size) { floats[it].toRawBits() }
    add(PathData(wireId, intBits))
    return id
}

/** `DRAW_PATH` — render the path previously created at [pathId]. Does NOT allocate a new id. */
fun RemoteComposeContext.drawPath(pathId: Int) {
    add(DrawPath(pathId))
}

/**
 * REM-148 S1 — `PATH_TWEEN` (opcode [com.tneff.kmpremotecompose.remote.core.operations.Operations.PATH_TWEEN]).
 * Interpolates the geometry of [pathId1] and [pathId2] into a fresh region-0 path id at parameter
 * [tween]; the returned id can be passed to [drawPath] or another [pathTween] (chaining is the
 * `path_demo_path_tween_demo.rc` corpus pattern).
 *
 * Wire layout (17 bytes total, byte-anchored against
 * `corpus/path_demo_path_tween_demo.rc`'s op #22 sub-span): opcode(1) + outId(4) + pathId1(4) +
 * pathId2(4) + tween(4).
 *
 * **NaN-bit fidelity (W14).** [tween] is `Float`-typed rather than `Number` so a NaN-encoded
 * variable reference (e.g. `WireTypes.asNan(animatedFloatId)` — the corpus encodes tween as
 * `Float.fromBits(0xFF80002B.toInt())` referring to an `ANIMATED_FLOAT id=43`) round-trips
 * verbatim through `WireBuffer.writeFloat`'s `toRawBits()` path. `Number.toFloat()` would discard
 * the NaN payload — this signature shape forbids that mistake at the call site.
 *
 * **id-allocation (E5 byte-contract).** The output path id comes from [IdAllocator.nextId]
 * (region-0). The two inputs are pre-existing path ids (no allocation here). Mirrors REM-86
 * `pathCreate` (allocates) vs. `drawPath` (does not).
 */
fun RemoteComposeContext.pathTween(pathId1: Int, pathId2: Int, tween: Float): Int {
    val outId = ids.nextId()
    add(PathTween(outId, pathId1, pathId2, tween))
    return outId
}

/**
 * `DRAW_TWEEN_PATH` — render the linear interpolation between [path1Id] and [path2Id] at parameter
 * [tween], optionally restricted to the `[start..stop]` segment of the assembled path.
 */
fun RemoteComposeContext.drawTweenPath(
    path1Id: Int,
    path2Id: Int,
    tween: Number,
    start: Number = 0f,
    stop: Number = 1f,
) {
    add(DrawTweenPath(path1Id, path2Id, tween.toFloat(), start.toFloat(), stop.toFloat()))
}
