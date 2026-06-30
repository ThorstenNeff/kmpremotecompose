/*
 * REM-168 consumer-smoke — the done-means-proven gate: an EXTERNAL consumer resolves the PUBLISHED
 * `com.tneff.kmpremotecompose:shared:0.1.0` artifact and renders a `.rc` through the public
 * `RemoteComposeApp(loadRc = { bytes })` entry. "publish task green" ≠ "artifact works"; this renders a
 * real document headlessly (Compose-Desktop ImageComposeScene, the same Skiko backend the player uses)
 * and asserts visible pixels — so a broken/empty artifact fails here.
 */
package com.tneff.kmpremotecompose.consumer

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import com.tneff.kmpremotecompose.RemoteComposeApp
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConsumerSmokeTest {

    private fun sampleRcBytes(): ByteArray {
        val stream = javaClass.getResourceAsStream("/sample.rc")
        assertNotNull(stream, "sample.rc must be bundled in the consumer test resources")
        return stream.use { it.readBytes() }
    }

    @Test
    fun publishedArtifact_rendersRc_throughRemoteComposeApp() {
        val rc = sampleRcBytes()
        assertTrue(rc.isNotEmpty(), "sample .rc must have bytes")

        val scene = ImageComposeScene(width = 500, height = 500, density = Density(1f)) {
            // The public entry point from the PUBLISHED library; loadRc supplies the consumer's own bytes.
            RemoteComposeApp(loadRc = { rc })
        }
        try {
            // Pump a few frames so the (suspend) loadRc + LaunchedEffect commit and the Canvas draws.
            var nonBackground = 0
            repeat(6) { frame ->
                val image = scene.render(frame * 16_000_000L) // ~16ms/frame
                val pixels = image.toComposeImageBitmap().toPixelMap()
                nonBackground = 0
                var y = 0
                while (y < pixels.height) {
                    var x = 0
                    while (x < pixels.width) {
                        val c = pixels[x, y]
                        // procedure_simple1 paints a dark circle on the light surface → any clearly
                        // non-near-white pixel proves the document actually rendered.
                        if (c.red < 0.85f || c.green < 0.85f || c.blue < 0.85f) nonBackground++
                        x += 8
                    }
                    y += 8
                }
                if (nonBackground > 0) return@repeat
            }
            assertTrue(
                nonBackground > 0,
                "the published RemoteComposeApp must render visible pixels for sample.rc (got an all-blank frame)",
            )
        } finally {
            scene.close()
        }
    }
}
