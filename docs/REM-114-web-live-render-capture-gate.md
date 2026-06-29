# REM-114 / S4 — Web-Live/Animation-Render-Verifikations-Gate (Methodik-Grenze)

> **Owner:** test-2 (QA, Web-Lane). **Status:** REM-114 RESOLVED — *works in real browser, headless-capture artifact* (kein dev-Fix).
> **Scope:** legt durable fest, **was der Headless-Web-Render-Sweep verifizieren kann und was NICHT.**
> Schwester-Doc zu [REM-80-c7-wasm-render-proof.md](REM-80-c7-wasm-render-proof.md) (statischer Render-Beweis, headless gültig).

---

## 1. Verdikt (TL;DR)

Der **Headless-Web-Render-Sweep (Maestro-chromium / `Page.captureScreenshot`-Einzelshot) verifiziert nur den
ERSTEN Paint, NICHT den fortlaufenden Live-Present-Layer.** Ein live/animiertes wasm-Doc erscheint im
Headless-Capture statisch eingefroren, **obwohl es im echten Browser korrekt tickt**. Das ist ein
**Capture-/Tooling-Artefakt des Headless-Pfads**, kein CMP-wasm-Frame-Clock-Bug und kein Render-Defekt.

**→ Web-Live/Animation-Checks brauchen einen REAL-Browser (headed, Mensch). Der Headless-Screenshot-Sweep ist
dafür NICHT gültig. Statische Render-Verifikation (REM-80) bleibt headless voll gültig.**
**🔴 Empirisch korrigiert (REM-111, 2026-06-29): „CDP-rAF-Capture" ist KEIN gangbarer Web-Live-Pfad** — rohes
system-Chrome via `--remote-debugging-port`/CDP bootet den skiko-`<canvas>` gar nicht (silent, headless+headed;
nur Maestros Selenium/ChromeDriver-Launch bootet die App). Und Maestro-headless liefert nur den ersten Paint
(keine Live-Frames). Der einzige verlässliche Live-Pfad bleibt der headed Real-Browser (Mensch).

---

## 2. Beweiskette (wie wir das von einem echten Bug getrennt haben)

S4/REM-101 wurde über 5 Iterationen (iter-3→7) headless eingegrenzt; jede Render-Hypothese wurde
pixel-/empirisch widerlegt bis zur Framework-Grenze:

1. **Render-Code pixel-exoneriert** (dev-1 `b0a452b`): isolierte Skia-`readPixels`-Tests bewegen den Ball
   headless korrekt — Adapter + `paint()` + Blit alle korrekt. Der Defekt liegt **downstream des Draw-Calls.**
2. **Vier Headless-Probes**, jede schließt eine Klasse aus (durable Methode → [memory: web-wasmjs-render-sweep-gap]):
   - **(1) Liveness-Kontrolle:** bekannt-animiertes `procedure_simple_clock_fast&live=1` A↔B → Δ=2952px →
     der **Capture-Pfad IST live** → ein statisches Pixel ist REAL, kein Screenshot-Miss.
   - **(2) Direkter Canvas-Buffer-Read** (umgeht Maestro-Compositing komplett): Ball-Pixel im EIGENEN
     `<canvas>`-Backing-Store == center, während das Marker-Field (`data-rc-rcenterx`) off-center sagt.
   - **(3) Canvas-Inventory:** genau 1 `<canvas>`, Marker-Canvas === canvas[0] → kein Multi-Canvas/Blit-Mismatch.
   - **(4) Instanz-Identität:** `data-rc-dochash` STABIL über beweisbar-avancierte Frames (`f1<f3`) +
     `rcenterx` off-center → keine stale/Two-Decode-Instanz (candidate-(a) tot).
3. **Übrig blieb:** Bruch im **Present/Composite-Layer (rAF-Present-Loop)** — headless ERSCHÖPFT.
4. **🔑 Real-Browser-Check (Mensch, 2026-06-29):** `?rc=procedure_simple_clock_fast&live=1` im echten Browser →
   **der Uhrzeiger BEWEGT sich.** Disambiguierungs-Kriterium eingetreten → **Headless-Capture-Artefakt**,
   der Live-Present-Layer rendert real korrekt.

**Lektion (durable):** ein field-korrekter Marker ist *necessary-but-not-sufficient* für „was sichtbar ist";
und ein Headless-Einzelshot ist *necessary-but-not-sufficient* für „der Live-Loop läuft". Nur Pixel + Liveness +
headed Real-Browser (Mensch) entscheiden Live-Korrektheit (raw-CDP bootet die App nicht — s. §1).

---

## 3. Gate-Regel (verbindlich für künftige Web-Verifikation)

| Verifikations-Art | Headless-Sweep (Maestro-headless Einzelshot) | Headed Real-Browser (Mensch) |
|---|---|---|
| **Statischer Render** (`live` aus, `t`-pinned) — REM-80 | ✅ **gültig** (erster Paint == finaler Frame) | nicht nötig |
| **Live/Animation** (`live=1`, tickende Frame-Clock) | ❌ **NICHT gültig** (nur erster Paint, friert ein) | ✅ **erforderlich** (einziger Pfad) |
| **Live-Sensor** (devicemotion-getrieben) | ❌ nicht gültig + kein Accelerometer auf Desktop | ✅ **echtes Mobil-Gerät mit Sensor** |

*(„CDP-rAF-Capture" wurde als Live-Pfad gestrichen: raw-CDP/`--remote-debugging-port` bootet den skiko-`<canvas>` nicht — nur Maestro/ChromeDriver, und das nur für den ersten Paint. REM-111-Befund.)*

**Konkret für die Harness:**
- Animierte/Live-Docs (Clocks, Sensor-Demos, `heart_rate`, `moon_phases`, `text_refresh_bug`, `thumb_wheel*`,
  alle `&live=1`) **nicht** mit dem Headless-Screenshot-Sweep als „bewegt sich / live korrekt" claimen.
- Live-Verifikation: **headed Real-Browser-Visual-Check (Mensch, wie REM-114)** ist der einzige verlässliche Pfad.
  ~~CDP-rAF / `Page.captureScreenshot`-Loop~~ scheidet aus (raw-CDP bootet die App nicht). Falls je automatisiert
  gewünscht: ein ChromeDriver-/Maestro-getriebener Headed-Lauf mit echter rAF-Δ-Assertion müsste erst bewiesen werden.
- **Statik bleibt headless:** für REM-80-Render-Parität immer mit pinned `&t=<sec>` capturen, nie `&live=1`.

---

## 4. Ticket-Konsequenzen

- **REM-114:** RESOLVED — *works in real browser; headless-capture artifact* (kein dev-Fix). Diagnostik-Probes
  beendet (Branch `diagnostic/REM-101-s4-wasm-probe` kann geschlossen werden).
- **S4 / REM-101 Sensor-Ball:** **code-complete** — Render-Pfad pixel-exoneriert (dev-1 `b0a452b`),
  Live-Present im echten Browser bewiesen. Der „statische Ball" headless war dasselbe Capture-Artefakt +
  fehlende echte devicemotion-Events. **Real-Sensor-Mobil-Browser-Demonstration pending** (analog zum
  iOS-Sensor-Sim-Scope) — **KEIN Code-Gap.**
