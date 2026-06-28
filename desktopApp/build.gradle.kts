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

    // REM-81 (Desktop Compose-UI-Test analog): in-lane Aequivalent zu Maestro fuer Compose-Desktop,
    // weil Maestro nur Android/iOS/RN/Flutter-mobile/Web-Browser unterstuetzt (kein nativer
    // Compose-Desktop, REM-81-Spec §2 Watchpoint test-3-resolved 2026-06-28). Selbe testTag-API,
    // post-frame-commit via awaitIdle(), headless JVM. Test-only (kein commonMain/Prod-Dep) → die
    // PROJECT_CONTEXT §5/§8 "kein Dep ohne iOS-Target"-Regel greift nicht (jvm-Test-Scope).
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.junit)
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