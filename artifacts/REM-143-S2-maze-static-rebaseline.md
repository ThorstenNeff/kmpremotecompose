# REM-143 S2 maze-Static-Re-Baseline — ✅ GRÜN

> **Adressat:** PO. **Status:** ✅ **GRÜN (Voll-173 + Seed-Daten-Orakel intent-verified).**
> **Branch (Code):** `feature/REM-143-particles-s2` `4930141` (dev-2, S2b PARTICLE_COMPARE + Δt>0-Seed-Gate).
> **Branch (Bundle):** `bugfix/REM-143-s2-maze-static-rebaseline` (test-3, dieser Commit).
> **PO-Routing:** `1521200467516981449` (S2-Voll-Render-Sweep + maze-Static-Re-Baseline) + `1521201334089678919` (assist-§2-GO + Daten-Orakel-Bedingung).

## TL;DR (3 Sätze)

REM-143-S2b (PARTICLE_COMPARE + Δt>0-Seed-Gate) verschiebt 3 maze-Static-Goldens (Compare-Children werden korrekt aus dem Seed-Frame entfernt — sie gehören nur in Process-Frames). **0 Collateral** auf den anderen 170 Docs (170/173 IDENT vs current goldens). **Seed-Daten-Orakel-Bedingung erfüllt:** der bestehende `Rem143S1SeedConvergenceOracleTest` (auf S2 re-run) bestätigt 6/6 bit-exact incl. maze/maze1/maze2 — die 3 maze-Static-Re-Baselines sind „N Partikel an Init-Positionen OHNE Compare-Mutation" intent-verified, NICHT blind self-compared.

## Bundle-Inhalt

1. **3 maze-Static-Re-Baselines:** `screenshots/reference/desktop/` —
   - `maze.png` 4003B → **3773B** (Maze + 1 Init-Ball top-center, KEIN green Compare-Children-Leak mehr)
   - `maze1.png` 6803B → **6568B** (dichte Maze + 1 Init-Ball, KEIN green Leak)
   - `maze2.png` 17612B → **17455B** (Maze + 120 Particle-Row top, sauber)
2. **Verdikt-Report:** `artifacts/REM-143-S2-maze-static-rebaseline.md` (dieses Dokument).

## Gate-Schritte

### 1. Voll-173-Sweep auf S2-Tip `4930141`

`./gradlew :desktopApp:desktopRenderSweep` (mit S2-checkout) → **173 RENDERS / 0 BLANK / 0 ERROR**, byte-diff vs current goldens (die jetzt-mit-S1-merged develop tip `aea653c` enthält):

**170 IDENT / 3 DIFF** = **exakt die 3 maze-Docs**:
- `maze.png` 4003 → 3773 (-230B)
- `maze1.png` 6803 → 6568 (-235B)
- `maze2.png` 17612 → 17455 (-157B)

→ 0 Collateral auf den anderen 170 (incl. confetti/hearts/particle S1-Goldens unangetastet — Δt>0-Gate trennt korrekt Seed-Frame von Evolution-Frames).

### 2. Visual-Bestätigung der Korrektur (maze.png Spot-Check)

- **Pre-S2-Golden:** Maze-Grid + **roter Init-Ball top-center** + **grüner Dot top-left** (Compare-Children-Leak im Seed-Frame).
- **Post-S2-Render:** Maze-Grid + **roter Init-Ball top-center**. **Grüner Dot weg.**

Der grüne Dot war die Compare-Children (conditional child-Render der Compare-Branch), die pre-S2 UNCONDITIONAL gerendert wurden — auch im Seed-Frame, wo sie nicht hingehören. S2s PARTICLE_COMPARE-Walk + Δt>0-Gate scopt sie korrekt auf Process-Frames. Visuell ein klassisches dispatch≠intent-Fix (S1-Render-Half-Plausibility hätte das nicht gefangen — der grüne Dot sah harmlos aus).

### 3. Daten-Orakel-Bedingung (assist `1521201334089678919`)

> „die 3 maze-Static-Re-baseline müssen via DATEN-ORAKEL re-baselined werden (REM-123-Disziplin), NICHT blind/self-compare. Verifizier, dass der korrekte maze-Seed-Frame = N Partikel an Init-Positionen OHNE Compare-Mutation"

**Bedingung erfüllt durch den bestehenden `Rem143S1SeedConvergenceOracleTest` (auf S2-tip re-run):**

```
[REM-143-S1] maze:  1 particles × 5 vars — bit-exact convergence ✅
[REM-143-S1] maze1: 1 particles × 5 vars — bit-exact convergence ✅
[REM-143-S1] maze2: 120 particles × 5 vars — bit-exact convergence ✅
```

**Warum das die Bedingung erfüllt:**

Mein independent Spec-Reconstruction (`reconstructSeedPositions`) ruft ausschließlich `RpnFloatEvaluator.eval(initEq[j], ...)` PRO Partikel auf — **keine Compare-Conditional-Logic, keine Compare-Mutation**. Es ist purely-Init-Eval. Sim's Pass-1 lieft pc.particles über `player.paint()`; Δt>0-Gate verhindert, dass `ParticlesCompare.apply` oder `ParticlesLoop` Evolution/Mutation am Seed-Frame (Δt=0) ausführt — also auch reine Init-Positions ohne Compare-Mutation. **Beide Quellen = pure Init, kein Compare-Mutation; bit-exact identisch** ⇒ die maze-Seed-Positionen sind spec-correct, die maze-Static-Re-Baselines bilden den korrekten „N Partikel an Init-Positionen ohne Compare-Mutation"-State ab. Intent-verified, NICHT blind self-compared.

Zusätzlich: per PO heads-up `1521190267124977704` ist dev-2s Δt>0-Fix der Grund, warum die S1-Konvergenz auf maze1/maze2 wieder grün ist (vor dem Fix waren maze1/maze2 rot — Compare-Mutation am Seed-Frame führte zu Sim-Order-Divergenz vs pure-Init-Spec). S2-Fix repariert beides: visuell (Compare-Children weg vom Seed) UND Oracle-konvergent (Seed-Positionen pure-Init).

### 4. Compile-Cross-Check der Oracle-Test gegen S2-State

Der `Rem143S1SeedConvergenceOracleTest` (in dieser Bundle nicht enthalten, schon auf develop @ `aea653c`) kompiliert + läuft GREEN auf S2-tip. Cross-target-Bar verifiziert: Seed-Frame-Konvergenz survives PARTICLE_COMPARE-Addition.

## Was NICHT in dieser Bundle

- **dev-2 S2-Code** (`4930141`) — file-disjunkt auf separatem Branch, PO mergt atomar.
- **dev-1 Multi-Frame-LIVE-Evolution-Orakel** (Process-Frames) — dev-1s Lane (REM-143-multiframe-gate-harness), separate Gate.
- **Oracle-Test-Update** — der bestehende Test deckt S2-Seed-Frame schon ab (Re-Run auf S2 ist 6/6 grün); kein Test-Code-Change nötig.

## Acceptance-Bar erfüllt

- [x] **Voll-173-Sweep:** 170 IDENT (0 Collateral) + 3 expected DIFF (maze×3).
- [x] **Daten-Orakel-Bedingung:** bestehender S1-Convergence-Oracle bit-exact auf S2-tip (maze/maze1/maze2 alle ✅) → intent-verified pure-Init OHNE Compare-Mutation.
- [x] **Visual-Verifikation:** maze.png Pre-S2-Compare-Leak (green dot) vs Post-S2-clean ist klare Korrektur.
- [x] **3 Goldens re-baselined** auf den korrekten post-S2-State (mit Pin, deterministisch).

→ **Bei PO-Merge:** dev-2 S2-Code (`4930141`) zuerst (oder atomar mit dieser Bundle) + dev-1 LIVE-Evolution-Orakel + assist-§2-GO (laut PO bereits gegeben) → **REM-143 S2 zu**.
