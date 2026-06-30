# consumer-smoke (REM-168)

A **standalone** Gradle build that consumes KmpRemoteCompose as a **published Maven artifact** (not a
project dependency). It is the done-means-proven gate for the publishing pipeline **and** the
copy-paste integration template for external app teams.

## Run

From the **main repo root** (reuses the wrapper; this build is intentionally not in the root
`settings.gradle.kts`):

```bash
# 1. publish the libraries to your local Maven cache (Phase 1)
./gradlew :shared:publishToMavenLocal :creation-compose:publishToMavenLocal

# 2. resolve + render through the published artifact
./gradlew -p consumer-smoke jvmTest
```

The test (`ConsumerSmokeTest`) resolves `com.tneff.kmpremotecompose:shared:0.1.0` from `mavenLocal()`,
renders the bundled `sample.rc` via `RemoteComposeApp(loadRc = { bytes })` in a headless
`ImageComposeScene`, and asserts visible pixels — proving the artifact actually works, not just resolves.

## Integrating in your own app

1. Repositories — Phase 1 (local) uses `mavenLocal()`. Phase 2 (GitHub Packages) needs a
   `read:packages` token; see the commented `maven { … }` block in `settings.gradle.kts`.
2. Dependency: `implementation("com.tneff.kmpremotecompose:shared:0.1.0")` (the root KMP module resolves
   the right variant per target). Add `:creation-compose` too if you author `.rc` documents.
3. Bring your own Compose deps (`compose.runtime` / `foundation` / `ui`) — `:shared` exposes Compose as
   `implementation`, not `api`.
4. Render: `RemoteComposeApp(loadRc = { yourRcBytes })` inside any Compose surface.

## Coordinates / versions

- group `com.tneff.kmpremotecompose`, version `0.1.0` (SemVer), variants: android / iosArm64 /
  iosSimulatorArm64 / jvm / wasmJs.
