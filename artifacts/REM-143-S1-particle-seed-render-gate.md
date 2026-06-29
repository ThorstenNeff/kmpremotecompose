# REM-143 S1 Particle Seed-Render-Gate — ✅ GRÜN

> **Adressat:** PO. **Status:** ✅ **GRÜN (Voll-Render + Seed-Konvergenz-Oracle + Determinismus verifiziert).**
> **Branch (Code):** `feature/REM-143-particles-s1` `5e8c1f3` (dev-2, S1 ParticlesCreate.apply + PARTICLE_LOOP-Intercept).
> **Branch (Bundle):** `feature/REM-143-s1-particle-seed-wire-and-goldens` (test-3, dieser Commit).
> **Bar:** real-Skia Voll-173-Render (0 Collateral auf 167) + 6/6 bit-exact Seed-Konvergenz (independent Spec-Reconstruction) + 6 deterministische Seed-Goldens (Reseed-Pin gewired).

## TL;DR (3 Sätze)

REM-143-S1 (Particle Seed-Frame) ist render-korrekt verifiziert auf 3 unabhängigen Achsen: (1) **Render-Half** — 173 RENDERS / 167 IDENT / 0 Collateral, die 6 Particle-Docs rendern Seed-Partikel statt blank (haptic untouched); (2) **Seed-Konvergenz-Oracle** — 6/6 bit-exact über 282 Partikel zwischen dev-2-Sim (t-overload-eval) und independent Spec-Reconstruction (VAR1-mutate-in-place, no-t-overload-eval) → dev-2s Vereinfachung IST order-äquivalent zur Upstream-Spec; (3) **Determinismus** — mit dem Reseed-Pin-Wire produziert der Sweep 6/6 byte-id über Runs (pre-Pin: 6/6 byte-DIFF). Bundle (Wire + 6 Goldens + Oracle-Test + Verdikt) ready für atomaren Merge mit dev-2 S1-Code.

## Bundle-Inhalt

1. **Wire (1 Datei):** `desktopApp/src/main/kotlin/.../sweep/DesktopRenderSweep.kt` —
   `RpnFloatEvaluator.seedRngForCapture(RenderRngPins.PARTICLE_SEED)` vor `paint()` für **ALLE 173 Docs**
   (nicht nur Particle-Docs — verhindert static-RNG-State-Drift zwischen Sweep-Runs der den ganzen
   Korpus poisonen würde).
2. **6 Re-Baseline-Goldens:** `screenshots/reference/desktop/` —
   - `impulse_demo_confetti_demo.png` 7327B → **16765B** (100 Sprites edge-skewed @ Seed-Frame, S2-Live-Spread)
   - `impulse_demo_hearts_demo.png` 8707B → **112001B** (50 Heart-Glyphs verteilt im Kreis)
   - `particle.png` 67542B → **67239B** (Avatar + 41 Path-Shapes, kleines Δ)
   - `maze.png` 3394B → **4003B** (Maze-Grid + 1 Circle-Particle)
   - `maze1.png` 6155B → **6803B** (dichte Maze + 1 Circle)
   - `maze2.png` 3865B → **17612B** (Maze + 120 farbige Circle-Particles als Row)
3. **Oracle-Test:** `shared/src/jvmTest/.../Rem143S1SeedConvergenceOracleTest.kt` —
   6 Tests (1 pro Particle-Doc), 282 Partikel bit-exact Vergleich Sim vs independent Reconstruction.
   **Compile-Abhängigkeit:** `pc.particles` (S1-neue Property von ParticlesCreate) — kompiliert erst
   nach Merge von dev-2 S1-Branch. PO-Routing: dev-2 S1 zuerst, dann diese Bundle (oder atomar).
4. **Verdikt-Report:** `artifacts/REM-143-S1-particle-seed-render-gate.md` (dieses Dokument).

## Gate-Schritte (test-3 unabhängig)

### 1. Render-Half (Voll-173-Sweep mit Pin)

`./gradlew :desktopApp:desktopRenderSweep` (mit S1-cherry-pick lokal für sweep) → **173 RENDERS / 0 BLANK / 0 ERROR**, byte-diff vs Goldens: **167 IDENT / 6 DIFF** = exakt die 6 PO-genannten Particle-Docs. haptic_demo_demo_haptic1 = IDENT (untouched per PO-Soll). 0 Collateral auf den anderen 167 (incl. heart_rate_timeline, alle clock-docs, alle non-particle docs).

### 2. Determinismus-Verify (Run-A vs Run-B mit Pin)

```
=== run-A vs run-B (6 particle docs, with Pin) ===
  impulse_demo_confetti_demo   a=16765B  b=16765B  [IDENT]
  impulse_demo_hearts_demo     a=112001B b=112001B [IDENT]
  particle                     a=67239B  b=67239B  [IDENT]
  maze                         a=4003B   b=4003B   [IDENT]
  maze1                        a=6803B   b=6803B   [IDENT]
  maze2                        a=17612B  b=17612B  [IDENT]
```

→ Pin wirkt: 6/6 byte-id über Runs. (Pre-Pin: 6/6 byte-DIFF — Non-Determinismus dokumentiert in Render-Half-Status-Update an PO `1521183130218790992`.)

### 3. Seed-Konvergenz-Oracle (independent Spec-Reconstruction vs Sim)

Spec extrahiert aus `androidx/.../ParticlesCreate.java:250-266` (paint + initializeParticle): **particle-major × var-major × VAR1-mutate-in-place × eval**.

Reimplement-Disziplin (PO `1521181920355024966` + `1521184992179257555`):
- **Reuse** (input-data/Player-Primitive): `RpnFloatEvaluator.eval()`, `pc.equations` (byte-decoded), ctx system+DATA_FLOAT vars (populated by player.paint()).
- **Reimpl** (orchestration): eigene `i × j`-Schleife, eigene VAR1-NaN-Scan + literal-Float-Substitution, 3-arg-eval ohne t-overload (vs dev-2: 4-arg-eval mit t-overload).
- **Reseed BEFORE BOTH** Sim-Pass + Oracle-Pass (`RpnFloatEvaluator.seedRngForCapture(RenderRngPins.PARTICLE_SEED)`); zusätzlich BETWEEN Oracle-ctx-paint und Orchestration-call (clean RNG-state für meine RAND-Konsumption).

**Resultat:**
```
[REM-143-S1] impulse_demo_confetti_demo: 100 particles × 6 vars — bit-exact convergence ✅
[REM-143-S1] impulse_demo_hearts_demo:    50 particles × 6 vars — bit-exact convergence ✅
[REM-143-S1] particle:                    10 particles × 6 vars — bit-exact convergence ✅
[REM-143-S1] maze:                         1 particle  × 5 vars — bit-exact convergence ✅
[REM-143-S1] maze1:                        1 particle  × 5 vars — bit-exact convergence ✅
[REM-143-S1] maze2:                      120 particles × 5 vars — bit-exact convergence ✅
```

**282 Partikel × bis zu 6 vars = ≤1692 Float-Vergleiche, alle bit-exact** (`toRawBits()`-Vergleich, NaN-/signed-zero-safe). → **dev-2s t-overload-Vereinfachung IS order-äquivalent zur Upstream-Spec** (RAND-consumption-order konvergiert byte-token-order in beiden Orchestrierungen).

### 4. Visual-Spot-Check (Daten-Orakel + Render zusammen)

- `impulse_demo_confetti_demo`: 10+ Cloud-Sprites am oberen Rand (Seed-Frame edge-skewed). Per Oracle bit-exact zu Spec → korrekt; S2 wird Spread animieren über Time.
- `impulse_demo_hearts_demo`: 50 Heart-Glyphs verteilt um zentralen blauen Kreis mit „5"-Text. Dicht gepackt am Seed-Frame.
- `particle`, `maze`-Docs visual analog.

## Methodologie-Lehren

**Initial 6/6 Oracle-Divergenz war NICHT Sim-Bug.** Mein erster Oracle-Run ergab 6/6 fail. Per `feedback_oracle_sim_divergence_escalate.md`-Disziplin: NICHT still angepasst. Stattdessen charakterisiert → debug-dump zeigte: equations referenzieren System-Vars (z.B. confetti `var[5]` = WINDOW_WIDTH, hearts `var[47]` = DATA_FLOAT id=47=150). Mein Pass-2 ctx hatte nur `seedHostPalette()` — keine System-Vars, keine DATA_FLOAT vars. → `getFloat(id)` returnte 0 → Equation kollabierte. **Hypothese B (Oracle-Bug: ctx-init unvollständig) bestätigt — NICHT Sim-Bug.** Fix: Pass 2 mit player.paint()-populiertem ctx (data-input reuse, orchestration bleibt independent).

**Anti-Anchoring auf "Sim-Bug-Hypothese":** Hypothese A war "dev-2 t-overload divergent" — die plausibel-aussehende Erklärung am ersten Diff. Hypothese B (Oracle-Bug) war notwendig zu verfolgen statt Hypothese A blind anzunehmen. Beide Hypothesen offen halten + charakterisieren bevor escalating ist der Standardweg.

**assist-Hardening-Caveat (PO `1521190611812880525`) verstanden:** der Wire hardcoded `staticTime` (via `cfg.staticTime` default 0 + `RenderTimePins`-Fallback) statt resolved-`startAt` pro Particle-Doc. **Aktuell unkritisch** weil alle Particle-Docs startAt→0 (dev-2 decode-verifiziert); **forward-looking robuster** wäre ein per-Doc-startAt-Pin (entweder via Impulse-State-Lookup in S2, oder explizit über `RenderTimePins`-Entry). Caveat als Code-Kommentar im Wire dokumentiert (DesktopRenderSweep.kt). Deferred Hardening kein Blocker für S1.

## Acceptance-Bar erfüllt

- [x] **Render-Half:** 167 IDENT + 6 expected DIFF + 0 Collateral auf den anderen 167.
- [x] **Seed-Konvergenz-Oracle:** 6/6 bit-exact über 282 Partikel.
- [x] **Determinismus:** run-A vs run-B 6/6 byte-id mit Pin.
- [x] **Goldens:** 6 Re-Baselines per Daten-Orakel verifiziert.
- [x] **Bundle gepusht:** wire + 6 PNGs + Oracle-Test + Verdikt.

→ **Bei PO-Merge:** dev-2 S1-Code zuerst (oder atomar) → dann diese Bundle → REM-143 S1 zu.
