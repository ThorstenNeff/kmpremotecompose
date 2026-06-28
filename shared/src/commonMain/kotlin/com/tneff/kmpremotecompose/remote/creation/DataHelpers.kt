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

import com.tneff.kmpremotecompose.remote.core.operations.DataMapIds
import com.tneff.kmpremotecompose.remote.core.operations.DataMapLookup
import com.tneff.kmpremotecompose.remote.core.operations.IntegerConstant

/**
 * Data-resource helpers (REM-87 / E3). Covers the id-allocation-sensitive ops on the data side:
 * DATA_INT, ID_MAP, DATA_MAP_LOOKUP.
 *
 * **id-allocation (E5 byte-contract).** Pin per [IdAllocator]'s region split:
 *  - [addInt] pulls **region-0** (plain) — like DATA_TEXT.
 *  - [addDataMapIds] pulls **region-2** — `ID_MAP` is the canonical region-2 op
 *    (`(2 shl 20) + 42 = 2097194` on first call). Independent of the plain counter.
 *  - [dataMapLookup] pulls **region-0** for the *result* id, references the region-2 dataMapId on
 *    the wire verbatim.
 */

/** `DATA_INT` — register [value] under a freshly allocated region-0 id; returns the id. */
fun RemoteComposeContext.addInt(value: Int): Int {
    val id = ids.nextId()
    add(IntegerConstant(id, value))
    return id
}

/**
 * Entry type tags for [addDataMapIds] / [DataMapIds.Entry]. Values are the **wire bytes**
 * written by [DataMapIds.write] (`buffer.writeByte(e.type)`), verified against upstream
 * `androidx/compose/remote/remote-core/.../operations/DataMapIds.java:44-46`:
 * ```
 * public static final byte TYPE_STRING = 0;
 * public static final byte TYPE_INT    = 1;
 * public static final byte TYPE_FLOAT  = 2;
 * ```
 * Only those three are defined upstream — there is **no BITMAP or PATH type** on the wire.
 * [DATA_MAP_TYPE_STRING] is the `look_up1`/E5-watchpoint default.
 *
 * REM-97 fix-history: the prior constants were `BITMAP=0, INT=1, STRING=2, FLOAT=3, PATH=4`,
 * which silently emitted divergent wire bytes (STRING → byte 2 instead of 0). Caught by
 * test-2's full E5 byte-closure (look_up1 was replicated with literal-type entries, hiding
 * the default-bug). The fix pins to the upstream wire ordinals so the helper-default and
 * the wire byte agree.
 */
const val DATA_MAP_TYPE_STRING: Int = 0
const val DATA_MAP_TYPE_INT: Int = 1
const val DATA_MAP_TYPE_FLOAT: Int = 2

/**
 * `ID_MAP` — register an id-map of [entries] under a freshly allocated **region-2** id (first call
 * returns `(2 shl 20) + 42 = 2097194` — the look_up1 byte-anchor). Returns the full region-tagged
 * id; references to this map (e.g. [dataMapLookup]) embed that id verbatim, not the index.
 */
fun RemoteComposeContext.addDataMapIds(entries: List<DataMapIds.Entry>): Int {
    val id = ids.nextArrayId()
    add(DataMapIds(id, entries))
    return id
}

/**
 * Convenience: build a [DataMapIds.Entry] tuple. [type] defaults to [DATA_MAP_TYPE_STRING] — the
 * upstream/look_up1 default and the only type the four E5 watchpoint fixtures exercise.
 */
fun dataMapEntry(name: String, valueId: Int, type: Int = DATA_MAP_TYPE_STRING): DataMapIds.Entry =
    DataMapIds.Entry(name = name, type = type, valueId = valueId)

/**
 * `DATA_MAP_LOOKUP` — bind a freshly allocated **region-0** result id to `dataMap[key]`. [dataMapId]
 * is the full region-2-tagged id returned by [addDataMapIds]; [keyStringId] is a DATA_TEXT id whose
 * value selects the entry. Returns the result id.
 */
fun RemoteComposeContext.dataMapLookup(dataMapId: Int, keyStringId: Int): Int {
    val id = ids.nextId()
    add(DataMapLookup(id, dataMapId, keyStringId))
    return id
}
