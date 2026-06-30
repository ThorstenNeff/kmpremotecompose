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

/**
 * REM-169 — a page builder: `pageId` + request params → the **byte-true** `.rc` bytes. `suspend` because
 * the Compose-DSL producer (`captureSingleRemoteDocument`) is suspend; the procedural `document{…}`
 * producer is sync (trivially callable from a suspend body). REM-169 only *calls* the already byte-true
 * producers — it adds no encoding logic (§2 by-construction).
 */
fun interface RcPageBuilder {
    suspend fun build(params: RcPageParams): ByteArray
}

/**
 * Read-only, typed wrapper over the request query params — keeps Ktor types out of the registry/builder
 * API (so builders stay transport-agnostic). A builder validates its own params and throws
 * [IllegalArgumentException] on bad input → the serving layer maps that to a fail-closed 400.
 */
class RcPageParams(private val map: Map<String, String>) {
    operator fun get(key: String): String? = map[key]
    fun int(key: String, default: Int): Int = map[key]?.toIntOrNull() ?: default
    val keys: Set<String> get() = map.keys
}

/**
 * REM-169 — `pageId → RcPageBuilder` **allowlist**. A non-registered `pageId` resolves to `null`
 * (fail-closed 404 in the serving layer). **`pageId` is a registry KEY, never a filesystem path** → no
 * path traversal, no arbitrary file read / execution: a traversal-looking id is simply an unknown key.
 */
class RcPageRegistry {
    private val pages = LinkedHashMap<String, RcPageBuilder>()

    fun register(pageId: String, builder: RcPageBuilder): RcPageRegistry {
        pages[pageId] = builder
        return this
    }

    fun get(pageId: String): RcPageBuilder? = pages[pageId]

    val ids: Set<String> get() = pages.keys
}
