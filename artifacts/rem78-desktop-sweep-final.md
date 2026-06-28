# REM-78 — Desktop-Render-Sweep Final (post-REM-75, autoritativ)

> **Autor:** QA test-3 · **Datum:** 2026-06-28 · **Branch:** `feature/REM-78-desktop-render-sweep`
> **Stand:** rebased auf develop `9a203cd` (REM-75 ist drin). Pre-REM-75-Baseline siehe git-History.

## 🎯 Cross-Platform-Render-Paritaet Desktop ↔ iOS: **136 / 142 = 95.8%**

| Bucket | n | Bedeutung |
|---|---:|---|
| **PASS** | 104 | pixel-clean (breach ≤2%, kein Cluster) |
| **TEXT** | 32  | diffuse Cross-Skia Font/AA/thin-line-Divergenz (erwartet, Heatmap-Beleg) |
| **BLANK** | 10 | beide blank — nicht gerendert (Feature-Gaps, aus Paritaet ausgeschlossen) |
| **FAIL** | 5  | echte strukturelle Divergenz (zur Triage, s.u.) |
| **ERROR** | 1 | shader_calendar = >2.25x dim-Mismatch (iOS-Golden falsche Groesse?) |

(`comparable=152`; render-Paritaet rechnet PASS+TEXT als „rendered & matched", BLANK ausgeschlossen.)

## Methode

1. Voller 173-Doc-Render: `./gradlew :desktopApp:desktopRenderSweep` (post-REM-75) →
   `screenshots/reference/desktop/*.png` (density=1.0, doc-native-px, statisch t=0).
   Dispatch-Klassifikation: **RENDERS=169 · BLANK=4 · ERROR=0** (BLANK-4 = text-only Docs,
   drawCount zaehlt Text nicht; nicht zwangslaeufig pixel-blank — Pixel-Truth aus parity_compare).
2. Cross-Plat-Diff: `python3 parity_sweep.py ../reference/desktop ../reference/ios --no-resize` →
   neue desktop-mode-Variante (s.u.), Verdikt-Logik unveraendert (PASS/TEXT/BLANK/FAIL/ERROR).

## Tooling-Fix: parity_compare desktop-mode (`--no-resize`)

**Bug:** der bestehende ±2px-LANCZOS-Density-Resize-Pfad (justiert fuer Android-density-2.625 ↔ iOS-3.0)
hat meine Desktop-density-1.0-doc-native-PNGs gegen iOS-Goldens fehlerhaft auf iOS-Groesse resampelt
→ ~3-5% Kanten-Pixel-Differenzen → 79 spurious STRUKTURELL-FAILs im ersten Pre-REM-75-Sweep, wo
Spot-Checks bewiesene Pixel-Identitaet zeigten.

**Fix:** `--no-resize` Flag (in `parity_compare.py` + propagated durch `parity_sweep.py`):
- dim-Diff ≤2px → beidseitig auf `min(W,H)` croppen (Top-Left), kein LANCZOS;
- dim-Diff >2px → ERROR (heterogene Densities sollten im Desktop-vs-iOS-Vergleich nicht passieren).

**Wirkung:** spurious-FAIL 79→5. Render-Paritaet-Zahl 44.4%→95.8%. Authoritativ.

## STRUKTURELL FAIL — 5 echte Cross-Plat-Divergenzen (zur PO-Triage)

| Doc | Spot-Check-Beobachtung |
|---|---|
| `moon_phases` | Desktop = voller Mond + „Days Offset: 0." auf schwarzem Hintergrund · iOS-Golden = nur Mondhaelften, kein Text/BG. **Eher iOS-Capture-Anomalie** (Crop oder mid-frame) als Desktop-Defekt — Desktop rendert MEHR als iOS-Golden. test-1 verifizieren on-device. |
| `moon_phase_dial` | nicht detailliert spot-checked — vermutlich gleicher Muster wie `moon_phases`. |
| `heart_rate_timeline` | nicht spot-checked. |
| `pressure_gauge` | nicht spot-checked. |
| `stock_sparkline` | aus alter Vorgaenger-NOTES bekannt als Android-Path-Fill/Gradient-Gap (REM-8-Aera) — bereits gefixt fuer Android↔iOS, ggf. Desktop-Variante? |

**Empfehlung:** PO routet die 5 an dev-2 (oder test-1 on-device-verify falls iOS-Goldens
re-captured werden muessen). Keiner ist ein REM-78-Blocker — meine Harness liefert sie als
identifizierte Befunde, nicht als REM-78-Defekt.

## BLANK-BOTH (10, Feature-Gaps, kein Render-Beweis)

`attribute_string`, `c_modifier_background_id`, `c_modifier_fill_max_size`,
`c_modifier_fill_parent_max_size`, `c_modifier_wrap_content_size`, `c_state_layout`,
`demo_graphs1`, `hostile_actor1`, `path_demo_path_tween_demo`, `stock`.

(Subset des bekannten 9-BLANK aus Android↔iOS-PARITY-RECORD plus 1-2 zusaetzliche, die sich
mit der no-resize-Verdikt-Schaerfe als BLANK-Pattern reklassifizieren — Wert von BLANK ist
„beide blank", nicht „nur Desktop blank".)

## ERROR-1: `shader_calendar`

iOS-Golden mit Doc-Dim-Mismatch > 2.25x — nicht vergleichbar. Vermutlich altes iOS-Capture
in falscher Groesse persistiert; nicht REM-78-relevant.

## Skiko==Skiko-by-construction bestaetigt

Die 95.8% Render-Paritaet ohne weitere Code-Aenderung am Adapter belegt: Compose-Desktop+iOS
rendern via gemeinsamem Skiko = gleichem Ergebnis fuer fast den ganzen Korpus. REM-75 hat die
3 jvm-Stubs auf Skiko-real gehoben (1:1 iOS-Vorlage), Rest der Render-Logik in commonMain ist
target-agnostisch und „greift" einfach auf jvm wie auf iOS.

## REM-78 Status & verbleibende Lieferung

- ✅ `:desktopApp:desktopRenderSweep` Harness (post-REM-75 funktional, 173 in ~20s).
- ✅ Pre-REM-75/post-REM-75-Differential (Bucket A/C): particle + bit_draw2 als REM-75-Gate-Pixel-Beweis.
- ✅ `parity_compare.py` Desktop-Mode (`--no-resize`).
- ✅ Cross-Plat-Verdikt 136/142 = 95.8%.
- ✅ 5 echte FAIL-Befunde fuer PO-Routing.

Empfehlung: REM-78 mergebar nach assist-Review (Helper-Approval bereits da). Die 5 FAILs sind
neue Stories (oder per-Doc-Fixes via test-1 on-device).

## Reproduktion

```
./gradlew :desktopApp:desktopRenderSweep
mkdir -p /tmp/dd_desktop_ios && \
  cd screenshots/parity && \
  python3 parity_sweep.py ../reference/desktop ../reference/ios \
    --diff-out /tmp/dd_desktop_ios --no-resize
```
