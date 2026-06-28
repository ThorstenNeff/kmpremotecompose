# REM-80 / C7 — WASM (wasmJs) Render-Proof Record

> **Owner:** test-2 (QA, Web-Lane). **Status:** REM-80/C7 substanziell bewiesen — durable Evidenz-Record.
> **Scope:** der §6-Render-Beweis für das wasmJs-Target (REM-76), Pixel-Parität Web↔Mobile über den
> 173-Doc-Korpus. Dies ist die **Beleg-Grundlage für die WASM-Feature-Complete-Claim** (Roadmap REM-72)
> + für den Menschen. **L1-Byte-Conformance (173/173) ist orthogonal + unberührt — dies ist reiner Render-Beweis.**

---

## 1. Verdikt (TL;DR)

**WASM rendert den Korpus auf Parität mit — bzw. besser als (Textur) — Mobile.** Über 150 viewport-fittende
Docs gg die Android-Baseline (matched-code-state, post REM-105 + REM-112 Golden-Refreshes):

| Bucket | n | Bedeutung |
|---|---|---|
| **PASS** | 103 | Pixel-Parität (clean) |
| **TEXT** | 31 | diffus (Font/AA/thin-line) — akzeptierte Vergleichs-Toleranz, wie der Mobile-Sweep |
| **BLANK-both** | 11 | Web==Mobile (beide blank, kein Render-Beweis, kein Regress) |
| **FAIL** | 5 | **alle = Diskriminator-Artefakt** (thin-line-Position; Web visuell == Golden) — KEIN Web-Defekt |
| **ERROR** | 0 | — |

**Render-Parität = (PASS+TEXT)/(comparable − BLANK) = 134/139 = 96.4 %.**
**Echte Web-Render-Defekte = 0 strukturell + 1 isolierter Element-Gap** (heart_rate ❤ = REM-110, narrowed).

---

## 2. Verifikationskette (verify-not-assume)

Alles empirisch gg den realen wasmJs-dist + echtes chromium, nicht inspiziert:

1. **webApp-Entry rendert den Player:** `webApp/main.kt → ComposeViewport { RemoteComposeApp(...) }` (W3, via C5/REM-82).
2. **Korpus im dist:** 173 `.rc` in `composeResources/files/rc/`, HTTP-fetchbar (C5).
3. **🔑 Korpus == Conformance-Oracle:** **shasum-Vergleich aller 173 web-live `.rc` == `rc-corpus`-Oracle** (kein stale-Doc-Risiko — same-source-state-Orakel, assist-Watchpoint).
4. **W1 (`?rc=`):** Doc-Selektion via `window.location`-Query, chromium-verifiziert (`data-rc-doc==doc`).
5. **W2 (DOM-Marker, REM-83):** `data-rc-canvas/-rendered/-draw-count/-doc/-error` auf dem `<canvas>`; honest-render-Gate (`committed && drawCount>0`). chromium-`assertTrue`+page-JS liest sie.
6. **Capture/Verdikt-Stack:** Maestro-`chromium`-Capture → Crop → `screenshots/parity/parity_sweep.py` (PASS/TEXT/BLANK/FAIL) gg die bewiesene Mobile-Baseline. Derselbe Verdikt-Stack wie mobil (nicht neu erfunden).

**Crop-Kalibrierung (test-2-Befund):** Web rendert das Doc bei **SCALE=1.0, top-left-anchored** → Crop
`(0,0, golden_w, golden_h)` = doc-nativ, kein Resize. Validiert simple1(600²)+simple2(300²) → PASS vs Android+iOS.

**Same-code-state-Disziplin (REM-77-Lektion):** bei jedem Golden-Refresh (REM-105/112) wurde **dist
mit-rebuilt** (Web-Render UND Golden vom selben Commit) — sonst Cross-State-Falsch-FAILs.

---

## 3. Alle FAIL-Kategorien aufgelöst (verify-don't-trust, jedes Bild angesehen)

Der rohe Sweep zeigte zunächst 13 FAIL; jeder wurde charakterisiert — **0 blieben unerklärt:**

| Kategorie | Docs | Auflösung |
|---|---|---|
| **Stale Textur-Goldens** | 4 (texture_demo_basic_texture/clock/clock_test, wake_demo_wake_clock) | Mobile-Goldens waren prä-REM-98 (solid-grün/Textur-nicht-gerendert); Web rendert die Textur korrekt. **REM-105** refreshte sie → FAIL→PASS/TEXT. |
| **Stale animierte/density-Goldens** | 4 (moon_phases, thumb_wheel2, text_refresh_bug, heart_rate_timeline) | Goldens prä-REM-89/93-Density-Fix. **REM-112** refreshte sie; re-pin static `&t=0&density=1.0` → moon_phases/thumb_wheel2 PASS, text_refresh_bug/heart_rate TEXT. |
| **Diskriminator-Artefakt** | 5 (demo_graphs0, graph_graph2, moon_phase_dial, pressure_gauge, stock_sparkline) | thin-line/Text-Position: ein 1-2px-Versatz lässt 100 % der dünnen Linien-Pixel breachen, obwohl visuell identisch. **Gleiche Heatmap-Limitation wie der Mobile-Sweep** (betrifft beide Targets gleich) → KEIN Web-Defekt, akzeptiert. |
| **Viewport-Clip (Tooling)** | 3 (digital_clock1 1500h, shader_calendar 2400h, haptic_demo 1204h) | Doc > 1200×780-chromium-Viewport → Capture-Clip-Artefakt. **Render-bestätigt** (Liveness: data-rc-rendered + draw-count>0; shader_calendar zeigt sichtbar Shader+Kalender). Voll-Pixel-Capture braucht höheren Viewport → **REM-111** (Tooling, low-prio, blockt den Beweis NICHT). |
| **🔴 Echter Web-Render-Gap** | 1 (heart_rate_timeline) | **REM-110** (narrowed): das rote Herz ❤ (gezeichnete Form, KEIN Emoji — 0× U+2764 im Doc) rendert an **keinem** Frame auf wasm; rote Linie Y-Position unten statt oben. Verdikt ist TEXT (kleine Fläche) — **das TEXT-Verdikt maskierte den Gap**, erst der Bild-Vergleich zeigte ihn. test-3-Desktop-Triangulation (wasm-Skiko vs Skiko-Family). |

**Bonus-Befund:** Web-Textur/Image-Pfad ist der (alten) Mobile-Baseline VORAUS — Web rendert Texturen, die
Android+iOS zur Golden-Zeit als solid-color zeigten (→ REM-105 Golden-Refresh).

---

## 4. Was NICHT bewiesen ist (ehrliche Grenzen)

- **Live-Interaktivität / Animation:** der wasm-Live-Loop advanced nicht im headless-chromium — zeit-getriebenes
  `clock&live=1` eingefroren, Sensor-Wert gecached aber kein Re-Render (REM-101-S4-Befund). rAF feuert, aber
  `withFrameNanos→recompose` tickt nicht. **→ REM-114** (dev-1, Web-Loop-Owner). Honest-Caveat: headless-chromium
  × Compose-wasm-Frame-Clock-Interaktion nicht voll ausschließbar → Real-Browser-Gegencheck (Human/Display).
  **Der Render-Beweis hier ist STATIC** (&t-Pins); LIVE war nie exercised, ist auf REM-114 geblockt.
- **3 oversize Docs:** render-bestätigt, aber nicht voll-pixel-verglichen (REM-111).
- **DeviceMotion-Sensor-Pfad:** WebSensorSource (dev-1, REM-101-S4) ist auf der Browser→Cache-Hälfte verifiziert;
  Cache→Render blockt auf demselben REM-114-Live-Loop.

---

## 5. Offene Tickets (Stand 2026-06-28)

- **REM-110** — heart_rate ❤-Element rendert nicht auf wasm (narrowed: Herz-Form + rote-Linie-Y). test-3-Triangulation.
- **REM-111** — höherer Capture-Viewport / Scroll-Stitch für 3 Docs > 780h (Tooling, low-prio).
- **REM-114** — wasm-Live-Loop tickt nicht (Zeit + Sensor); dev-1. Blockt Live-Interaktivitäts-Beweis.

## 6. Reproduktion
```
./gradlew :webApp:wasmJsBrowserDistribution
(cd webApp/build/dist/wasmJs/productionExecutable && python3 -m http.server 8080)
# pro Doc: maestro test --device chromium --env RC=<doc> docs/flows/web_render_sweep.yaml
#          (Uhren: --env T=36630 ; static-pin: ?rc=<doc>&t=0&density=1.0)
# Crop auf golden-dims + parity_sweep.py screenshots/web screenshots/reference/android
# Treiber: screenshots/parity/web_sweep_driver.sh  (Image-Bucket zuerst, oversize→_oversized)
```
Flows: `docs/flows/web_render_sweep.yaml` + `web_default_smoke.yaml`. Harness-Detail: `docs/a2-web-render-sweep-harness-prep.md` (§0.1–0.3).
