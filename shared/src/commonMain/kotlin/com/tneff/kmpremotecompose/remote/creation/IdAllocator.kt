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
 * **Region-aware counters.** Mirrors upstream `RemoteComposeState`:
 *  - **Region 0 (plain)** — `nextId()`, starts at [START_ID] (42). Pool for every id-bearing op
 *    *except* `ID_MAP`: DATA_TEXT / DATA_INT / DATA_FLOAT / COLOR_CONSTANT / ANIMATED_FLOAT /
 *    TEXT_FROM_FLOAT / TEXT_MEASURE / COLOR_EXPRESSIONS / DATA_PATH / DATA_BITMAP /
 *    DATA_MAP_LOOKUP-result, and — verified against upstream `RemoteComposeWriter.createNamed-
 *    Variable` + the `color_table.rc` oracle — also `NAMED_VARIABLE` (upstream binds varIds from
 *    the plain pool; the `NanMap.TYPE_VARIABLE` region is creation-vestigial, nothing in the
 *    creation path calls it). REM-92 byte-blocker fix.
 *  - **Region 2 (array/data-map)** — `nextArrayId()`, starts at [START_ARRAY] (`(2 shl 20) + 42 =
 *    2097194`). Pool for `ID_MAP` / array / data-map collections. `cacheData(…, TYPE_ARRAY)` /
 *    `createID(TYPE_ARRAY)` upstream ⇒ `mIdMaps[2]++`. **Independent** of the plain counter:
 *    `nextArrayId()` does not advance `nextId()` and vice versa, exactly as upstream — verified
 *    against `RemoteComposeState.java` + `NanMap.java` (see `docs/TECHSPEC-E3-datamap-id-allocation.md`).
 *
 * **Region 1 (TYPE_VARIABLE) is intentionally NOT wired here.** Upstream's `NanMap.START_VAR =
 * (1 shl 20) + 42` exists in the state machine but the creation-side writer never allocates from
 * it — every `addNamedX` / `createNamedVariable` / `setNamedVariable` overload pulls from the
 * plain pool (verified against `RemoteComposeWriter.java`). Adding a separate region-1 counter
 * would produce byte-divergent documents AND a name→id-registry mismatch (consumers reference the
 * plain id; a region-1 key would never resolve). If a future feature needs the TYPE_VARIABLE
 * region (e.g. mid-document var-id remapping for macros), reintroduce it explicitly then.
 *
 * Keeping the counters on the context — not on a global — means each `document { … }` call starts
 * from a known baseline (deterministic byte output for the same script).
 */
class IdAllocator(start: Int = START_ID, startArray: Int = START_ARRAY) {
    private var next: Int = start
    private var nextArray: Int = startArray

    /**
     * Next region-0 id. Pool: every id-bearing op except `ID_MAP` (see [nextArrayId]). Mirrors
     * upstream `createNextAvailableId(0)` / `createNextAvailableId()` ⇒ `mNextId++`.
     */
    fun nextId(): Int = next++

    /**
     * Next region-2 (array/data-map) id. Mirrors upstream `createNextAvailableId(TYPE_ARRAY)` ⇒
     * `mIdMaps[2]++`. **Independent** of [nextId] — pulling here advances no other counter.
     */
    fun nextArrayId(): Int = nextArray++

    /** Reseed the plain counter (mirrors upstream `RemoteComposeState.setNextId`). */
    fun setNextId(id: Int) { next = id }

    /** Reseed the array (region-2) counter. */
    fun setNextArrayId(id: Int) { nextArray = id }

    /** Current value of the plain counter without consuming it. */
    fun peek(): Int = next

    /** Current value of the array (region-2) counter without consuming it. */
    fun peekArray(): Int = nextArray

    companion object {
        /** Upstream `NanMap.START_VARIABLE_ID` / `RemoteComposeState.START_ID`. */
        const val START_ID: Int = 42

        /** Upstream `NanMap.START_ARRAY = (2 << 20) + START_VARIABLE_ID = 2097194`. */
        const val START_ARRAY: Int = (2 shl 20) + START_ID
    }
}
