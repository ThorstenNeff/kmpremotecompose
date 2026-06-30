/*
 * REM-168 consumer-smoke — a STANDALONE Gradle build (its own settings; intentionally NOT included in the
 * root settings.gradle.kts). It consumes KmpRemoteCompose purely as a PUBLISHED Maven artifact
 * (`com.tneff.kmpremotecompose:shared:0.1.0`), never as a project dependency — so it proves real
 * dependency resolution + that the published artifact works, and doubles as the copy-paste integration
 * template for external app teams.
 *
 * Run it (from the main repo root, reusing the wrapper, no separate wrapper needed):
 *   ./gradlew -p consumer-smoke jvmTest
 * Prereq: `./gradlew :shared:publishToMavenLocal :creation-compose:publishToMavenLocal` first (Phase 1).
 */
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        // REM-168 Phase 1 — resolve the published KmpRemoteCompose artifacts from the local Maven cache.
        mavenLocal()
        google()
        mavenCentral()
        // REM-168 Phase 2 (token-gated) — to consume from GitHub Packages instead of mavenLocal, add a
        // read:packages token (gpr.user/gpr.token in ~/.gradle/gradle.properties or GITHUB_ACTOR/
        // GITHUB_TOKEN env) and uncomment:
        // maven {
        //     url = uri("https://maven.pkg.github.com/ThorstenNeff/kmpremotecompose")
        //     credentials {
        //         username = (extra.properties["gpr.user"] as String?) ?: System.getenv("GITHUB_ACTOR")
        //         password = (extra.properties["gpr.token"] as String?) ?: System.getenv("GITHUB_TOKEN")
        //     }
        // }
    }
}

rootProject.name = "consumer-smoke"
