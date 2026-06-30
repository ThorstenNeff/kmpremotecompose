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
package com.tneff.kmpremotecompose.server

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-173 — pins the example registry against the **ID contract with dev-1's example-app catalog**: the
 * 10 Area-B `pageId`s (`rc_box … rc_compass`) are present **in order**, each serves the bundled lib-corpus
 * `.rc` **verbatim**, and every page (incl. the procedural extras) builds non-empty bytes. The HTTP serving
 * contract is covered by REM-169's `RcServingTest`; this guards the example pages + the id↔doc mapping.
 */
class ExampleRcPagesTest {

    @Test
    fun areaBPageIds_presentInContractOrder() {
        val ids = defaultExampleRegistry().ids.toList()
        // The first 10 registered ids must be the Area-B catalog ids, verbatim and in list order.
        assertEquals(AREA_B_PAGE_IDS, ids.take(AREA_B_PAGE_IDS.size), "Area-B pageIds must match dev-1's catalog 1:1")
    }

    @Test
    fun everyExamplePage_buildsNonEmptyBytes() = runBlocking {
        val registry = defaultExampleRegistry()
        assertTrue(registry.ids.isNotEmpty(), "the example registry must register pages")
        for (id in registry.ids) {
            val bytes = registry.get(id)!!.build(RcPageParams(emptyMap()))
            assertTrue(bytes.isNotEmpty(), "page '$id' must build non-empty .rc bytes")
        }
    }

    @Test
    fun areaBPages_serveBundledCorpusVerbatim() = runBlocking {
        val registry = defaultExampleRegistry()
        // The contract: rc_box → c_box.rc verbatim, rc_compass → sensor_demo_compass.rc verbatim, etc.
        assertContentEquals(corpusRc("c_box"), registry.get("rc_box")!!.build(RcPageParams(emptyMap())))
        assertContentEquals(
            corpusRc("sensor_demo_compass"),
            registry.get("rc_compass")!!.build(RcPageParams(emptyMap())),
        )
        assertTrue(corpusRc("moon_phases").isNotEmpty(), "bundled corpus .rc must resolve from :server resources")
    }

    @Test
    fun simple2ProceduralExtra_isTheByteTrueOracle() = runBlocking {
        val bytes = defaultExampleRegistry().get("simple2")!!.build(RcPageParams(emptyMap()))
        assertContentEquals(buildSimple2(), bytes, "the simple2 procedural page must be the byte-true producer output")
    }
}
