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

/**
 * Monotonic id source used by every resource-emitting helper on [RemoteComposeContext].
 *
 * **Byte-format invariant (§2).** The order in which ids are handed out is part of the document's
 * wire shape — `addText` / `addColor` / `addPathData` etc. embed the id into the operation that
 * consumes it, and a different id ⇒ different bytes.
 *
 * **Region-aware counters (REM-87, E3 byte-contract).** Mirrors upstream `RemoteComposeState`:
 *  - **Region 0 (plain)** — `nextId()`, starts at [START_ID] (42). Pool for DATA_TEXT / DATA_INT /
 *    DATA_FLOAT / COLOR_CONSTANT / ANIMATED_FLOAT / TEXT_FROM_FLOAT / TEXT_MEASURE / COLOR_EXPRESSIONS
 *    / DATA_PATH / NAMED_VARIABLE / DATA_MAP_LOOKUP-result. Every id-bearing op in §E2/§E3 except
 *    `ID_MAP` itself pulls from here.
 *  - **Region 2 (array/data-map)** — `nextArrayId()`, starts at [START_ARRAY] (`(2 shl 20) + 42 =
 *    2097194`). Pool for `ID_MAP` / array / data-map collections. `cacheData(…, TYPE_ARRAY)` /
 *    `createID(TYPE_ARRAY)` upstream ⇒ `mIdMaps[2]++`. **Independent** of the plain counter:
 *    `nextArrayId()` does not advance `nextId()` and vice versa, exactly as upstream — verified
 *    against `RemoteComposeState.java` + `NanMap.java` (see `docs/TECHSPEC-E3-datamap-id-allocation.md`).
 *  - **Region 1 (var)** — upstream `NanMap.START_VAR = (1 shl 20) + 42` — separate counter for
 *    named variables. Not wired here yet; the 4 E5-fixture targets don't exercise it. Add when an
 *    E4 helper needs it.
 *
 * Keeping the counters on the context — not on a global — means each `document { … }` call starts
 * from a known baseline (deterministic byte output for the same script).
 */
class IdAllocator(
    start: Int = START_ID,
    startArray: Int = START_ARRAY,
    startVariable: Int = START_VAR,
) {
    private var next: Int = start
    private var nextArray: Int = startArray
    private var nextVar: Int = startVariable

    /**
     * Next region-0 id. Pool: every id-bearing op except `ID_MAP` (see [nextArrayId]) and
     * `NAMED_VARIABLE` (see [nextVariableId]). Mirrors upstream `createNextAvailableId(0)` /
     * `createNextAvailableId()` ⇒ `mNextId++`.
     */
    fun nextId(): Int = next++

    /**
     * Next region-2 (array/data-map) id. Mirrors upstream `createNextAvailableId(TYPE_ARRAY)` ⇒
     * `mIdMaps[2]++`. **Independent** of [nextId] / [nextVariableId] — pulling here advances no
     * other counter.
     */
    fun nextArrayId(): Int = nextArray++

    /**
     * Next region-1 (variable) id. Mirrors upstream `createNextAvailableId(TYPE_VARIABLE)` ⇒
     * `mIdMaps[1]++`. **Independent** of [nextId] / [nextArrayId]. Used by `NamedVariable` /
     * `addNamedVariable`; the four E5-watchpoint fixtures don't exercise this counter (so a value
     * here is verifiable only against the upstream `NanMap.START_VAR` constant — see [START_VAR]).
     */
    fun nextVariableId(): Int = nextVar++

    /** Reseed the plain counter (mirrors upstream `RemoteComposeState.setNextId`). */
    fun setNextId(id: Int) { next = id }

    /** Reseed the array (region-2) counter. */
    fun setNextArrayId(id: Int) { nextArray = id }

    /** Reseed the variable (region-1) counter. */
    fun setNextVariableId(id: Int) { nextVar = id }

    /** Current value of the plain counter without consuming it. */
    fun peek(): Int = next

    /** Current value of the array (region-2) counter without consuming it. */
    fun peekArray(): Int = nextArray

    /** Current value of the variable (region-1) counter without consuming it. */
    fun peekVariable(): Int = nextVar

    companion object {
        /** Upstream `NanMap.START_VARIABLE_ID` / `RemoteComposeState.START_ID`. */
        const val START_ID: Int = 42

        /** Upstream `NanMap.START_ARRAY = (2 << 20) + START_VARIABLE_ID = 2097194`. */
        const val START_ARRAY: Int = (2 shl 20) + START_ID

        /** Upstream `NanMap.START_VAR = (1 << 20) + START_VARIABLE_ID = 1048618`. */
        const val START_VAR: Int = (1 shl 20) + START_ID
    }
}
