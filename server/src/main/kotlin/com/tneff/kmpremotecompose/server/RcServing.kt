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

import com.tneff.kmpremotecompose.remote.creation.document
import com.tneff.kmpremotecompose.remote.creation.drawOval
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.security.MessageDigest

/** Content type for served `.rc` bytes — universal binary so no client guesses/transforms (TechSpec §4). */
val RC_CONTENT_TYPE: ContentType = ContentType("application", "octet-stream")

/**
 * The default page allowlist: the byte-true `procedure_simple2` oracle replica ([buildSimple2], the §2
 * byte-anchor) plus a param-driven oval page (exercises typed-param validation → 400 on bad input).
 */
fun defaultRcPageRegistry(): RcPageRegistry = RcPageRegistry()
    .register("simple2") { buildSimple2() }
    .register("oval") { params ->
        val size = params.int("size", 300)
        require(size in 1..4096) { "size must be in 1..4096" } // bad param → fail-closed 400
        buildOval(size)
    }

/** A param-driven byte-true page: a full-rect oval in a `size`×`size` document. */
internal fun buildOval(size: Int): ByteArray = document(width = size, height = size) {
    drawOval(left = 0f, top = 0f, right = size.toFloat(), bottom = size.toFloat())
}

/**
 * REM-169 HTTP serving module. `GET /rc/{pageId}?params` → the registered builder's byte-true bytes,
 * transported **VERBATIM** (`respondBytes`, no re-encode/trim → §2 by-construction). A strong ETag =
 * content-hash of the **raw** `.rc` bytes → `If-None-Match` revalidation returns **304** without
 * rebuild/transfer. Unknown `pageId` → **404** (allowlist, fail-closed); a builder that rejects a param
 * ([IllegalArgumentException]) → **400** with a short message and **no** stacktrace/internals leaked.
 */
fun Application.rcServingModule(registry: RcPageRegistry = defaultRcPageRegistry()) {
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respondText(cause.message ?: "bad request", status = HttpStatusCode.BadRequest)
        }
        status(HttpStatusCode.NotFound) { call, status -> call.respondText("unknown pageId", status = status) }
    }
    routing {
        // Dev discovery index (localhost-only; lists allowlist keys, never paths).
        get("/rc") { call.respondText(registry.ids.joinToString("\n")) }
        get("/rc/{pageId}") {
            val pageId = call.parameters["pageId"]!!
            val builder = registry.get(pageId) ?: return@get call.respond(HttpStatusCode.NotFound)
            val params = RcPageParams(
                call.request.queryParameters.entries().associate { it.key to it.value.first() },
            )
            val bytes = builder.build(params) // VERBATIM byte-true bytes (§2)
            val etag = "\"" + sha256Hex(bytes) + "\""
            if (call.request.header(HttpHeaders.IfNoneMatch) == etag) {
                return@get call.respond(HttpStatusCode.NotModified)
            }
            call.response.headers.append(HttpHeaders.ETag, etag)
            call.respondBytes(bytes, RC_CONTENT_TYPE)
        }
    }
}

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/**
 * A started Dev/Local server handle: the actual bound [port] (resolves an ephemeral `port = 0`) plus
 * [close] for clean shutdown. Wraps the Ktor [EmbeddedServer] so callers (e.g. the `:desktopApp` e2e
 * test) get a Ktor-type-free lifecycle handle.
 */
class LocalRcServer internal constructor(
    val port: Int,
    private val server: EmbeddedServer<*, *>,
) : AutoCloseable {
    override fun close() {
        server.stop(gracePeriodMillis = 100, timeoutMillis = 500)
    }
}

/**
 * Start the Dev/Local `.rc` server (non-blocking) and return a [LocalRcServer] handle.
 *
 * **🔴 SECURITY (hard, REM-169 §5):** binds **ONLY `127.0.0.1`** (localhost) — **no auth, no TLS**.
 * **NOT `0.0.0.0`, NOT publicly exposed.** Public exposure (auth / rate-limit / TLS / `0.0.0.0` behind a
 * reverse proxy / size+DoS limits) is the **human-gated REM-170**, deliberately NOT shipped here.
 *
 * @param port the local port; `0` binds an ephemeral port (read back via [LocalRcServer.port]).
 */
fun startLocalRcServer(
    port: Int = 8080,
    registry: RcPageRegistry = defaultRcPageRegistry(),
): LocalRcServer {
    val server = embeddedServer(Netty, port = port, host = "127.0.0.1") {
        rcServingModule(registry)
    }
    server.start(wait = false)
    val boundPort = runBlocking { server.engine.resolvedConnectors().first().port }
    return LocalRcServer(boundPort, server)
}
