# TechSpec — REM-169 HTTP `.rc`-Serving (Ktor, JVM-headless)

> **Autor:** PO-Assistent (Reviewer) · **Status:** Draft v1 · **Base:** `develop = e36b447`.
> **Bindend:** `PROJECT_CONTEXT.md` (§2 Byte-Invariante, §0 Server-Creation). **Vorgänger:**
> `docs/TECHSPEC-REM-126-server-creation.md` (FINAL) — die byte-true JVM-Creation-DSL + das `:server`-Modul
> existieren schon; REM-169 setzt die **HTTP-Transport-Schicht** darüber. dev-2 baut die Impl gegen diese Spec.
> Ktor-Patterns gegen aktuelle Ktor-Doku gegroundet (embeddedServer/Netty, routing, respondBytes,
> StatusPages, ConditionalHeaders/ETag).

---

## 0. Kernaussage (TL;DR)

REM-169 fügt **genau eine** neue Fähigkeit hinzu: ein `.rc`-Dokument über HTTP ausliefern. Es **baut keine
neue Encoding-Logik** — der Endpoint ruft die schon byte-true-bewiesene `document{…}` / `captureSingleRemoteDocument{…}`
DSL und transportiert deren `ByteArray` **unverändert** im Response-Body. Damit ist **§2 byte-true
by-construction** (dieselbe Logik wie REM-126: die DSL ist das Orakel, REM-169 prüft nur die *neue* Transport-
Oberfläche). Drei Bausteine: **(1) Page-Registry** (`pageId → (params)->ByteArray`), **(2) Ktor-Endpoint**
`GET /rc/{pageId}`, **(3) Serving-Contract** (Content-Type / ETag / Fehler). **Security: Dev/Local zuerst
(localhost-gebunden); öffentliche Exposition = separater human-gated Schritt (Access-Grenze).**

---

## 1. Ist-Stand auf `develop=e36b447` (was NICHT neu gebaut wird)

- **`:server`-Modul existiert** (REM-126): `server/build.gradle.kts` = `kotlinJvm` + `application`, hängt an
  `:shared` (jvm-Variante) + `okio`. `server/.../Main.kt` = hardcodierter Disk-CLI-Runner (`buildSimple2()`
  → `RcDiskWriter.write`). REM-169 ergänzt die HTTP-Oberfläche **im selben Modul** (ersetzt den Single-Doc-
  CLI nicht — der bleibt als Disk-Pfad).
- **Byte-Produzenten (byte-true bewiesen):** `document(width, height, profile=Profile.Baseline,
  contentDescription?, content: RemoteComposeContext.()->Unit): ByteArray` (prozedural, sync) und
  `suspend fun captureSingleRemoteDocument(…): ByteArray` (Compose-DSL). `CreationByteConformanceTest` pinnt
  beide gegen die `procedure_*`-Korpus-Orakel. **REM-169 ruft sie nur auf.**
- **Ktor ist noch KEINE Dependency** (verifiziert) → REM-169 führt Ktor (server-core + Netty-engine +
  Plugins) in `:server` ein. okio 3.9.1 schon da.
- **Build-Quirk (erben):** `:server` hat `duplicatesStrategy = EXCLUDE` für installDist/distZip/distTar
  (Compose/Skiko transitiv doppelter Lifecycle-Jar via `:shared`). Gilt für ein Ktor-Fat-Jar / die
  Distribution genauso → übernehmen/erweitern (kein neuer Slim-Artifact-Refactor in REM-169).

---

## 2. §2 — die harte Regel (byte-true by-construction)

- **`commonMain` wird NICHT angefasst.** Jedes REM-169-Artefakt lebt in `:server` (+ ggf. `:shared` jvmMain,
  falls ein Helper geteilt wird). Kein Op-`write`/`read`/`equals`/`hashCode`-Change, keine DSL-Änderung →
  173/173-Korpus + die 5 `CreationByteConformanceTest`-Fixtures bleiben grün **by construction**.
  **§2-Guard pro Diff:** `git diff develop..BR -- 'shared/src/commonMain/**'` MUSS leer sein.
- **Der Body wird VERBATIM transportiert.** `call.respondBytes(bytes, contentType)` mit exakt dem `ByteArray`
  aus `document()`/`capture()` — **keine** Transformation (kein Re-Encoding, kein Trim, kein Pretty-Print).
  ETag/Caching arbeiten über die **rohen** Bytes (s. §4). Eine etwaige Transport-Kompression (`Content-Encoding:
  gzip`) ist transparent (Body nach Dekompression bit-identisch) — der **ETag wird über die unkomprimierten
  `.rc`-Bytes** gebildet, nie über den komprimierten Stream.
- **Serving-Byte-Anker (das neue Surface, das REM-169 pinnt):** ein Ktor-`testApplication`-Test holt
  `GET /rc/{knownId}` und assertet **`response.bodyAsBytes() == <Korpus-Orakel-Bytes>`** (byte-equality) —
  analog zu REM-126s Disk-Byte-Anker. Das ist der §2-Beweis der Transport-Schicht.

---

## 3. Endpoint + Page-Registry (Design)

**Page-Registry** (`:server`, neue Datei `RcPageRegistry.kt`):
```kotlin
/** A page builder: pageId + request params → the byte-true .rc bytes (suspend, da capture() suspend ist). */
fun interface RcPageBuilder { suspend fun build(params: RcPageParams): ByteArray }

/** Thin, read-only wrapper über die Query-Params (kein Ktor-Typ-Leak in die Registry-API). */
class RcPageParams(private val map: Map<String, String>) {
    operator fun get(key: String): String? = map[key]
    fun int(key: String, default: Int): Int = map[key]?.toIntOrNull() ?: default
    // … weitere typed-Accessors nach Bedarf
}

/** pageId → Builder. Registrierbar (kein hardcodiertes Einzeldoc). Unbekannte id → null (fail-closed). */
class RcPageRegistry {
    private val pages = LinkedHashMap<String, RcPageBuilder>()
    fun register(pageId: String, builder: RcPageBuilder) { pages[pageId] = builder }
    fun get(pageId: String): RcPageBuilder? = pages[pageId]
    val ids: Set<String> get() = pages.keys
}
```
- **pageId ist ein Registry-Key, KEIN Dateipfad** → **kein Path-Traversal, keine arbitrary-execution**:
  ein nicht registrierter `pageId` schlägt fehl-geschlossen mit 404 (§4). Die Registry ist eine **Allowlist**.
- Builder-Typ `suspend (RcPageParams) -> ByteArray` deckt BEIDE Produzenten: prozedural (`document{…}` sync →
  trivial im suspend-Body) und Compose (`captureSingleRemoteDocument{…}` suspend). MVP-Registrierungen können
  die `buildSimple2()`-Replik (schon in Main.kt) + 1–2 Korpus-Orakel-Pages sein (für den Byte-Anker).

**Ktor-Endpoint** (`Server.kt`):
```kotlin
fun Application.rcServingModule(registry: RcPageRegistry) {
    install(StatusPages) {
        status(HttpStatusCode.NotFound) { call, st -> call.respondText("unknown pageId", status = st) }
    }
    install(ConditionalHeaders)            // ETag → 304 (s. §4)
    routing {
        get("/rc/{pageId}") {
            val pageId = call.parameters["pageId"]!!
            val builder = registry.get(pageId) ?: return@get call.respond(HttpStatusCode.NotFound)
            val bytes = builder.build(RcPageParams(call.request.queryParameters.toMap()))
            call.respondBytes(bytes, RC_CONTENT_TYPE)   // VERBATIM body (§2)
        }
        get("/rc") { call.respondText(registry.ids.joinToString("\n")) } // optional: Index/Discovery (Dev)
    }
}
```
- **Server-Start (Dev/Local, §5):** `embeddedServer(Netty, configure = { connectors.add(EngineConnectorBuilder().apply {
  host = "127.0.0.1"; port = 8080 }) }) { rcServingModule(registry) }.start(wait = true)` — **bind 127.0.0.1,
  NICHT 0.0.0.0.**

---

## 4. Serving-Contract

- **Content-Type:** MVP **`application/octet-stream`** (universell, kein Client rät/transformiert). Optional ein
  projekt-eigener Typ `application/vnd.tneff.rc` als dokumentierte Alternative (Empfehlung: octet-stream zuerst;
  custom MIME ist ein Header-String, keine §2-Frage). Optional `Content-Disposition: inline; filename="{pageId}.rc"`.
  → Konstante `RC_CONTENT_TYPE` zentral.
- **Caching / ETag (deterministisch ⇒ stark cachebar):** Die Bytes für `(pageId, params)` sind **deterministisch
  byte-true** → ein **starker ETag = Content-Hash der rohen `.rc`-Bytes** (z.B. `EntityTagVersion(sha256(bytes))`)
  via `ConditionalHeaders`. Ein Re-Fetch mit `If-None-Match` → **304** ohne Re-Build/Re-Transfer. `Cache-Control:
  public, max-age=<konfigurierbar>` (Dev: kurz/`no-cache`+ETag; statische Pages: länger). **ETag über die
  unkomprimierten `.rc`-Bytes** (§2).
  - *Optimierung (S2, optional):* Build-Cache `(pageId, params-canonical) → bytes` — da deterministisch ist
    Memoisierung sicher; vermeidet Re-Build pro Request. Cache-Key MUSS die params kanonisch normalisieren.
- **Fehler-Handling (fail-closed):** unbekannter `pageId` → **404** (StatusPages). Builder wirft (z.B. invalider
  param) → **400** (Bad Request, Body = kurze Diagnose, **kein** Stacktrace/Internals an den Client). Niemals 5xx
  mit Implementierungs-Details leaken.

---

## 5. Security-Posture (Risk-Posture — explizit, human-gated bei Exposition)

- **Dev/Local zuerst (dieser Spec-Scope):** Server bindet **`127.0.0.1`** (nur localhost), **keine** Auth,
  **kein** TLS. Sicher, weil nicht extern erreichbar. Read-only (GET), allowlist-Registry, fail-closed 404/400.
- **🔴 Öffentliche Exposition = SEPARATER, human-gated Schritt (eigenes Ticket).** Ein öffentlich erreichbarer
  Endpoint ist eine **Access-Grenze** → das ändert die Risk-Posture und wird **vom Menschen out-of-band
  autorisiert**, nicht in REM-169 mitgeliefert. Der Exposed-Schritt braucht (mind.): **Auth** (API-Key/Token),
  **Rate-Limiting**, **TLS**, Bind `0.0.0.0` nur hinter Reverse-Proxy, Request-Size/Timeout-Limits, ein
  Build-Cache-DoS-Guard (unbeschränkte param-Kombinationen → Memo-Cache-Flooding). **Bis dahin: NICHT 0.0.0.0
  binden, NICHT exponieren.** Die Spec schreibt das als Default-Off-Leitplanke fest.
- **Input:** `pageId` = Allowlist-Key (kein FS-Pfad). params werden vom Builder validiert; ungültig → 400,
  fail-closed. Keine params fließen je in `eval`/Reflection/FS-Zugriff.

---

## 6. Slice-Zerlegung (foundation-first)

| Slice | Inhalt | §2-Risiko |
|---|---|---|
| **S1 (blockierend) — Endpoint + Registry + Byte-Anker** | Ktor (server-core + Netty + StatusPages) in `:server`; `RcPageRegistry` + `RcPageParams`; `GET /rc/{pageId}` → `respondBytes` (verbatim) + 404; localhost-bind. **Byte-Anker-Test** (`testApplication`: `bodyAsBytes()==Orakel`). MVP-Registrierungen = simple2-Replik + 1–2 Korpus-Orakel. | **byte-true by-construction** (commonMain leer); Transport-Surface gepinnt |
| **S2 — Serving-Contract** | `RC_CONTENT_TYPE` + optional Content-Disposition; `ConditionalHeaders`+Content-Hash-ETag + `Cache-Control`; 304-Revalidate-Test; 400-on-bad-param; optional deterministischer Build-Cache (kanonische param-Keys). | §2 unberührt (Header/Cache, Body verbatim) |
| **S3 — End-to-End-Gate** | Konsument holt `GET /rc/{id}` über HTTP + rendert via `RemoteComposeApp(loadRc={bytes})` (headless Skiko, wie REM-168-consumer-smoke) → sichtbare Pixel. Beweist serve→fetch→render. | — |
| **D1 (deferred, human-gated) — Exposed-Security** | Auth/Rate-Limit/TLS/0.0.0.0/Size-Limits/DoS-Guard. **Separater Schritt, Mensch autorisiert** (§5). | Access-Grenze → Mensch |

---

## 7. Verifikation (§6-Äquivalent — Server rendert nichts, also byte+HTTP+e2e)

- **Byte-Anker** (S1, der §2-Beweis): Ktor `testApplication` — `client.get("/rc/{knownId}").bodyAsBytes()` ==
  `document(){…}`-Bytes == Korpus-Orakel-Bytes. Kein realer Socket nötig.
- **Contract** (S2): 200-Header (Content-Type), `If-None-Match` → 304, unbekannte id → 404, bad param → 400.
- **End-to-End** (S3, der „done-means-proven"-Gate): HTTP-Fetch → `RemoteComposeApp`-Render → non-background-
  Pixel (genau das REM-168-consumer-smoke-Muster, jetzt mit HTTP-Quelle statt gebündelter Bytes).
- Kein Maestro (Server hat keine UI) — die aggregierte byte/HTTP/e2e-Verifikation IST der funktionale Beweis.

---

## 8. Offene Punkte (PO/Mensch)

- **Q1 (Content-Type):** `application/octet-stream` (Empfehlung, MVP) vs. custom `application/vnd.tneff.rc`. —
  *konventionell, default annehmbar.*
- **Q2 (🔴 Exposed-Security, Risk-Posture):** Die öffentliche-Exposition-Schicht (D1) ist **human-gated** —
  ein öffentlicher Endpoint ist eine Access-Grenze. **An den Menschen:** ob/wann exponiert wird + welche
  Auth/Rate-Limit-Posture. Bis dahin localhost-only. *(Nicht von dev-2 ohne Mensch-Autorisierung shippen.)*
- **Q3 (Caching-Default):** Dev `no-cache`+ETag vs. statische Pages `max-age` — *reversibel, dev-2 wählt einen
  konservativen Default, dokumentiert.*
