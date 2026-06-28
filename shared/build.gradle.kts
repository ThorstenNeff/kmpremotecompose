import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}