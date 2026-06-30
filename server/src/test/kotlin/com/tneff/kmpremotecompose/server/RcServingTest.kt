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

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * REM-169 — the §2 + serving-contract proof for the HTTP transport surface (Ktor `testApplication`,
 * in-process, no real socket). The byte-anchor pins that the served body is the **verbatim byte-true
 * oracle** ([buildSimple2] = the `procedure_simple2` corpus oracle, byte-true-proven by REM-126's
 * `CreationByteConformanceTest`); the rest pin the contract (Content-Type, ETag→304, 404/400 fail-closed,
 * no path traversal, no internals leaked).
 */
class RcServingTest {

    @Test
    fun byteAnchor_servedBodyIsVerbatimByteTrueOracle() = testApplication {
        application { rcServingModule() }
        val resp = client.get("/rc/simple2")
        assertEquals(HttpStatusCode.OK, resp.status)
        assertEquals(RC_CONTENT_TYPE, resp.contentType()?.withoutParameters(), "Content-Type = octet-stream")
        assertContentEquals(
            buildSimple2(),
            resp.bodyAsBytes(),
            "served bytes MUST equal the byte-true producer output (verbatim transport, §2)",
        )
    }

    @Test
    fun unknownPageId_failsClosed404() = testApplication {
        application { rcServingModule() }
        assertEquals(HttpStatusCode.NotFound, client.get("/rc/does_not_exist").status)
    }

    @Test
    fun pathTraversalLookingId_isJustAnUnknownKey_404_neverAFileRead() = testApplication {
        application { rcServingModule() }
        // A traversal-looking pageId is only an unregistered allowlist key → 404; never a filesystem path.
        assertEquals(HttpStatusCode.NotFound, client.get("/rc/..%2F..%2F..%2Fetc%2Fpasswd").status)
    }

    @Test
    fun badParam_failsClosed400_withoutLeakingInternals() = testApplication {
        application { rcServingModule() }
        val resp = client.get("/rc/oval?size=0") // size out of 1..4096 → IllegalArgumentException → 400
        assertEquals(HttpStatusCode.BadRequest, resp.status)
        val body = resp.bodyAsText()
        assertFalse(body.contains("Exception", ignoreCase = true), "no exception class leaked")
        assertFalse(body.contains("\tat "), "no stacktrace leaked")
    }

    @Test
    fun validParam_rendersByteTrueDoc() = testApplication {
        application { rcServingModule() }
        val resp = client.get("/rc/oval?size=200")
        assertEquals(HttpStatusCode.OK, resp.status)
        assertContentEquals(buildOval(200), resp.bodyAsBytes(), "param-driven page is verbatim byte-true")
    }

    @Test
    fun etag_ifNoneMatch_revalidatesTo304() = testApplication {
        application { rcServingModule() }
        val first = client.get("/rc/simple2")
        val etag = first.headers[HttpHeaders.ETag]
        assertNotNull(etag, "a strong ETag must be served for the deterministic bytes")
        val second = client.get("/rc/simple2") { header(HttpHeaders.IfNoneMatch, etag) }
        assertEquals(HttpStatusCode.NotModified, second.status, "If-None-Match must revalidate to 304")
    }

    @Test
    fun discoveryIndex_listsAllowlistKeys() = testApplication {
        application { rcServingModule() }
        val body = client.get("/rc").bodyAsText()
        assertTrue(body.contains("simple2"), "index lists the registered allowlist keys")
    }
}
