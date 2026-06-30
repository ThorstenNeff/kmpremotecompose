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
// REM-173: deliberately in the `.server.example` sub-package — NOT `.server` — so this `main` does not
// collide with the disk-runner `main` in `.server` (Main.kt). Two top-level `fun main(Array<String>)` in
// one package make a bare `main(...)` call (e.g. REM-126's MainCliSmokeTest) ambiguous and could bind to
// this *blocking* server entry → an infinite test hang. Keeping it in a sub-package keeps both unambiguous.
package com.tneff.kmpremotecompose.server.example

import com.tneff.kmpremotecompose.server.defaultExampleRegistry
import com.tneff.kmpremotecompose.server.startLocalRcServer

/**
 * REM-173 — runnable local example `.rc` server for the example app's "load from server" mode.
 *
 * **🔴 SECURITY:** binds **127.0.0.1 only** (localhost) via [startLocalRcServer] — no auth/TLS, not
 * publicly exposed (public exposure is the human-gated REM-170, parked). Serves [defaultExampleRegistry]
 * pages over `GET /rc/{pageId}`.
 *
 * Run: `./gradlew :server:runExampleServer -PserverPort=8080` (port arg optional, default 8080).
 * Stop with Ctrl-C (a shutdown hook closes the server cleanly).
 */
fun main(args: Array<String>) {
    val port = args.getOrNull(0)?.toIntOrNull() ?: DEFAULT_PORT
    val registry = defaultExampleRegistry()
    val server = startLocalRcServer(port = port, registry = registry)
    Runtime.getRuntime().addShutdownHook(Thread { server.close() })

    println("REM-173 example .rc server — http://127.0.0.1:${server.port}  (127.0.0.1 only)")
    println("pages (${registry.ids.size}): ${registry.ids.joinToString(", ")}")
    registry.ids.firstOrNull()?.let { println("try:   GET http://127.0.0.1:${server.port}/rc/$it") }
    println("(Ctrl-C to stop)")

    Thread.currentThread().join() // block until the process is interrupted
}

private const val DEFAULT_PORT = 8080
