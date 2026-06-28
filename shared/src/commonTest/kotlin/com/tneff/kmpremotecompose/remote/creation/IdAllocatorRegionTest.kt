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

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-90 (E3) — region-counter byte-contract. Pins the verified upstream schema
 * (`docs/TECHSPEC-E3-datamap-id-allocation.md` — assist-decoded against `RemoteComposeState.java` +
 * `NanMap.java`):
 *  - region-0 (plain) starts at 42
 *  - region-2 (array/data-map) starts at `(2 shl 20) + 42 = 2097194`
 *  - the two counters are independent: pulling from one does not advance the other
 */
class IdAllocatorRegionTest {

    @Test
    fun arrayCounter_startsAtUpstreamStartArray() {
        // The ONE-sample upstream verification: the first array id MUST equal NanMap.START_ARRAY =
        // (2 << 20) + 42 = 2097194. This is the byte-anchor that procedure_look_up1's
        // `ID_MAP id=2097194` is decoded with — getting this wrong byte-diverges that fixture.
        assertEquals(2097194, IdAllocator.START_ARRAY)
        assertEquals((2 shl 20) + 42, IdAllocator.START_ARRAY)

        val ids = IdAllocator()
        assertEquals(2097194, ids.peekArray())
        assertEquals(2097194, ids.nextArrayId())
        assertEquals(2097195, ids.nextArrayId(), "second array id = START_ARRAY + 1")
        assertEquals(2097196, ids.peekArray())
    }

    @Test
    fun plainCounter_unchangedByArrayAllocation() {
        // Independence: nextArrayId() must not advance the plain pool. The look_up1 sequence relies on
        // this — plain runs 42..45 + 46..48 around the ID_MAP allocation, the array counter ticks once
        // for the map and is otherwise untouched.
        val ids = IdAllocator()
        assertEquals(42, ids.nextId()) // plain 42
        assertEquals(43, ids.peek())
        val mapId = ids.nextArrayId() // pulls region-2 only
        assertEquals(2097194, mapId)
        assertEquals(43, ids.peek(), "plain counter must not advance when allocating array id")
        assertEquals(43, ids.nextId()) // plain continues at 43
        assertEquals(2097195, ids.peekArray(), "array counter advanced once, regardless of plain")
    }

    @Test
    fun arrayCounter_unchangedByPlainAllocation() {
        val ids = IdAllocator()
        assertEquals(2097194, ids.peekArray())
        ids.nextId(); ids.nextId(); ids.nextId() // pull 3 plain ids
        assertEquals(2097194, ids.peekArray(), "array counter must not advance when allocating plain ids")
    }

    @Test
    fun arrayCounter_canBeReseeded() {
        val ids = IdAllocator()
        ids.setNextArrayId(2097200)
        assertEquals(2097200, ids.nextArrayId())
        assertEquals(2097201, ids.nextArrayId())
    }

    @Test
    fun arrayCounter_acceptsExplicitConstructorStart() {
        val ids = IdAllocator(start = 100, startArray = 2097250)
        assertEquals(100, ids.nextId())
        assertEquals(2097250, ids.nextArrayId())
    }

    // Region-1 (TYPE_VARIABLE) is intentionally NOT wired here — see the IdAllocator header
    // comment. Upstream allocates NAMED_VARIABLE varIds from the plain pool (verified against
    // `color_table.rc`: NAMED_VARIABLE id=50 is region-0). The earlier region-1 counter was a
    // REM-92-review byte-blocker; the dedicated NamedVariable byte-anchor test lives in
    // ColorExpressionHelpersTest.
}
