/*
 * Copyright 2026 The KmpRemoteCompose Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// REM-126 — Server-Creation (JVM-headless `.rc`-Authoring) executable.
//
// TechSpec §4: thin :server module = the executable surface. Depends only on :shared (jvm variant,
// which carries the Creation-DSL + the RcDiskWriter okio helper) — no UI/Compose-of-its-own. The
// §4.1 wiring note (shared's commonMain transitively pulls Compose/Skiko onto the server classpath)
// is byte-irrelevant for MVP — Compose is never called on the creation path.
plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

dependencies {
    implementation(projects.shared)
    implementation(libs.okio)

    // REM-169 — Ktor HTTP serving layer (JVM-only, scoped to :server; commonMain untouched → §2 safe).
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.statusPages)

    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
    // In-process Ktor test harness (byte-anchor + contract via testApplication; no real socket needed).
    testImplementation(libs.ktor.server.testHost)
}

application {
    // TechSpec §8 S2: hardcoded one-doc runner. main(args[0] = outPath).
    mainClass.set("com.tneff.kmpremotecompose.server.MainKt")
}

// REM-173 — run the localhost example .rc server (127.0.0.1 only; REM-170/public is human-gated, parked).
// Usage: ./gradlew :server:runExampleServer -PserverPort=8080  (port optional, default 8080).
tasks.register<JavaExec>("runExampleServer") {
    group = "application"
    description = "REM-173 — run the localhost example .rc server (GET /rc/{pageId}) on 127.0.0.1."
    mainClass.set("com.tneff.kmpremotecompose.server.example.ExampleServerMainKt")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOfNotNull(project.findProperty("serverPort") as String?)
}

// TechSpec §4.1 — :shared's commonMain pulls Compose/Skiko transitively onto the :server
// classpath (byte-irrelevant: Compose is never called on the creation path). One of the lifecycle
// jars resolves twice through that wiring, so the application-plugin's installDist / distZip /
// distTar tasks would otherwise fail with "Entry ... is a duplicate". EXCLUDE picks the first copy
// and is safe here: both are the same artifact at the same version. Slim-server-artifact extraction
// is a separate, larger refactor (TechSpec §4.1 — explicitly out of REM-126 scope).
// Note: installDist is a `Sync` task while distZip/distTar are `Zip`/`Tar` — all extend
// `AbstractCopyTask`, which is what carries `duplicatesStrategy`.
tasks.withType<AbstractCopyTask>().matching { it.name in setOf("installDist", "distZip", "distTar") }.configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// REM-126 S3 — CI-hook aggregator (TechSpec §8 S3). Runs the S1 disk-byte-anchor (the §0
// acceptance gate) plus the S2 CLI smoke (executable wires up + produces a non-empty file).
// PROJECT_CONTEXT §6 equivalent: the server renders nothing — no Maestro flow — so this
// aggregated byte/disk verification IS the functional proof.
tasks.register("serverCreationCiAnchor") {
    group = "verification"
    description = "REM-126 — runs the disk-byte-anchor (S1) + the :server CLI smoke (S2)."
    dependsOn(":shared:jvmTest", "test")
}
