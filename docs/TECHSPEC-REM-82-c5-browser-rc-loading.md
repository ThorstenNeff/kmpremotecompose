# TechSpec — REM-82 (C5) Browser-.rc-async-Loading (Epic C, Web-kritischer-Pfad)

> **Autor:** PO-Assistent · **Status:** Design (kein Code), Web-Blocker-Slice · **Datum:** 2026-06-28
> **Auslöser:** Im Browser (wasmJs) gibt es **kein okio-FileSystem** → `.rc` muss **async** geladen werden. Der geteilte `RemoteComposeApp`-Vertrag nimmt aber `loadRc: (String) -> ByteArray` **synchron**. C5 löst das, ohne den honest-render-Gate (rc-rendered/rc-doc) zu brechen. **C5 gatet REM-80 (C7 Browser-Sweep)** = der §6-Render-Beweis für die schon gemergten wasmJs-actuals (REM-76).
> **Byte-irrelevant:** App-Schicht + `loadRc`-Signatur; kein `operations/write/read` → 173/173 unberührt. Verifikation = REM-80/A2 Browser-Sweep (test-2).

---

## 0. Die Kern-Erkenntnis: `loadRc` suspend machen ist ein MINIMAL-Change

`RemoteComposeApp` ruft `loadRc(docName)` **bereits in einem `LaunchedEffect(docName)`** (Z.85) — also in einem Coroutine-Scope:
```
LaunchedEffect(docName) {
    doc = null; decodeError = null          // ← reset: während des Ladens kein stale Render
    try { doc = DocumentReader.inflate(loadRc(docName)) }
    catch (t) { decodeError = "doc '$docName': ${t.message ?: "not found"}" }  // ← Load-Fehler → rc-error
}
```
→ **`loadRc: suspend (String) -> ByteArray`** fügt sich nahtlos ein: der Aufruf liegt schon in einer Coroutine, **keine State-Machine-Umstrukturierung nötig.** Das ist der ganze architektonische Hebel.

---

## 1. Der Slice

**(1) Geteilten Vertrag ändern:** `loadRc: (String) -> ByteArray` → **`loadRc: suspend (String) -> ByteArray`** in `RemoteComposeApp`. Einziger Aufrufort (Z.93) ist bereits suspend-fähig.

**(2) Plattform-Loader anpassen (4 Entries):**
- **android/ios/desktop:** den bestehenden synchronen Read in suspend wrappen — idealerweise `withContext(Dispatchers.IO) { assets.open(...).readBytes() }` etc. (Nebeneffekt: blockierender File-Read raus vom Main-Thread.) Trivialer Adapt.
- **wasmJs:** `suspend loadRc = { name -> fetchRcBytes("rc/$name.rc") }` — Browser-Fetch, kein okio.

**(3) Resource-Bundling (symmetrisch):** Korpus-`.rc` als statische Web-Assets servieren (webApp → `/rc/$name.rc` über HTTP), symmetrisch zu android-assets / ios-Resources / desktop-resources.

**🎯 Empfehlung — `compose.components.resources` evaluieren (ist schon Dependency):** Compose-Multiplatform-Resources bietet `suspend readBytes(path)` **cross-target inkl. wasmJs** (handhabt den async-Browser-Fetch intern). Das würde EINEN unified suspend-Loader für ALLE Targets liefern statt hand-gerolltem `window.fetch` + Coroutine-Promise-Bridge + CORS — am wenigsten Custom-Code. **Trade-off:** Korpus müsste in `composeResources/` (shared) statt per-Platform-Dirs liegen (Re-Bundling, berührt auch android/ios). → dev-1 entscheidet: (A) compose-resources unified (elegant, mehr Bundling-Umbau) vs (B) per-Platform behalten + wasmJs hand-fetch (minimaler Blast, web-isoliert). **Architektur-Invariante (loadRc→suspend) gilt für beide.** Meine Lehnung: (A) prüfen zuerst — es ist der idiomatische CMP-Weg und löst wasm-async ohne Custom-JS-Interop.

---

## 1a. W1 — wasmJs URL-Query-Parser im RcRouter (Doc-AUSWAHL, die zweite Hälfte)

test-2-Befund: die gemergte Web-Infra **rendert** den Default-Doc, ist aber **nicht sweepbar** — `RcRouter` parst heute nur Android-`intent.data` / iOS-`onOpenURL`; auf Web bleibt's auf `procedure_simple1`, `?rc=` wählt nichts, `&t` pinnt nichts. C5 muss daher das Doc **lad- UND wählbar** machen.

**W1 = der Web-Input-Quell-Adapter, symmetrisch zu Mobile — KEINE neue Router-Logik:**
- Der wasmJs-Entry (webApp-`main`) liest **`window.location`**-Query (`?rc=<name>&live=1&t=<sek>`) **einmal beim Start** und treibt den GETEILTEN `RcRouter` exakt wie MainActivity.selectFromIntent / iOS onOpenURL:
  `RcRouter.resetForLaunch()` (REM-62-Disziplin: frischer Start = static t=0) → `RcRouter.select(rc)` → `RcRouter.live = (live == "1")` → `RcRouter.setStaticTime(t)`.
- **Wiederverwendet die bestehende RcRouter-API + Fail-Safes:** `select` → `UNKNOWN_DOC`-Sentinel bei ungültigem Namen (deterministisch, kein Silent-Fallback, Traversal-safe); `setStaticTime` fail-safe → 0f bei null/blank/non-num/negativ/NaN. **Nichts an RcRouter neu erfinden** — nur die Web-Query als dritte Eingabequelle.
- Query-Parse: `URLSearchParams(window.location.search)` via JS-Interop ODER ein reiner Kotlin-Query-Split. Liegt in wasmJsMain/webApp (nicht commonMain).
- Scope: einmaliges Parsen beim Page-Load reicht für den Sweep (Maestro-web lädt je Doc eine URL `?rc=<name>`). SPA-Hash-Change-Re-Nav = optional/out-of-scope.

→ Damit ist `?rc=<name>` (→ async loadRc des gewählten Docs) + `&t=<frame>` (→ deterministischer Frame-Pin) auf Web aktiv = die Voraussetzung für den 173-Sweep (REM-80). **W2 (DOM-Marker-Spiegel) ist separat REM-83/dev-2, NICHT Teil von C5.**

## 2. 🔴 Honest-render-Gate bleibt intakt (necessary≠sufficient — der Review-Kern)

Async-Loading darf rc-rendered/rc-doc/rc-error NICHT verfälschen. Quell-belegt erhalten:
- **Kein false-green während des Ladens:** der Effect setzt `doc = null` zu Beginn → die Render-Branch (`d != null && decodeError == null`) läuft erst, wenn die Bytes da + decodet sind. committed/drawCount werden NUR im Canvas-Draw-Lambda gesetzt (nach Load+Decode+Paint) → rc-rendered bleibt ehrlich (nach Frame-Commit + ≥1 Paint).
- **🔴 Fetch-Fehler MUSS werfen (fail-closed):** ein 404/Netzwerk/CORS-Fehler im wasmJs-`fetchRcBytes` muss eine Exception werfen (NICHT leere/teilweise Bytes zurückgeben) → der bestehende `catch` (Z.95) setzt `decodeError` → **rc-error** mit klarer Message. Stiller Empty-Return würde zu „rendered empty"/decode-fail degradieren (auch rc-error, aber unklarer) — also: non-2xx/Netzwerk → throw.
- **Transienter Lade-Zustand:** während des Fetches ist weder rc-rendered noch rc-error gesetzt (doc=null, decodeError=null). Das ist korrekt; **A2/Maestro-web wartet auf den terminalen Hook** (rendered ODER error) — kein false-timeout, solange der Fetch immer terminal endet (success→rendered / throw→error). Spec-Pflicht: der Fetch endet IMMER terminal (kein hängender Promise; Timeout→throw→rc-error).

---

## 3. wasmJs-Fetch-Mechanik (falls Option B / hand-gerollt)
`window.fetch(url)` → `Response.ok` prüfen (sonst throw) → `arrayBuffer()` → `Int8Array`/`ArrayBuffer` → `ByteArray`; Promise→suspend via kotlinx-coroutines-`await` (wasmJs). CORS: same-origin (Assets vom selben Web-Host) → unkritisch. Bei Option A (compose-resources) entfällt das alles.

---

## 4. Akzeptanz + Abhängigkeiten
- **Akzeptanz C5:** `:webApp` (wasmJs) lädt ein Korpus-`.rc` async → decode → L2-Player → sichtbar; die Hooks (rc-canvas/rc-rendered/rc-error) feuern korrekt; ein Load-Fehler → rc-error (kein Hang, kein false-green).
- **Gate-Kette:** C5 (REM-82) → **REM-80/C7 Browser-Render-Sweep (test-2, A2-Harness)** = der „Web-Target-done"-Beweis + der §6-Render-Beweis für REM-76s wasmJs-actuals. C5 ist NICHT selbst der Sweep — es ENTSPERRT ihn.
- **A2/REM-79-Hook-Vertrag:** identisch zu android/ios/desktop (rc-canvas-Crop, rc-rendered nach Commit, rc-doc==name) — der suspend-loadRc-Change erhält ihn (siehe §2). test-2 fährt A2/Maestro-web dagegen.
- **Sequenz:** dev-1 nimmt C5 direkt nach D1 — Spec steht jetzt, kein Design-Stall.

## 5. Harte Regeln
Byte-irrelevant (kein operations-Change → 173/173). Kein `java.*` in commonMain (der suspend-Vertrag ist common; Fetch/Resource-Read in wasmJsMain bzw. compose-resources). Hooks NICHT duplizieren (geteilter `RemoteComposeApp`). Fetch fail-closed (throw auf non-2xx/Netzwerk). Resource-Bundling symmetrisch zu den anderen Targets.
