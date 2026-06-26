package com.tneff.kmpremotecompose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // REM-8: render the bundled default fixture from app assets (path A, no params).
        // testTagsAsResourceId (Android-only) exposes Compose testTags as resource-ids so Maestro can
        // address the rc-* hooks by `id`; built here and passed into the shared composable so commonMain
        // stays platform-clean. On iOS, testTag maps to the accessibility id directly.
        setContent {
            RemoteComposeApp(
                loadRc = { assets.open("rc/procedure_simple1.rc").use { it.readBytes() } },
                modifier = Modifier.semantics { testTagsAsResourceId = true },
            )
        }
    }
}
