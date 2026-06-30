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

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import com.tneff.kmpremotecompose.RemoteComposeApp
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REM-169 S3 — the literal serve→fetch→render end-to-end gate (the app-team path). Starts the real
 * localhost [startLocalRcServer], does a **real HTTP GET** (real Ktor client, not testApplication) for
 * `/rc/simple2`, feeds the fetched bytes into the public `RemoteComposeApp(loadRc = { … })`, renders
 * headlessly via Compose-Desktop [ImageComposeScene], and asserts visible pixels. Proves "an app fetches
 * a `.rc` over HTTP and renders it" in one flow (done-means-proven over composition — verify-don't-trust).
 * Server is bound to 127.0.0.1 only and stopped cleanly.
 */
class Rem169HttpE2eTest {

    @Test
    fun servedRcRendersViaRemoteComposeApp_overRealHttp() {
        val server = startLocalRcServer(port = 0) // 127.0.0.1 : ephemeral
        try {
            val bytes = runBlocking {
                HttpClient(CIO).use { client ->
                    client.get("http://127.0.0.1:${server.port}/rc/simple2").bodyAsBytes()
                }
            }
            assertTrue("real HTTP GET must return the .rc bytes", bytes.isNotEmpty())

            val scene = ImageComposeScene(width = 500, height = 500, density = Density(1f)) {
                RemoteComposeApp(loadRc = { bytes }) // the fetched-over-HTTP bytes
            }
            try {
                var nonBackground = 0
                repeat(6) { frame ->
                    val pixels = scene.render(frame * 16_000_000L).toComposeImageBitmap().toPixelMap()
                    nonBackground = 0
                    var y = 0
                    while (y < pixels.height) {
                        var x = 0
                        while (x < pixels.width) {
                            val c = pixels[x, y]
                            if (c.red < 0.85f || c.green < 0.85f || c.blue < 0.85f) nonBackground++
                            x += 8
                        }
                        y += 8
                    }
                    if (nonBackground > 0) return@repeat
                }
                assertTrue(
                    "a .rc fetched over real HTTP must render visible pixels via RemoteComposeApp",
                    nonBackground > 0,
                )
            } finally {
                scene.close()
            }
        } finally {
            server.close()
        }
    }
}
