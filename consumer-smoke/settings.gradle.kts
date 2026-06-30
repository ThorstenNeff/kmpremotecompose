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
        // REM-168 Phase 1 — resolve from the local Maven cache (token-free local dev). Listed first so
        // token-less local builds keep working; GHP below is the real external-app-team path.
        mavenLocal()
        google()
        mavenCentral()
        // REM-168 Phase 2 — GitHub Packages: the real external-app-team resolution path. Consumers need a
        // `read:packages` token, read ONLY from `~/.gradle/gradle.properties` (gpr.user/gpr.token) or the
        // environment (GITHUB_ACTOR/GITHUB_TOKEN) — never hardcoded, never committed. Used when the artifact
        // isn't in mavenLocal (e.g. a fresh external machine, or `--refresh-dependencies` after clearing it).
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/ThorstenNeff/kmpremotecompose")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.token").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

rootProject.name = "consumer-smoke"
