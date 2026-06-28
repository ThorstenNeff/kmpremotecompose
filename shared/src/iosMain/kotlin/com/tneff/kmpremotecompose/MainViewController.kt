package com.tneff.kmpremotecompose

import androidx.compose.ui.window.ComposeUIViewController

// REM-8/REM-34: render the bundled fixture selected by RcRouter (default or deep-link).
// REM-82/C5: the corpus is loaded by the shared default loader via Compose-Multiplatform resources
// (composeResources/files/rc/), packaged into the iOS framework by the compose-resources plugin — so
// no NSBundle code here anymore (the shared suspend loader serves every target uniformly).
fun MainViewController() = ComposeUIViewController {
    RemoteComposeApp()
}
