package com.tneff.kmpremotecompose

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.tneff.kmpremotecompose.remote.player.core.AndroidHapticActuator
import com.tneff.kmpremotecompose.remote.player.core.AndroidSensorSource

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
            // REM-101/D5 S2: inject the Android SensorManager-backed source (App-Shell injection, TechSpec
            // §5) so live sensor docs (sensor_demo_*) read real device sensors; remembered so it survives
            // recomposition. The shared player only seeds in live mode → static/golden render is untouched.
            val sensorSource = remember { AndroidSensorSource(applicationContext) }
            // REM-143 S3b / REM-151: inject the haptic actuator (App-Shell injection) so a live
            // touch-triggered impulse buzzes. REM-151 fidelity: View.performHapticFeedback (exact upstream
            // HapticFeedbackConstants map, no VIBRATE permission) → bound to the Compose root view. Keyed on
            // the view so it rebinds if the view changes. Live-only → static/golden render never buzzes.
            val view = LocalView.current
            val hapticActuator = remember(view) { AndroidHapticActuator(view) }
            // REM-144 S4 — route `rc=e6_creation_proof` to the Compose-Creation-DSL E6 §6 proof
            // loader; anything else flows through the default resource loader unchanged. Branching
            // at the screen level (rather than wrapping the loadRc) so the non-proof path retains
            // its exact default behaviour (Res.readBytes lives `internal` in :shared — can't be
            // composed from :androidApp).
            if (RcRouter.docName == E6CreationProofLoader.DOC_NAME) {
                RemoteComposeApp(
                    loadRc = { _ -> E6CreationProofLoader.buildE6ProofDocument() },
                    sensorSource = sensorSource,
                    hapticActuator = hapticActuator,
                    modifier = Modifier.semantics { testTagsAsResourceId = true },
                )
            } else {
                RemoteComposeApp(
                    sensorSource = sensorSource,
                    hapticActuator = hapticActuator,
                    modifier = Modifier.semantics { testTagsAsResourceId = true },
                )
            }
        }
    }

    // Deep-link delivered while the activity is alive (e.g. Maestro openLink after launch).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        selectFromIntent(intent)
    }

    /**
     * Apply the `rc` (doc), `live` (REM-37 E-D1 animation), `t` (REM-62 static frame pin, seconds),
     * `density` (REM-91 forced density) and `palette` (REM-135 `=baseline` capture-determinism seed)
     * query params of a `kmprc://render` URI.
     */
    private fun selectFromIntent(intent: Intent?) {
        RcRouter.select(intent?.data?.getQueryParameter("rc"))
        RcRouter.live = intent?.data?.getQueryParameter("live") == "1"
        RcRouter.setStaticTime(intent?.data?.getQueryParameter("t"))
        RcRouter.setForcedDensity(intent?.data?.getQueryParameter("density"))
        // REM-178: optional `&epoch=<sec>` override for the player's `epochSeconds` seed; absent/
        // blank/invalid → 0L → falls back to RenderTimePins.epochFor(docName) in RemoteComposeApp.
        RcRouter.setEpochSeconds(intent?.data?.getQueryParameter("epoch"))
        RcRouter.setForceBaselinePalette(intent?.data?.getQueryParameter("palette"))
    }
}
