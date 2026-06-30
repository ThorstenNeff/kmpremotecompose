/*
 * REM-168 consumer-smoke — minimal EXTERNAL KMP consumer of the published KmpRemoteCompose library.
 * Template for external app teams: depend on the published coordinate, render a `.rc` via
 * `RemoteComposeApp(loadRc = { bytes })`. Versions are spelled out (no shared version catalog) so this
 * stands alone exactly like a real downstream project would.
 */
plugins {
    kotlin("multiplatform") version "2.4.0"
    id("org.jetbrains.compose") version "1.11.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
}

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                // The published artifact (root KMP module → jvm variant via Gradle metadata). NOT a
                // project(":shared") dependency — this is the real "external consumer" resolution.
                implementation("com.tneff.kmpremotecompose:shared:0.1.0")
                // :shared exposes Compose as `implementation` (not api), so a consumer that calls the
                // @Composable RemoteComposeApp brings its own Compose deps (exactly what app teams do).
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.ui)
                // Headless desktop render backend (Skiko) for the smoke's ImageComposeScene.
                implementation(compose.desktop.currentOs)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
