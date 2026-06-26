package com.tneff.kmpremotecompose

import android.content.Intent
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

        // REM-34: pick the doc from the launch deep-link (kmprc://render?rc=<name>); else the default.
        selectFromIntent(intent)

        // testTagsAsResourceId (Android-only) exposes Compose testTags as resource-ids so Maestro can
        // address the rc-* hooks by `id`; built here and passed into the shared composable so commonMain
        // stays platform-clean. On iOS, testTag maps to the accessibility id directly.
        setContent {
            RemoteComposeApp(
                loadRc = { name -> assets.open("rc/$name.rc").use { it.readBytes() } },
                modifier = Modifier.semantics { testTagsAsResourceId = true },
            )
        }
    }

    // Deep-link delivered while the activity is alive (e.g. Maestro openLink after launch).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        selectFromIntent(intent)
    }

    /** Apply the `rc` query param of a `kmprc://render?rc=<name>` URI; null/blank keeps the default. */
    private fun selectFromIntent(intent: Intent?) {
        RcRouter.select(intent?.data?.getQueryParameter("rc"))
    }
}
