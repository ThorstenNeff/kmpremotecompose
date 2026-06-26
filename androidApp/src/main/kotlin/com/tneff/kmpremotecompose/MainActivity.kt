package com.tneff.kmpremotecompose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // REM-8: render the bundled default fixture from app assets (path A, no params).
        setContent {
            RemoteComposeApp { assets.open("rc/procedure_simple1.rc").use { it.readBytes() } }
        }
    }
}
