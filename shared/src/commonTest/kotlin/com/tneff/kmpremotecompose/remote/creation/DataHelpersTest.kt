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

import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.DataMapIds
import com.tneff.kmpremotecompose.remote.core.operations.DataMapLookup
import com.tneff.kmpremotecompose.remote.core.operations.IntegerConstant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-90 (E3) — Data-helper byte tests. Pins:
 *  - `addInt` is region-0 id-bearing.
 *  - `addDataMapIds` is **region-2** id-bearing — first call returns the upstream byte-anchor
 *    `(2 shl 20) + 42 = 2097194` from the look_up1 oracle.
 *  - `dataMapLookup` is region-0 id-bearing (result id) and references the full region-tagged
 *    dataMapId on the wire.
 *  - The two counters are independent in DSL use (W#4).
 */
class DataHelpersTest {

    @Test
    fun addInt_allocatesPlainId_andEmitsDataInt() {
        var id = -1
        val bytes = document(width = 100, height = 100, contentDescription = "Clock") {
            id = addInt(32) // 43, after content-description claimed 42
        }
        assertEquals(43, id)
        val op = DocumentReader.inflate(bytes).operations.first { it is IntegerConstant } as IntegerConstant
        assertEquals(43, op.id)
        assertEquals(32, op.value)
    }

    @Test
    fun addDataMapIds_firstCallId_equalsUpstreamStartArray_2097194() {
        // 🔴 The ONE-sample byte-anchor verification (PO W#4 + docs/TECHSPEC-E3-datamap-id-allocation).
        // Decoded from look_up1.rc: `ID_MAP id=2097194` = `(2 shl 20) + 42`.
        var mapId = -1
        val bytes = document(width = 100, height = 100) {
            val first = addText("First") // region-0, id 42
            mapId = addDataMapIds(
                listOf(dataMapEntry("First", first)),
            )
        }
        assertEquals(2097194, mapId)
        val op = DocumentReader.inflate(bytes).operations.first { it is DataMapIds } as DataMapIds
        assertEquals(2097194, op.id)
        assertEquals(1, op.entries.size)
        assertEquals("First", op.entries[0].name)
        assertEquals(DATA_MAP_TYPE_STRING, op.entries[0].type)
        assertEquals(42, op.entries[0].valueId)
    }

    @Test
    fun addDataMapIds_multipleCallsIncrement_inRegion2() {
        var first = -1; var second = -1; var third = -1
        document(width = 100, height = 100) {
            first = addDataMapIds(emptyList())
            second = addDataMapIds(emptyList())
            third = addDataMapIds(emptyList())
        }
        assertEquals(2097194, first)
        assertEquals(2097195, second)
        assertEquals(2097196, third)
    }

    @Test
    fun addDataMapIds_doesNotAdvancePlainCounter() {
        // Cross-check the IdAllocator independence at the DSL level.
        document(width = 100, height = 100, contentDescription = "Clock") {
            assertEquals(43, ids.peek())
            addDataMapIds(emptyList()) // pulls region-2 only
            assertEquals(43, ids.peek(), "plain counter must not move after addDataMapIds")
            assertEquals(2097195, ids.peekArray())
        }
    }

    @Test
    fun dataMapLookup_emitsOp_andReturnsPlainResultId() {
        var lookupId = -1
        var mapId = -1
        var keyId = -1
        val bytes = document(width = 100, height = 100, contentDescription = "Clock") {
            val first = addText("John") // 43
            mapId = addDataMapIds(listOf(dataMapEntry("First", first))) // region-2 = 2097194
            keyId = addText("First") // 44
            lookupId = dataMapLookup(mapId, keyId) // 45
        }
        assertEquals(2097194, mapId)
        assertEquals(44, keyId)
        assertEquals(45, lookupId)
        val op = DocumentReader.inflate(bytes).operations.first { it is DataMapLookup } as DataMapLookup
        assertEquals(45, op.id)
        assertEquals(2097194, op.dataMapId, "DATA_MAP_LOOKUP references the full region-tagged dataMapId")
        assertEquals(44, op.keyStringId)
    }

    @Test
    fun dataMapEntry_defaultType_isWireByteZero_string() {
        // 🔴 REM-97 byte-anchor — mirror of the REM-92 NamedVariable handcrafted oracle.
        //
        // Verified against `androidx/compose/remote/remote-core/.../operations/DataMapIds.java:44`:
        //   `public static final byte TYPE_STRING = 0;`
        // Before REM-97 the helper constant was `DATA_MAP_TYPE_STRING = 2`, so `dataMapEntry`
        // with the default emitted wire byte `2` — silently diverging from upstream for every
        // String-typed map entry. The look_up1 fixture (the only ID_MAP corpus) was replicated
        // by test-2 with literal-type entries, hiding the default-bug. This test pins both
        // (a) the constant value and (b) the round-tripped wire-byte to `0`, so any future
        // regression at either point fails locally.
        assertEquals(0, DATA_MAP_TYPE_STRING, "wire-byte constant for STRING must be 0 (upstream)")

        val entry = dataMapEntry(name = "foo", valueId = 99)
        assertEquals(0, entry.type, "dataMapEntry default type slot = STRING = 0")

        val bytes = document(width = 100, height = 100) {
            addDataMapIds(listOf(entry))
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is DataMapIds } as DataMapIds
        assertEquals(1, op.entries.size)
        assertEquals("foo", op.entries[0].name)
        assertEquals(99, op.entries[0].valueId)
        assertEquals(
            0, op.entries[0].type,
            "wire byte for STRING entry must round-trip as 0 — guards against the pre-REM-97 default of 2",
        )
    }

    @Test
    fun dataMapEntry_intAndFloatTypes_matchUpstreamWireBytes() {
        // Pins the two non-default types — upstream `DataMapIds.java:45-46`:
        //   `TYPE_INT = 1, TYPE_FLOAT = 2`.
        assertEquals(1, DATA_MAP_TYPE_INT)
        assertEquals(2, DATA_MAP_TYPE_FLOAT)

        val bytes = document(width = 100, height = 100) {
            addDataMapIds(
                listOf(
                    dataMapEntry("i", 50, DATA_MAP_TYPE_INT),
                    dataMapEntry("f", 51, DATA_MAP_TYPE_FLOAT),
                ),
            )
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is DataMapIds } as DataMapIds
        assertEquals(2, op.entries.size)
        assertEquals(1, op.entries[0].type, "INT entry wire byte = 1")
        assertEquals(2, op.entries[1].type, "FLOAT entry wire byte = 2")
    }

    @Test
    fun lookUp1PrefixSequence_matchesE5IdOrderReference() {
        // Replicates the look_up1 plain-pool-around-ID_MAP scenario from the E5 id-order reference,
        // without the ANIMATED_FLOAT (E4) ops. Verifies the SHARED interleaving rule:
        //   id 42 = "Clock" (content-desc) · 43 = "John" · 44 = "David" · 45 = DATA_INT(32) ·
        //   [ID_MAP id=2097194, 3 entries pointing at 43/44/45] · ...
        var clockId = -1; var johnId = -1; var davidId = -1; var ageId = -1; var mapId = -1
        document(width = 200, height = 200, contentDescription = "Clock") {
            // contentDescription = "Clock" auto-emitted DATA_TEXT(42) by REM-85 prolog.
            clockId = 42
            johnId = addText("John")           // 43
            davidId = addText("David")         // 44
            ageId = addInt(32)                 // 45
            mapId = addDataMapIds(             // 2097194 (region-2)
                listOf(
                    dataMapEntry("John", johnId),
                    dataMapEntry("David", davidId),
                    dataMapEntry("age", ageId, DATA_MAP_TYPE_INT),
                ),
            )
        }
        assertEquals(43, johnId)
        assertEquals(44, davidId)
        assertEquals(45, ageId)
        assertEquals(2097194, mapId, "ID_MAP id = (2 shl 20) + 42 = 2097194 (look_up1 byte-anchor)")
    }
}
