# A2 — Web-Render-Sweep-Harness (wasmJs) · PREP ahead-of-C6

> **Owner:** test-2 (QA, Web-Lane). **Status:** Spec/Skelett-Vorarbeit, **ungated** (kein wasmJs-Target nötig).
> **Roadmap:** `docs/TECHSPEC-feature-complete-backlog.md` A2 (dep C6) → C7 (volle 173, dep C6+A2). Parent REM-72.
> **Zweck:** den Web-Sweep-Harness bereit-zu-wiren halten, sodass in dem Moment, wo dev-2s **C6** (wasmJs
> compile/link + First-Render-Smoke) landet, der 173-Doc-Sweep (C7) **sofort** läuft — analog zur
> clock-golden-freeze-spec, die vor dem REM-61/62-Merge fertig war.
>
> **Prinzip:** A2 ist KEIN neuer Harness from-scratch. Es ist die **Web-Capture-Schicht** unter meinem
> **schon existierenden Verdikt-Stack** (JVM-Render-Sweep + `parity_compare.py`-Bänder + FAIL-CLOSED-Capture).
> Recyceln, nicht neu erfinden.

---

## 0. 🔴 VERIFIZIERT gegen REM-76 (`origin/feature/REM-76-wasmjs-actuals`, 2026-06-28): Web-Shell-Instrumentierung FEHLT

**REM-76 ist Render-*Infra*, NICHT der sweepbare Test-Shell.** Selbst inspiziert (git show gegen den Branch):
- **REM-76-Inhalt:** 3 wasmJs-actuals (`ImageDecode`/`Offscreen`/`OpaqueSurface.wasmJs`) + js-Target-Entfernung + `webApp/build.gradle.kts`-Tweak. **Kein** Shell-/Router-Code.
- **`webApp/main.kt` = bare `ComposeViewport { App() }`** + Default-Loading-SVG in `index.html`. **Kein** `?rc=`-Param-Lesen, **kein** Doc-Selector, **kein** DOM-Hook.
- **Die Hooks existieren — aber als Compose-`testTag`s im SHARED `RemoteComposeApp.kt`** (`rc-canvas`/`rc-rendered`/`rc-error`/rc-doc-Echo, der test-2-Contract aus REM-8). Getrieben von **`RcRouter`**.
- **`RcRouter` parst NUR mobile Deep-Links** — sein eigener Docstring: „Each platform parses its own deep-link (Android `intent.data`, iOS `onOpenURL`)". **Es gibt KEINEN wasmJs/`window.location`-Parser** → auf Web bleibt `RcRouter` auf `DEFAULT_DOC` (procedure_simple1), `?rc=<doc>` wählt NICHTS.

**→ Zwei konkrete Web-Shell-Lücken (Dev-Arbeit, NICHT in REM-76 — das Web-Analogon zu meinem mobilen `rem8-app-shell-requirement.md`):**

| # | Lücke | Warum sie A2/C7 hart blockt |
|---|---|---|
| **W1** | **wasmJs-`RcRouter`-URL-Parser:** `window.location`-Query (`?rc=`, `&t=`, `&live`) → `RcRouter.select/setStaticTime`. | Ohne ihn rendert Web nur `procedure_simple1` — **kein 173-Doc-Sweep, kein Frame-Pin** möglich. |
| **W2** | **DOM-Spiegel der Render-Marker:** Compose-`testTag`s liegen IM wasm-`<canvas>` (ComposeViewport-Semantik) → **ein DOM-Web-Driver (Maestro-web/Playwright) sieht sie NICHT** (nur das Canvas). webApp muss `rc-rendered`/`rc-error`/rc-doc als **`data-*`-Attribut auf einem bekannten DOM-Element** spiegeln (beim Frame-Commit), sonst kann der Capture nicht race-frei warten + nicht FAIL-CLOSED gegen Doc-Mismatch prüfen. |

**Konsequenz für die PO-Sequenz:** C7 ist **nicht nur** auf C5 (Browser-`.rc`-async-Loading, dev-1) gated — **auch auf W1+W2** (Web-Shell-Instrumentierung). REM-76-Merge = „Web compiliert + rendert den Default-Doc", **≠** „Web ist sweepbar". Sauber zu trennen wie der §6-„Web-Target-done"-Gate. **W1+W2 sind mein Contract (test-2-owned Anforderung), Umsetzung = dev.**

---

## 0.1 🟢 STAND 2026-06-28 (post-compact, EMPIRISCH gegen den realen wasmJs-dist verifiziert — chromium)

PO-Auftrag „pre-arm REM-80": (a) chromium-Smoke re-confirmen, (b) Harness gegen Default-Render stagen.
**Methode:** `./gradlew :webApp:wasmJsBrowserDistribution` (Build **grün**, exit 0) → `python3 -m http.server 8080`
auf dem dist → Maestro `run` device_id=`chromium` gegen die echten DOM-Marker. **Befunde (hart, non-optional asserts):**

| Gate | Vorher angenommen | **Verifizierte Realität (2026-06-28)** |
|---|---|---|
| **W2** DOM-Marker | offen (dev) | ✅ **GEMERGT (REM-83, develop `494a6d8`).** `RenderMarkerMirror.wasmJs.kt` schreibt `data-rc-canvas/-rendered/-draw-count/-doc/-error` auf das `<canvas>`. honest-render-Gate (`committed && drawCount>0`) erhalten. **chromium-`assertTrue`+page-JS liest sie** (verifiziert). → Flow jetzt darauf gated. |
| **W3** Player-Entry | (nicht erkannt) | 🔴 **NEU/BLOCKER:** `webApp/main.kt → ComposeViewport { App() }` = CMP-Template („Click me!"), **NICHT `RemoteComposeApp`** (androidApp: `setContent { RemoteComposeApp(...) }`). → **0 data-rc-Marker im DOM**, kein Doc rendert. Smoke-Screenshot = Placeholder-Button, nicht procedure_simple1. FIX (DEV): web-Entry auf RemoteComposeApp umstellen. |
| Korpus-Bytes | (angenommen verfügbar) | 🔴 **0 `.rc` im web-dist** (`composeResources` hat nur 1 drawable). Ohne C5-HTTP-fetch (oder Resource-Bundling) keine Doc-Bytes auf Web — selbst mit gefixtem W3. |
| **C5** Browser-Loading | „gemergt" (FALSCH notiert) | 🔴 **nur TechSpec gemergt** (REM-82 `fbd4dd3`); **C5+W1-IMPL ist WIP/ungemergt** (`feature/REM-82-c5-browser-loading` `63fbc34`). |
| **W1** URL-Router | offen | 🔴 weiter offen (Teil der C5-WIP-Branch). |

**→ REM-80/C7 ist NICHT armbar** (W3 + Korpus-Bytes/C5 + W1 offen; W2 erledigt). **Pre-arm geliefert:**
- `docs/flows/web_render_sweep.yaml` **auf die echten W2-`data-rc-*`-Marker umverdrahtet** (assertTrue+page-JS, FAIL-CLOSED honest-render + stale-doc-Guard + error-Guard).
- `docs/flows/web_default_smoke.yaml` (NEU) — die kleinste Liveness („rendert der Entry den Default?"), flippt grün sobald W3 + Doc-Bytes da sind, **unabhängig von W1**.
- `screenshots/parity/web_sweep_driver.sh` (NEU) — 173-Doc-Treiber, Image-Bucket zuerst, REM-62-Frame-Pin (t=36630) für die Uhren, → `parity_sweep.py <web> <mobile-baseline>` (derselbe Verdikt-Stack). Name-Listing + Bucket (14/14) gegen den realen Korpus validiert; bash-Syntax ok.

**Offen an PO/dev:** W3 (web-Entry→RemoteComposeApp) ist der neue Top-Blocker VOR C5 — ohne ihn rendert Web gar nichts. Reihenfolge-Empfehlung: **W3 → web_default_smoke grün (Liveness) → C5+W1 → voller C7-Sweep.**

---

## 0.2 🟢 ERSTER ECHTER WEB-RENDER-BEWEIS (REM-80/C7) — 2026-06-28, chromium gg. develop `c826125` (C5 gemergt)

**Verifiziert (nicht angenommen):** webApp `→ RemoteComposeApp()` ✅, 173 `.rc` in `composeResources/files/rc/` ✅,
**shasum-Spot-Check: alle 173 web-live `.rc` == conformance-oracle `.rc`** (kein stale-Doc-Risiko, assist-Watchpoint) ✅.
`web_default_smoke.yaml` + `?rc=`-Selektion (W1) auf chromium **grün** (data-rc-rendered==true, data-rc-doc==doc).

**🔧 Crop/Dims-Kalibrierung GELÖST (mein Watchpoint):** Web rendert das Doc bei **SCALE=1.0, top-left-anchored**
(Doc-Pixelmaß == Mobile-Golden-Maß). Capture = ganzer Viewport (1200×780) mit Doc oben-links + Debug-testTags
unten-links. → **Crop (0,0, golden_w, golden_h)** = Doc-nativ, schließt Debug-Text aus, KEIN Resize. Validiert:
procedure_simple1 (600×600) + simple2 (300×300) → **PASS vs Android UND iOS** (breach ≤0.01). In `web_sweep_driver.sh` gebacken.

**Sweep-Ergebnisse (Bucket-zuerst, gg. Mobile-Baseline):**
- **Geo/Text-Klasse (Stichprobe 12/138 fitting): 100% Render-Parität** — 8 PASS + 4 TEXT (Font/AA-diffus, erwartet), **0 FAIL/0 ERROR**. → die „echte-Bug"-Klasse (höhere Severity) rendert sauber auf Web.
- **Image-Doc-Bucket (14): 0 echte Web-Defekte** nach Analyse (verify-don't-trust auf die rohen Verdikte):
  - **6 PASS / 1 TEXT / 1 BLANK** (c_image, demo_bitmap_drawing_bit_draw1/2, hostile_actor1_c, impulse_demo_confetti_demo, particle, stock; hostile_actor1=both-blank) → Web-Parität.
  - **2 „FAIL" = Viewport-Clip-Artefakte** (digital_clock1 500×1500, shader_calendar 1000×2400 > 780h-Viewport → schwarz-gepaddeter Crop). KEIN Render-Defekt — Harness-Limit. **Fix-Item: höherer Capture-Viewport** (oder Scroll-Stitch) für Docs > Viewport. Im Treiber jetzt nach `_oversized/` ausgesondert statt false-FAILt.
  - **4 „FAIL" = Web ist RICHER als die Baseline** (texture_demo_basic_texture/clock/clock_test, wake_demo_wake_clock): **Web rendert die Textur** (z.B. cyan-Dot-Sphäre), **BEIDE Mobile-Goldens (Android+iOS) zeigen solid-grün** (Textur NICHT gerendert). → die §6-Bucket-Hypothese „wasm-Decode schlechter" ist INVERTIERT: Web-Image/Textur-Pfad ist der Mobile-Baseline VORAUS. **Befund an PO:** Mobile-Textur-Render ist degradiert (solid-color-Fallback); Web ist die korrektere Referenz. Golden-Refresh für Textur-Docs nötig, sonst flaggt der Sweep dauerhaft falsch.

**Fazit:** REM-76/Web-Target rendert den Korpus auf Parität (Geo/Text) bzw. besser (Textur) als Mobile. **REM-80/C7-Gate ist substanziell bewiesen** auf der Bucket+Geo/Text-Stichprobe; der volle 173-Lauf ist mechanisch via `web_sweep_driver.sh` (CLI). **Offene Harness-Items:** (1) Viewport-Höhe für ~Docs > 780h; (2) Textur-Golden-Refresh (Mobile-Baseline degradiert). **Conformance-Byte-Gate (L1) unberührt — das hier ist reiner Render-Beweis.**

---

## 1. Was A2 von C6 braucht (Dependency-Contract an dev-2, via PO zu routen)

Der Web-Sweep kann erst laufen, wenn der wasmJs-webApp diese Hooks bereitstellt. **Identisch zum
mobilen App-Shell-Contract** (`rem8-app-shell-requirement.md`) — gleiche Disziplin, Web-Mechanik:

| Hook | Anforderung | Warum (Harness-Bedarf) |
|---|---|---|
| **Doc-Selektion** | URL-Param `?rc=<docname>` lädt das benannte Korpus-`.rc` (async-fetch, C5). | Sweep iteriert die 173 Doc-Namen → je ein Page-Load. |
| **Frame-Pin** | `&t=<sek>` setzt Static-Seed=t (REM-62-Äquiv. auf Web). `&live=true` = Wall-Clock. | Uhr/Animations-Docs deterministisch (kanonischer Pin `t=36630`, s. clock-golden-freeze-spec). |
| **`rc-canvas`** | DOM-Element mit stabiler id `rc-canvas` (das Skiko-wasm-Canvas). | Screenshot-Target — der Capture clippt auf dieses Element, nicht den ganzen Viewport. |
| **`rc-rendered`** | Marker (DOM-Attribut/Element) **erst NACH erstem Frame-Commit** gesetzt, exklusiv. | **Race-frei:** Capture wartet auf `rc-rendered`, nie auf Timeout → kein Leer-Frame-Capture. |
| **`rc-error`** | Marker bei Decode/Load-Fehler, exklusiv zu `rc-rendered`. | FAIL-CLOSED: Error ≠ BLANK — wird als FAIL gezählt, nicht als legit-blank. |
| **rc-doc-Echo** | Der gerenderte Doc-Name ist aus dem DOM auslesbar (z.B. `data-rc-doc`). | **FAIL-CLOSED gegen stale Frame:** Capture verwirft, wenn `data-rc-doc != ?rc=`-Param (die adb-stale-Lektion: nie einen Frame des falschen Docs golden-en). |

> ⚠️ **Offene Contract-Fragen an dev-2 (via PO), sobald C6 in Sicht:** exakte id/Attribut-Namen
> (`rc-canvas`/`rc-rendered`/`data-rc-doc`), ob der Frame-Pin auf Web denselben `&t`-Seam wie REM-62
> nutzt, und wie die 173 `.rc` im webApp gebündelt/served werden (HTTP-fetch vs. resource-bundle, C5).

---

## 2. Capture-Mechanismus — Maestro `chromium` (recommended)

Der Maestro-MCP unterstützt **Web-Flows** (`device_id=chromium`, `openLink` + `takeScreenshot`).
→ **gleiche Toolchain wie die mobilen Goldens** (Konsistenz, ein Verdikt-Stack), kein neues Playwright-Setup.

**Skelett-Flow (`web_golden_capture.yaml`, race-frei, FAIL-CLOSED — Analog zu `golden_capture.yaml`):**
```yaml
# env: RC=<docname>, T=<sek optional>, BASE=<webapp-url>
url: ${BASE}
---
- openLink: ${BASE}/?rc=${RC}${T ? '&t=' + T : ''}
- extendedWaitUntil:
    visible: { id: "rc-rendered" }      # wartet auf First-Frame-Commit, NIE blind-timeout
    timeout: 15000
- assertTrue: ${ output.rcDoc == RC }    # FAIL-CLOSED: rendered-doc == angefragt
- takeScreenshot: web/${RC}             # clippt auf rc-canvas
```
**Fallback** falls Maestro-web im CI instabil: headless-Chromium via Playwright mit identischem
Warte-auf-`rc-rendered` + Element-Clip — gleiche Verdikt-Pipe dahinter. (Entscheidung erst mit C6-Realität.)

---

## 3. Verdikt — meinen EXISTIERENDEN Stack wiederverwenden (nichts Neues)

Die Web-PNGs gehen durch **denselben** perzeptuellen Vergleich wie die mobile Parität:
- **`screenshots/parity/parity_compare.py`** — Bänder **BLANK / PASS / TEXT / FAIL** via `breachFrac` +
  `maxClusterFrac` (Area-Struktur-Diskriminator), `TEXT_PRODUCT_OVERRIDE`. **Unverändert übernehmen.**
- **Both-blank-Detektion** + die dokumentierte **BLANK-both-Klasse (15 Docs)** inkl. der 6 WAI-inline-Uhren
  (REM-71-Provenance) — die bleiben auf Web BLANK, **kein Web-Gap**.

---

## 4. Referenz-Baseline — Web wird gegen Mobile-Klasse gedifft (nicht from-scratch)

**Kernidee:** ich habe schon eine autoritative Per-Doc-Verdikt-Baseline (Android/iOS):
**L2-Parität 98.5% (129/131), FULL/PARTIAL/BLANK pro Doc, 15 BLANK-both** (`PARITY-RECORD.md`, develop).
→ Der Web-Sweep-Verdikt je Doc wird **gegen die Mobile-Klasse desselben Docs** verglichen:

| Mobile-Klasse | Web-Erwartung | Web-Divergenz = Signal |
|---|---|---|
| FULL | FULL | Web BLANK/PARTIAL/FAIL → **echter Web-Render-Defekt** (melden) |
| TEXT-Band | TEXT-Band | solider Fehl-Cluster >5% → Web-spezifischer Struktur-Bug |
| BLANK-both (15) | BLANK | unverändert, **kein Gap** (faithful) |

So misst C7 nicht „rendert Web irgendwas", sondern **„rendert Web dasselbe wie die bewiesene Mobile-Referenz"** —
das ist die belastbare Web-Akzeptanz.

### 🔴 Web-spezifische Risiko-Hotspots (Roadmap-getrieben, vorab markiert)
- **C2 wasm-Bitmap-Decode = „spiky"** (Roadmap) → **Image/Bitmap-tragende Docs sind die #1-Divergenz-Erwartung.** (Bucket §4.1, headless verifiziert.)
- **C5 async-`.rc`-Loading** → ohne sauberes `rc-rendered`-Gating racet der Capture gegen den Fetch →
  **genau deshalb der FAIL-CLOSED-Wait** (§1, Hook W2). Kein Timeout-Capture.
- **Skiko-wasm vs. Skiko-JVM/Native Font-AA** → leichte Text-Divergenz erwartet → **TEXT-Band fängt das** (kein FAIL).

### 4.1 🎯 Image-Decode-Verdacht-Bucket (headless Decode-Survey, alle 173, 2026-06-28 — DATA_BITMAP/DRAW_*/DATA_SHADER)
PO-angefordert: „Bau die Image-Doc-Klasse als erste Verdacht-Bucket ein." Evidenz: Op-Span-Decode der 173, gebucketed nach wasm-Render-Risiko-Klasse.

- **Bucket A — encoded-image-Decode (12 Docs) = C2-spiky #1-Divergenz** (`DATA_BITMAP`, braucht wasm `Image.makeFromEncoded`):
  `c_image` (+LAYOUT_IMAGE) · `demo_bitmap_drawing_bit_draw1` · `demo_bitmap_drawing_bit_draw2` · `hostile_actor1` · `hostile_actor1_c` ·
  `impulse_demo_confetti_demo` · `particle` · `stock` · `texture_demo_basic_texture` · `texture_demo_texture_clock` · `texture_demo_texture_clock_test` · `wake_demo_wake_clock`.
- **Bucket B — Bitmap-Font-Atlas (1 Doc, SEPARATE Sub-Klasse):** `digital_clock1` — `DATA_BITMAP×64` ist der **Glyph-Atlas** (co-occurs `DATA_BITMAP_FONT` + `DRAW_BITMAP_FONT_TEXT_RUN`), KEIN Foto-Image. Font-Decode-Pfad ≠ encoded-image-Pfad → eigener Verdacht. (Relevant: digital_clock1 ist auch mein Uhr-Golden-Kandidat.)
- **Bucket C — Offscreen render-to-bitmap (2 Docs, ⊂ Bucket A, braucht `Offscreen.wasmJs` C3):** `demo_bitmap_drawing_bit_draw1` · `demo_bitmap_drawing_bit_draw2` (`DRAW_TO_BITMAP`).
- **Bucket D — Shader (1 Doc, D-Lane, separate wasm-Risiko-Klasse):** `shader_calendar` (`DATA_SHADER`).

**Sweep-Anwendung:** diese 12+1+1 Docs werden im C7-Sweep **zuerst + separat verdiktet** — eine Web-Divergenz HIER ist die erwartete C2/C3/D-Klasse (wasm-Decode/Offscreen/Shader), KEIN allgemeiner Render-Bug. Die übrigen 159 ohne Image/Shader-Op sind reine Geometrie/Text → Divergenz dort = echter Web-Render-Defekt (höhere Severity). So trennt der Bucket „erwartete Plattform-Decode-Spikes" von „echten Bugs".

---

## 5. Bereit-Status & nächste Schritte

- **JETZT (ungated, erledigt):** dieser Prep + Contract + Skelett-Flow + Verdikt-Reuse-Plan + Baseline-Diff-Logik.
- **Sobald C6 in Sicht:** Contract-Fragen (§1) an dev-2 via PO finalisieren (Hook-Namen, Frame-Pin-Seam, `.rc`-Serving).
- **Sobald C6 gemergt:** `web_golden_capture.yaml` gegen echte Hooks verdrahten → **First-Render-Smoke auf 1 Doc**
  (`procedure_simple1`) → dann **C7: voller 173-Sweep**, Verdikt gegen Mobile-Baseline, Divergenz-Liste an PO.
- **E5 (separat, dep E2–E4 dev-3):** Byte-Gleichheit erzeugte-Docs == Orakel-`.rc` — **derselbe L1-Conformance-Mechanismus**
  (`assertRcBytesEqual`, RcCorpus-Orakel) wie REM-7, nur Input = Creation-DSL-Output statt Fixture. Prep erst wenn E2–E4-API steht.

> **Bis C6/E2–E4:** echte aktive Lane bleibt Uhr-Golden-Hold (REM-61-v2/REM-62) + REM-68-Daten/Golden-Welle (Daten von test-1).
> Diese A2-Prep ist die ungated Parallel-Vorarbeit, kein Lane-Wechsel.
