import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.tneff.kmpremotecompose.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.tneff.kmpremotecompose"
            packageVersion = "1.0.0"
        }
    }
}

// REM-78 (Epic A/B Desktop Render Sweep Harness): headless render of the 173-doc corpus through
// the Desktop/jvm player into PNGs under screenshots/reference/desktop/ + a classification CSV.
// Pixel-proof gate for REM-75 (the 3 jvm render-actuals) — runs against current develop (stubs) to
// baseline BLANK on bitmap/offscreen-sensitive docs, then re-runs after REM-75 merge to prove FULL.
tasks.register<JavaExec>("desktopRenderSweep") {
    group = "verification"
    description = "REM-78 — render the .rc corpus headlessly via Compose-Desktop and capture PNGs."
    mainClass.set("com.tneff.kmpremotecompose.sweep.DesktopRenderSweepKt")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.projectDir
    // Forwarded args so the caller can pick subsets: --priority, --docs a,b, --t 0, --out DIR, ...
    args = (project.findProperty("sweepArgs") as? String).orEmpty()
        .split(' ').filter { it.isNotBlank() }
}