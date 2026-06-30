import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    `maven-publish`
}

// REM-168 Phase-1 (local) publishing coordinates. The Kotlin-Multiplatform plugin + `maven-publish`
// auto-create the root `kotlinMultiplatform` publication plus a per-target publication (jvm / wasmJs /
// iosArm64 / iosSimulatorArm64); the `com.android.kotlin.multiplatform.library` plugin contributes the
// `android` variant into that set. group+version must be set before the publications are wired.
group = "com.tneff.kmpremotecompose"
version = "0.1.0"

// REM-168: the Compose-resources generated `Res` package defaults to `{group}.{module}.generated.resources`.
// Setting the Maven `group` above would otherwise shift it from the package the code imports
// (`kmpremotecompose.shared.generated.resources`) and break every `Res` reference. Pin it so the resource
// package is decoupled from the Maven coordinate group (the publish group is a coordinate, not a code package).
compose.resources {
    packageOfResClass = "kmpremotecompose.shared.generated.resources"
}

// REM-7: generate an absolute path to the commonTest resources so the conformance corpus loader
// ([RcCorpus.fixtureRoot]) resolves portably across host (jvmTest) and the iOS simulator — both run
// on the build machine. Regenerated every build, so it is machine-correct and never committed. This
// is test-harness infra only; app/runtime resource loading is separate (okio FileSystem, REM-8).
val generateCorpusRoot by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/corpusRoot")
    val resourcesPath = layout.projectDirectory.dir("src/commonTest/resources").asFile.absolutePath
    inputs.property("resourcesPath", resourcesPath)
    outputs.dir(outDir)
    doLast {
        val pkgDir = outDir.get().dir("com/tneff/kmpremotecompose/conformance").asFile
        pkgDir.mkdirs()
        pkgDir.resolve("GeneratedCorpusRoot.kt").writeText(
            buildString {
                appendLine("package com.tneff.kmpremotecompose.conformance")
                appendLine()
                append("internal const val GENERATED_CORPUS_RESOURCES_ROOT: String = \"")
                append(resourcesPath.replace("\\", "\\\\").replace("\"", "\\\""))
                appendLine("\"")
            },
        )
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }
    
    androidLibrary {
       namespace = "com.tneff.kmpremotecompose.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
        }
        commonMain.dependencies {
            implementation(libs.okio)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutinesTest) // REM-82: runTest for the suspend loader
            }
            // Generated absolute corpus-root constant (see generateCorpusRoot above).
            kotlin.srcDir(generateCorpusRoot)
        }
        // REM-128 §3 Triple-Pin: the Compose-DSL byte anchor lives next to the corpus oracle
        // (a :shared commonTest resource) — :creation-compose is its only test-scope consumer.
        // Edge points only from :shared.jvmTest → :creation-compose.main (acyclic; :creation-compose
        // doesn't depend on :shared.test).
        jvmTest.dependencies {
            implementation(projects.creationCompose)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

// REM-168 Phase-2: GitHub Packages publish target. Credentials are read ONLY from the developer's
// `~/.gradle/gradle.properties` (`gpr.user`/`gpr.token`) or the environment (`GITHUB_ACTOR`/`GITHUB_TOKEN`)
// — NEVER hardcoded and NEVER committed to the repo's gradle.properties (no secret in the repo). The
// config + the `publishAllPublicationsToGitHubPackagesRepository` task build with null creds; the REAL
// publish only succeeds once a token is supplied out-of-band. Consumers need a `read:packages` token to
// resolve from GitHub Packages (documented in the REM-168 publishing notes).
publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/ThorstenNeff/kmpremotecompose")
            credentials {
                username = (findProperty("gpr.user") as String?) ?: System.getenv("GITHUB_ACTOR")
                password = (findProperty("gpr.token") as String?) ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}