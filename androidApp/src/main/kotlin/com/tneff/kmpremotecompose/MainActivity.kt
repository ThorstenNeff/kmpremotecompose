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

        // REM-62 hardening: reset transient deep-link state on a FRESH launch so a process-singleton
        // `live`/pin left by a prior capture (Maestro `clearState` may not kill the process) can't leak
        // into this launch → a fresh start is unconditionally static t=0. selectFromIntent re-applies any
        // params the launch intent actually carries.
        RcRouter.resetForLaunch()
        // REM-34: pick the doc from the launch deep-link (kmprc://render?rc=<name>); else the default.
        selectFromIntent(intent)

        // testTagsAsResourceId (Android-only) exposes Compose testTags as resource-ids so Maestro can
        // address the rc-* hooks by `id`; built here and passed into the shared composable so commonMain
        // stays platform-clean. On iOS, testTag maps to the accessibility id directly.
        setContent {
            // REM-82/C5: no platform loader — the shared default reads the corpus via Compose-Multiplatform
            // resources (composeResources/files/rc/), packaged into the APK by the compose-resources plugin.
            RemoteComposeApp(
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

    /**
     * Apply the `rc` (doc), `live` (REM-37 E-D1 animation) and `t` (REM-62 static frame pin, seconds)
     * query params of a `kmprc://render` URI.
     */
    private fun selectFromIntent(intent: Intent?) {
        RcRouter.select(intent?.data?.getQueryParameter("rc"))
        RcRouter.live = intent?.data?.getQueryParameter("live") == "1"
        RcRouter.setStaticTime(intent?.data?.getQueryParameter("t"))
        RcRouter.setForcedDensity(intent?.data?.getQueryParameter("density"))
    }
}
