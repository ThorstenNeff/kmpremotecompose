# REM-8 — Anfass-App-Shell — Anforderung (test-2 → dev, via PO)

> **Status:** Plan/Skelett (test-2). **Kein Player-Wiring hier** — das macht ein Dev. Dieses Dokument ist
> der **Vertrag**, gegen den die Maestro-Flows (`flows/render_smoke.yaml`, `flows/render_parity.yaml`)
> laufen. Eingefroren = der Hook-/Deep-Link-Contract; alles andere ist dev-Implementierungsfreiheit.
> **Basis (per Read verifiziert 2026-06-26):** `:androidApp` (`MainActivity` → shared `App()`),
> `:iosApp` (`ContentView`/`iOSApp.swift`), shared CMP. **Spec:** `TECHSPEC-REM-L2-player-v2.md` §3 L2-S4.
> **Akzeptanz (Mensch-Wunsch, STATUS §L2):** startbare App zeigt gerendertes Doc auf **Android + iOS**.

---

## 0. Wozu (Scope-Grenze)

Die Anfass-App ist das **Render-Vehikel** für die L2-§6-Gate: *bild-verifizierte* Render-Parität
(NICHT byte — das ist Layer 1, abgeschlossen). Sie lädt ein **gebündeltes `.rc`**, dekodiert es über den
L1-Codec, rendert es über den **L2-Player-Composable** (aus S1/S2/S3) und meldet Erfolg/Fehler über
**stabile testTag-Hooks**, die Maestro abgreift. Mehr nicht: keine UI-Chrome, keine Fixture-Auswahl-UI,
keine Navigation. Eine Route, ein Canvas, drei Hooks.

**Scharf, sobald** der Player **ein** Doc rendert (nach L2-S2). Bis dahin baut dev die Shell gegen einen
Stub-Player (zeichnet z.B. ein Rechteck) — der Hook-Contract ist davon unabhängig testbar.

---

## 1. Hook-Contract (VERBINDLICH — eingefroren, identisch zu QA_TEST_PLAN §6)

Drei `Modifier.testTag(...)` an exakt diesen Stellen. Maestro spricht sie als `id:` an.

| testTag | Erscheint WANN | Semantik | Fallstrick |
|---|---|---|---|
| `rc-canvas` | sobald die Render-Route **komponiert** ist | „App ist auf dem richtigen Screen, Container da" | — |
| `rc-rendered` | **ERST nach erstem erfolgreichem Frame-Commit** (erstes `onDraw`/`drawIntoCanvas` durch) | „dekodiert **UND** gezeichnet" — der Erfolgsbeweis | **NICHT** beim Composition-Start setzen, sonst grünt der Smoke obwohl nichts gezeichnet wurde |
| `rc-error` | bei Decode-/Render-Fehler **ODER** „rendered empty" (Decode ok, aber 0 Primitive gezeichnet), **exklusiv** zu `rc-rendered` | „Reader/Player ist am echten Doc gebrochen ODER hat nichts gezeichnet" — idealerweise mit Fehlertext im Node | exklusiv: nie beide gleichzeitig sichtbar |
| `rc-draw-count` | sobald der erste Frame committed ist | Debug-Node, dessen **sichtbarer Text die Zahl der im Frame ausgeführten `paint.*`-Primitive** ist (aus dem Frame-Draw-Counter, s.u.) | Maestro liest die Zahl → unabhängiger Count>0-Check |

**Kritische Semantik `rc-rendered` (wiederholt, weil sie das ganze Gate trägt) — AMENDMENT PO-genehmigt 2026-06-26:**
Das Tag muss an einen Zustand gebunden sein, der **erst nach dem ersten committeten Frame** `true` wird —
z.B. ein `mutableStateOf(false)`, das in einem `drawWithContent { drawContent(); if (!committed) onFirstCommit() }`
oder via `Snapshot`/`onGloballyPositioned` **nach** dem ersten Draw gesetzt wird. Composition-Start ≠ Frame-Commit.
**ZUSÄTZLICH (Amendment):** `rc-rendered` feuert **nur, wenn der Player-Walk in diesem Frame ≥1 `paint.*`-Primitiv
ausgeführt hat** (Draw-Count > 0) — **nicht** bei einem committeten **Blank**-Frame. Decode ok **aber Draw-Count==0**
→ stattdessen **`rc-error` „rendered empty"**. Das schließt das false-green-Loch, falls die Op→Paint-Binding-
Abdeckung für ein Doc (noch) unvollständig ist. Wenn dev hier unsicher ist → über PO an test-2 zurück, **nicht** raten.

**Frame-Draw-Counter (Pflicht für das Amendment, Core-Player-Seite):** der Player / `RemoteContext` führt einen
**pro-Frame-Zähler**, den **jede `paint.*`-Primitiv-Methode inkrementiert** (in `RemoteComposePlayer.paint` bei
`resetPass` auf 0 gesetzt). Nach dem Walk: Count == Zahl der tatsächlich gezeichneten Primitive. Die App liest
ihn für (a) das `rc-rendered`-vs-`rc-error`-Gating und (b) den `rc-draw-count`-Debug-Node-Text. `render_smoke.yaml`
prüft `rc-draw-count` matche `^[1-9][0-9]*$` **zusätzlich** zum Frame-Commit (Defense-in-depth, nicht nur App-Trust).

**Empfehlung (dev-Freiheit, nicht bindend):** den rendernden Knoten zusätzlich mit einer **stabilen
Pixel-Größe** layouten (feste `dp`-Box, kein `fillMaxSize`/`wrapContent`-Drift), damit Android- und
iOS-Screenshot **deckungsgleich** sind — sonst frisst Größendrift das Toleranzband (s. `render-parity-tolerance.md`).
Vorschlag: Render-Box exakt auf die Doc-Dimension (`width`/`height` aus dem `.rc`-Header, hier durchweg 500×500)
in dichte-normalisierten Pixeln. Density-Handling kommt aus L2-S1 `DensityProvider` — nicht hartkodieren.

---

## 2. Fixture-Bündelung (gebündeltes `.rc` — Pflicht; Deep-Link optional)

Zwei Wege, ein Fixture in die App zu bekommen. **Mindestanforderung = (A).** (B) ist optionaler Komfort.

**(A) Gebündeltes Default-`.rc` (Pflicht, MVP):**
- Genau **ein** einfaches Korpus-`.rc` als App-Asset bündeln. **Vorschlag: `procedure_simple1.rc`**
  (61 B, Header + DrawCircle — das kleinste Gate-Fixture, F1). Reines Geometrie-Doc → rendert mit
  S2 allein, **braucht S3-Text nicht** → frühestmöglicher scharfer Lauf.
- Android: `androidApp/src/main/assets/rc/procedure_simple1.rc`. iOS: ins App-Bundle (Copy-Bundle-Resources).
- Das Doc liegt schon im Korpus (`shared/src/commonTest/resources/rc-corpus/corpus/`) — **test-1 liefert
  die Bundle-Kopie** (Fixture-Provenance = test-1, nicht dev/test-2). Dev verdrahtet nur das Laden.
- App rendert beim Start **ohne Parameter** dieses Default-Doc → `launchApp` allein genügt für den Smoke.

**(B) Deep-Link `kmprc://render?rc=<name>` (optional, für Korpus-Matrix):**
- Erlaubt **einen Flow über den ganzen Korpus** (`--env RC=<name>`), statt pro Fixture neu zu bündeln.
- Erfordert: Android `intent-filter` (`<data android:scheme="kmprc"/>`, `BROWSABLE`) — **fehlt aktuell
  im `AndroidManifest.xml`** (verifiziert: nur `MAIN`/`LAUNCHER`); iOS `CFBundleURLSchemes=[kmprc]` —
  **fehlt aktuell in `Info.plist`** (verifiziert: nur `CADisableMinimumFrameDurationOnPhone`).
- `rc=<name>` lädt das gleichnamige gebündelte `.rc` (alle Korpus-`.rc` als Assets, oder lazy aus den
  shared-Resources). Unbekannter Name → `rc-error`.
- **MVP-Empfehlung:** (A) zuerst scharf machen (1 Doc, beide Plattformen) = der Anfass-Meilenstein.
  (B) nachziehen, wenn die Korpus-Render-Matrix (S4-Breite) drankommt. Der Smoke-Flow unterstützt
  **beide** (Default-Launch ODER Deep-Link) — s. `flows/render_smoke.yaml`.

---

## 3. Was die App NICHT tut (Anti-Scope)

- Keine Fixture-Picker-UI, kein Menü, keine Settings. Eine Render-Route.
- Kein Netzwerk, kein Datei-Picker — Fixtures sind **gebündelt** (deterministisch, offline, CI-tauglich).
- Keine `java.*` in `commonMain` (PROJECT_CONTEXT §5) — App-Shell-Logik in CMP `commonMain`; nur die
  Plattform-Einsprungpunkte (`MainActivity`, `iOSApp.swift`/`ContentView`) + Deep-Link-Registrierung
  in `androidMain`/iOS-nativ.
- Kein Verbatim-Copy aus `remote-player-view`/`remote-player-compose` (Referenz, nicht Paste).

---

## 4. Ownership (REM-8, eingefroren — an PO zur Bestätigung)

| Teil | Owner |
|---|---|
| App-Shell-Impl (Route, Asset-Laden, Deep-Link-Registrierung, Player-Composable-Wiring) | **dev** (erster freier Dev, nach S2) |
| testTag-Hook-Contract + Maestro-Flows + Screenshot-/Parity-Harness | **test-2** (dieses Doc + `flows/`) |
| iOS-Target-Verifikation (echter Sim-Run) + **Golden-Referenzbilder** pro Fixture | **test-1** |
| gebündelte Fixture-`.rc` (Provenance/Bundle-Kopie) | **test-1** |

**Akzeptanz des Anfass-Meilensteins:** `render_smoke.yaml` grün auf **Android** (lokal) **und iOS**
(Sim/Cloud) gegen das Default-Doc → die App zeigt nachweislich ein gerendertes `.rc` auf beiden Targets.
Parität (Pixel-Vergleich) ist der **nächste** Schritt (`render_parity.yaml` + Goldens von test-1), nicht
Teil des ersten „läuft"-Beweises.
