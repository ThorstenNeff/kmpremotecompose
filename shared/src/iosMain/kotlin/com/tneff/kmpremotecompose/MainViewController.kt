package com.tneff.kmpremotecompose

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.tneff.kmpremotecompose.remote.player.core.IosSensorSource

// REM-8/REM-34: render the bundled fixture selected by RcRouter (default or deep-link).
// REM-82/C5: the corpus is loaded by the shared default loader via Compose-Multiplatform resources
// (composeResources/files/rc/), packaged into the iOS framework by the compose-resources plugin — so
// no NSBundle code here anymore (the shared suspend loader serves every target uniformly).
// REM-101/D5 S3: inject the CoreMotion-backed sensor source (App-Shell injection) so live sensor docs
// read real device sensors; remembered so it survives recomposition. The shared player only seeds in
// live mode → static/golden render is untouched (light-26 has no iOS API → static).
fun MainViewController() = ComposeUIViewController {
    val sensorSource = remember { IosSensorSource() }
    RemoteComposeApp(sensorSource = sensorSource)
}
