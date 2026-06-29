# REM-139 S2 LayoutCompute — Voll-Render-Gate ✅ GRÜN

> **Adressat:** PO. **Status:** ✅ **GRÜN** (Daten-Orakel + 0 Collateral).
> **Branch (Code):** `feature/REM-139-compute-lookup-s2` `169d30c` (dev-2).
> **Branch (Goldens):** `feature/REM-139-s2-golden-rebaseline` (test-3, dieser Commit).
> **Bar:** Voll-173-Render-Sweep auf real-Skia (ImageComposeScene), Byte-Diff vs current Goldens, 3-Tip-Bisect-Attribution (pre-S1 ↔ S1-tip ↔ S2-tip), Daten-Orakel-Verifikation der 2 Ziel-Docs visuell.

## TL;DR (3 Sätze)

REM-139-S2 (LayoutCompute Measure/Position-Producer für `c_modifier_compute_measure` + `c_modifier_compute_position`) ist **render-korrekt mit 0 Collateral** auf den anderen 171 Docs (kritisch wg. REM-134/140-Klasse LayoutMeasure-shared-path-touch). 3-Tip-Bisect (pre-S1 `00f8ee9` ↔ S1-tip `02f542a` ↔ S2-tip `169d30c`) trennt S2-Δ sauber von S1-Δ. **2 Goldens re-baselined** (1707→1712B + 1718→1725B), per Daten-Orakel verifiziert: `compute_measure`-Box-H = 150 (war fill) + `compute_position`-Box bei (200,200) (war 0,0).

## Gate-Schritte (test-3 unabhängig)

### 1. Voll-173-Render-Sweep auf `169d30c`

`./gradlew :desktopApp:desktopRenderSweep -PsweepArgs="--out /tmp/rem-139-s2-sweep --csv ..."` → 173 RENDERS / 0 BLANK / 0 ERROR. Byte-Diff vs Goldens: **169 IDENT, 4 DIFF.**

### 2. 3-Tip-Bisect-Attribution (Source A)

Re-Render der 4 DIFF-Docs auf pre-S1 (`00f8ee9` REM-140-Tip) UND S1-tip (`02f542a` = aktueller develop) zur Trennung von S1-Δ vs S2-Δ:

| Doc | Golden | pre-S1 `00f8ee9` | S1-tip `02f542a` | S2-tip `169d30c` | Attribution |
|---|---|---|---|---|---|
| `c_modifier_compute_measure` | 1707B | — | 1707B IDENT | **1712B (S1↔S2 DIFF)** | **S2-Ziel** |
| `c_modifier_compute_position` | 1718B | — | 1718B IDENT | **1725B (S1↔S2 DIFF)** | **S2-Ziel** |
| `procedure_look_up1` | 5444B | 5444B IDENT | 7664B DIFF | 7664B (IDENT S1↔S2) | S1-attributable (REM-142-pending) |
| `procedure_center_text1` | 7883B | 7883B IDENT | 8041B DIFF | 8041B (IDENT S1↔S2) | S1-attributable (REM-142-pending) |

**Schlussfolgerung:** S2 verändert **NUR** die 2 Ziel-Docs. Die 2 S1-attributable DIFFs (pre-S1=IDENT vs Golden, S1↔S2=IDENT) sind 100% S1-Merge-Time-Δ, NICHT S2-Collateral. **0 S2-Collateral auf den anderen 171.**

### 3. Daten-Orakel-Verifikation der 2 Ziel-Docs (Source B = PO-Soll)

- **`c_modifier_compute_measure`** (LayoutCompute type=0 MEASURE):
  - Pre-S2: rotes Rechteck ~125px breit × **VOLL-HEIGHT 500px** (fill).
  - Post-S2: rotes Rechteck ~125px breit × **~150px** = **EXAKT Box-H=150**, PO-Soll erfüllt.
- **`c_modifier_compute_position`** (LayoutCompute type=1 POSITION):
  - Pre-S2: blaues Quadrat ~125×125 an **Position (0,0)** (top-left).
  - Post-S2: blaues Quadrat ~125×125 **zentriert um Pixel (200,200)** = **EXAKT Box-(200,200)**, PO-Soll erfüllt.

### 4. Verdikt

**S2-Code GREEN für Merge** (0 S2-Collateral, 2 Ziel-Docs Daten-Orakel-verifiziert).
**Re-Baseline-Bundle:** 2 Goldens in diesem Commit, atomar mit dev-2-Code `169d30c` per PO-Routing.

## Methodologie-Notes

- **3-Tip-Bisect-Attribution** war Pflicht, weil ein Voll-Render-Sweep auf S2-Tip 4 DIFFs zeigte — ohne S1-Tip-Re-Render wäre nicht klar, welche dem S2-Touch (LayoutMeasure-shared-path) und welche dem S1-Merge zuzuordnen sind. Spart die Fehlattribution-Falle.
- **S1-Merge-Time-Gap aufgedeckt:** `procedure_center_text1` hatte denselben S1-`TextMeasure.apply`-Effekt wie das schon bekannte `procedure_look_up1`, war aber am S1-Merge nicht via Voll-Korpus-Byte-Diff gefangen (fingerprint+1-Doc-Gate). Genau die Lehre aus `feedback_gate_verdict_full_corpus_byte_diff.md` — voll-Korpus-byte-Diff vs Goldens als Standard-Verdikt-Bar. PO routet beide Docs gemeinsam in **REM-142** (+-crosshair-Klärung mit dev-2).
- **Render-Disziplin REM-123/§6:** das ist KEIN dynamisch-akkumulierender Kurven-Doc-Bug-Klasse-Fall — die 2 Ziel-Docs sind statische Layout-Compute-Ergebnisse, Daten-Orakel ist hier die direkte visuelle Form-Verifikation (Box-Größe + Position als PO-Soll). Kein zusätzliches Cross-Density/Upstream-Player-Orakel nötig.
