# Feature-Complete Roadmap — Epics & Stories (Mensch-Scope FINAL 2026-06-28)

> **Autor:** PO-Assistent · **Status:** Backlog-Aufschlüsselung für PO→Jira · **Basis:** `./TECHSPEC-feature-completeness-audit.md`
> **Scope (Mensch, final):** (a) volle prozedurale Creation-DSL · (b) volle Live-Interaktivität · (c) **wasmJs-only (kein JS)**. = Maximal-Scope.
> **Schätz-Kalibrierung:** beobachtete ~17 Tickets/Tag kamen aus Render-Politur auf fertiger Basis. Bring-up/Greenfield ist langsamer → Story-Tage konservativ, Spanne wo unsicher. **Tage = aktive Agent-Tage bei gehaltener Intensität, parallel über Lanes.**
> **Lanes:** dev-2 = Render-actuals/Shader/GfxLayer (shared) · dev-1 = Creation-DSL/Text/Sensoren · test-1 = Desktop+Conformance-Sweep · test-2 = Web-Sweep + Creation-Byte-Conformance.

Parent-Epic-Vorschlag: **FC — Feature-Complete (Desktop+WASM-Render + Server-Creation + Live)**.

---

## Epic A — Cross-Target Test-Infra (Enabler, früh)
*Conformance (173 Decode) ist bereits cross-platform; neu = Render-Sweep-Harnesses, der „dispatch≠render"-Schiedsrichter.*

| Story | Inhalt | Tage | Dep | Lane |
|---|---|---|---|---|
| A1 | Desktop-Render-Sweep-Harness: 173 Korpus durch jvm/Compose-Desktop-Player, Capture + Orakel-Vergleich | 1.0 | — (Player da) | test-1 |
| A2 | Web-Render-Sweep-Harness (wasmJs, headless-Browser/Screenshot-Capture) | 1.0–1.5 | C6 | test-2 |

**Epic A ≈ 2–2.5 Tage** (A2 läuft nach erstem Web-Render).

---

## Epic B — Desktop-Render (jvm) · *iOS-Skiko-actuals = 1:1-Vorlage*

| Story | Inhalt | Tage | Dep | Lane |
|---|---|---|---|---|
| B1 | Desktop Verify-Sweep-Baseline: A1 fahren, Docs klassifizieren by-construction-OK vs braucht-Upgrade (der Schiedsrichter) | 0.5 | A1 | test-1/dev-2 |
| B2 | `ImageDecode.jvm` real (Skia `Image.makeFromEncoded`, iOS-Vorlage) statt `=null` | 0.5 | B1 | dev-2 |
| B3 | `Offscreen.jvm` real (Skia-Surface + `makeImageSnapshot`-Flush, iOS REM-60) statt bare Canvas | 0.5 | B1 | dev-2 |
| B4 | `OpaqueSurface.jvm` real (Skia-Raster-Surface, iOS REM-56) statt passthrough | 0.5 | B1 | dev-2 |
| B5 | Desktop Re-Sweep + Stragglers (Font/Density-Desktop-Wiring) → volle 173 grün | 0.5–1.0 | B2–B4 | test-1 |

**Epic B ≈ 2.5–3 Tage.** Shapes/Text/Pfade/Farbe rendern wohl by-construction; B2–B4 adressieren Bitmap- + offscreen/opaque-sensitive Docs (bit_draw2/cube3d).

---

## Epic C — Web-Render (wasmJs) · *greenfield-Skiko + Browser*

| Story | Inhalt | Tage | Dep | Lane |
|---|---|---|---|---|
| C1 | **js-Target aus Build entfernen** (settings/shared/webApp), wasmJs-only | 0.2 | — | dev-2 |
| C2 | `ImageDecode.wasmJs` (Skiko-wasm `Image.makeFromEncoded`) — wasm-Bitmap-Decode = spiky | 0.5–1.0 | B2 (Muster) | dev-2 |
| C3 | `Offscreen.wasmJs` (Skiko-wasm Surface+Flush) | 0.5–1.0 | B3 | dev-2 |
| C4 | `OpaqueSurface.wasmJs` (Skiko-wasm Raster) | 0.5 | B4 | dev-2 |
| C5 | webApp Browser-`.rc`-async-Loading (HTTP-fetch statt okio-FileSystem; Resource-Bundling) | 1.0 | — | dev-1 |
| C6 | wasmJs compile/link aktivieren + First-Render-Smoke | 0.5 | C2–C5 | dev-2 |
| C7 | Web-Render-Sweep volle 173 (A2-Harness) | 1.0 | C6, A2 | test-2 |

**Epic C ≈ 4–5 Tage.** Größtes Plattform-spezifisches Risiko (wasm-Decode, async-Loading).

---

## Epic D — Deferred-Render-Features · *commonMain/Skiko, target-übergreifend; Komplex-Text zuerst (meiste Docs)*

| Story | Inhalt | Tage | Dep | Lane |
|---|---|---|---|---|
| D1 | **Komplex-Text-Layout** (Hyphenation/Justification/LineBreak/BiDi via Skiko `Paragraph`) — meiste Docs | 1.5–2.0 | — | dev-1 |
| D2 | DATA_SHADER **AGSL→SkSL**-Übersetzung (~2 Docs: AiAgent/TimeSphere) — spiky | 1.5 | — | dev-2 |
| D3 | **Variable-Fonts** (FONT_AXIS via Skiko-Interop) | 1.0 | — | dev-1 |
| D4 | **GraphicsLayer-advanced** (cameraDistance/3D/Shadow/Blur-tileMode) | 1.0 | — | dev-2 |
| D5 | Sensor/Touch-Docs **Render-Basis** (statischer Frame, sodass sie zeichnen; Interaktivität = Epic F) | 0.5 | — | dev-1 |

**Epic D ≈ 5.5–6.5 Story-Tage**, split dev-1/dev-2 → **~3 Tage Wall**. Verifikation = visueller Golden (dispatch≠render).

---

## Epic E — Creation-DSL (`remote-creation`) · *unabhängige Lane, Writer-Basis byte-bewiesen da*

| Story | Inhalt | Tage | Dep | Lane |
|---|---|---|---|---|
| E1 | Prozedural-Core-Scaffold (`RemoteComposeContext`/Writer-Wrapper + Doc-Lifecycle/Header) | 1.0 | L1-Writer (da) | dev-1 |
| E2 | Draw-Prozedur-API (rect/circle/oval/line/arc/path + paint/color/stroke) | 1.5 | E1 | dev-1 |
| E3 | Text + Bitmap + Matrix/Clip-Prozedur-API | 1.0 | E1 | dev-1 |
| E4 | High-Level (clock/layout/state/variables/expressions-Creation) | 1.5 | E2,E3 | dev-1 |
| E5 | Byte-Gleichheits-Conformance: erzeugte Docs == Orakel-`.rc` (Round-trip) | 1.0 | E2–E4 | test-2 |
| E6 | **🔴 Klären:** `remote-creation-compose` (Compose-DSL für Creation) — Teil dieses Epics oder eigene Phase? | +2.0 falls drin | E4 | dev-1 |

**Epic E ≈ 6 Tage** (+2 falls E6/Compose-DSL dazu). **PO-Frage an Mensch: gehört die Compose-Creation-DSL zum „voll-prozedural"-Scope oder reicht die prozedurale API?**

---

## Epic F — Live-Interaktivität · *nach Render-Targets; capability-gestaffelt pro Target*

> **🔴 Nuance in der Story-Struktur:** Sensor/Input-APIs sind target-abhängig. **Mobile (And/iOS) voll; Desktop/Web begrenzt** (Web DeviceOrientation/Pointer wo verfügbar, sonst Render-Frame-Fallback). Jede Story trägt die Capability-Staffel explizit.

| Story | Inhalt | Tage | Dep | Lane |
|---|---|---|---|---|
| F1 | **Touch-Input** expect/actual + Event-Routing (TOUCH-Ops→Player). Mobile voll · Desktop/Web Pointer | 1.5 | B5,C6 | dev-2 |
| F2 | **Sensoren** expect/actual (Kompass/Gyro/Licht). Mobile voll · Desktop n/a · Web DeviceOrientation-wo-da, sonst Fallback | 1.5–2.0 | F1-Muster | dev-1 |
| F3 | **Haptik** expect/actual. Mobile · Desktop/Web no-op | 0.5 | F1 | dev-2 |
| F4 | Integration: Live-Loop (REM-37 da) × Input/Sensor + per-Target-Capability-Gating + Sweep | 1.0 | F1–F3 | test-1 |

**Epic F ≈ 4.5–5 Tage.**

---

## Sequenz (Default = Audit §9) & Wall-Clock

**Parallel-Lanes:**
- **dev-2 (Render-Infra):** A1-enable → B2/B3/B4 → C1–C4/C6 → D2/D4 → F1/F3.
- **dev-1 (Creation + Text + Sensoren):** E1→E2→E3→E4 (durchgehende eigene Achse) ‖ D1/D3/D5 ‖ C5 → F2.
- **test-1:** A1 → B1/B5 → F4. **test-2:** A2 → C7 → E5.

**Reihenfolge-Logik:** Epic B zuerst (billigster echter neuer Target, iOS-Vorlage; liefert das Skiko-actual-Muster für C). Dann C (wasm erbt B-Muster). D + E laufen als unabhängige Lanes parallel von Beginn (kein Render-actual-Dep). F zuletzt (braucht B+C-Render). A zieht mit B (A1) und C (A2).

**Wall-Clock-Schätzung (Story-Tage / Parallelität):** Summe ~27–31 Story-Tage über ~3–4 effektive Lanes →
- **untere Spanne ~5 aktive Tage**, **obere ~7–8** (Max-Scope: volle DSL + volle Interaktivität gewählt → obere Hälfte realistisch).
- Deckt sich mit Audit §8 (~3–7 Tage), jetzt am oberen Rand wegen Max-Scope.

**Dominante Unsicherheiten:** C2 (wasm-Bitmap-Decode), D1/D2 (Komplex-Text-Parität + Shader-Übersetzung = neue Risiko-Klassen), E4/E6 (DSL-Tiefe + Compose-DSL-Frage). Alles als Spanne, keine Punkt-Präzision.

---

## Offene Mensch/PO-Entscheidungen (vor/während Anlegen)
1. **E6:** Compose-Creation-DSL (`remote-creation-compose`) in Scope oder prozedurale API genügt? (+2 Tage falls drin.)
2. **F2/F3 Capability-Floor:** ist „Render-Frame-Fallback wo Sensor/Haptik fehlt" akzeptabel (Desktop/Web), oder muss jede Plattform jede Sensor-Capability haben? (Letzteres = nicht erfüllbar auf Desktop → empfehle Fallback-Staffel.)
