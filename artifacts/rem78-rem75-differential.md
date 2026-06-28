# REM-78 — REM-75 Differential-Pixel-Beweis (Bucket A/C)

> **Autor:** QA test-3 · **Datum:** 2026-06-28 · **Branch:** `feature/REM-78-desktop-render-sweep`
> **Antwort auf:** PO-Anweisung (1520784762107723886) — Differential Stub-Baseline vs REM-75-applied
> auf den Bitmap-/Offscreen-/Opaque-sensitiven Docs als Merge-Gate fuer REM-75.

## TL;DR

**REM-75 mergebar.** Auf den 9 Bucket-A/C-Docs verursacht REM-75 **2 klare visuelle Bildverbesserungen
(particle, demo_bitmap_drawing_bit_draw2)** durch tatsaechliches Bitmap-Rendering, **0 Regressionen**
auf den Rest (visuell identisch oder PNG-Encoding-Rauschen). Die zwei Treffer sind genau die Faelle,
in denen `ImageDecode.jvm` ungleich null das Bild erzeugt.

## Methode

1. **Baseline** = current jvm-stubs @ commit `4aa26b3` (develop `c542b05`, REM-75 NICHT angewandt):
   `screenshots/reference/desktop/<doc>.png`.
2. **REM-75-applied** = lokaler cherry-pick von `7b02cd7` (`feature/REM-B-desktop-actuals` REM-75-Commit)
   auf den Baseline-Tip, kein anderer Code geaendert. Re-Render des gleichen Subsets mit
   `--out screenshots/reference/desktop_rem75 --docs <bucket A/C>`:
   `screenshots/reference/desktop_rem75/<doc>.png`.
3. Cherry-pick **wieder entfernt** (lokaler reset, kein Push) — die Branch-Spitze ist die Baseline.
   Die `desktop_rem75/`-PNGs liegen als reines Evidenz-Artefakt im Repo, ohne REM-75 selbst zu
   importieren (PO mergt REM-75 separat).
4. PNG-byte-Vergleich (byte-equal? Delta-Groesse?) plus visueller Diff der signifikant geaenderten.

## Ergebnisse (9 Docs, alle drawCount > 0)

| Doc | Δ bytes | Visuell | REM-75-Treffer? |
|---|---:|---|---|
| **particle** | **+61281** | Stub: graue Box mit dunklem Rechteck-Placeholder · REM-75: **photorealistisches Portrait** (== iOS-Golden) | **JA — DRAMATISCH** |
| **demo_bitmap_drawing_bit_draw2** | +1939 | Stub: 16 WEISSE Kreise auf gelb · REM-75: **16 SCHWARZE Kreise** auf gelb (das echte Bitmap rendert) | **JA — KLAR** |
| texture_demo_texture_clock | +2053 | dunkler Kreis · dunkler Kreis (visuell identisch, PNG-Encoding-Rauschen) | nein-visuell |
| wake_demo_wake_clock | +2053 | dunkler Kreis · dunkler Kreis (visuell identisch, PNG-Encoding-Rauschen) | nein-visuell |
| texture_demo_basic_texture | +893 | visuell identisch | nein-visuell |
| hostile_actor1_c | +629 | visuell identisch | nein-visuell |
| c_image | +256 | grauer Placeholder oben-links · idem | nein-visuell |
| demo_bitmap_drawing_bit_draw1 | -35 | visuell identisch (Shapes-Doc, kein Bitmap-Pfad) | nein-visuell |
| stock | -1066 | visuell identisch (dunkelgruener Hintergrund) | nein-visuell |

**0 Regressionen.** Kein Doc verliert sichtbar Inhalt durch REM-75.

## Klassischer dispatch≠render-Befund

Sowohl baseline als auch post-REM-75 zeigen drawCount=11 fuer `particle`, drawCount=67 fuer `bit_draw2`.
**Der Walk dispatcht in beiden Faellen gleich oft** — nur das Pixel-Ergebnis unterscheidet sich. Genau
der Befund, vor dem das PROJECT_CONTEXT-Manifest+CLAUDE.md warnt: dispatch ist nicht render. Hier
besonders deutlich, weil `DRAW_BITMAP_*` durch den walk geht (Dispatch zaehlt), aber `decodeImageBitmap`
intern null liefert → keine Pixel.

## Empfehlung an PO

**REM-75 mergen.** Der Pixel-Beweis fuer den primaeren Effekt (Bitmap-Decode-Pfad funktioniert) ist
auf `particle` und `bit_draw2` eindeutig. Die uebrigen 7 Docs des Bucket A/C sind invariant — entweder
weil sie keine echten Bitmap-Treffer haben (c_image-Placeholder, stock = Shape-only) oder weil der
Bitmap-Pfad andere Failure-Modes hat, die ausserhalb REM-75 liegen.

## Was REM-78 NICHT abdeckt (Follow-ups, separate Stories)

- **parity_compare desktop-mode**: aktueller `parity_compare.py` ist auf android-density 2.625 ↔ iOS
  3.0 mit ±2px-Resize justiert; mein Desktop ist density=1.0 doc-native px → spurious 79 "FAIL" im
  Sweep. Spot-checks zeigen pixel-Identitaet, der Diff-Pfad muss desktop-density=1.0 erkennen und ohne
  Resize vergleichen. **Eigener REM-78-Follow-up oder neue Story.**
- **OpSpan-Scan**: identifiziert die echten DRAW_BITMAP_*-/Offscreen-/OpaqueSurface-Doc-Treffer
  (Bucket A/C koennte mehr als die 9 PO-genannten umfassen). Aktuell nicht noetig fuer den
  REM-75-Merge-Gate.
- **Post-REM-75 voller 173-Re-Sweep**: nach PO-Merge von REM-75 + meinem rebase, voller Sweep gegen
  desktop/ + parity_compare (mit desktop-mode-Fix) → autoritative Cross-Plat-Render-Paritaet-Zahl.

## Datenpunkte / Reproduktion

- Baseline-Sweep CSV: `screenshots/reference/desktop/_sweep.csv` (173 Zeilen)
- REM-75-Applied-Sweep CSV: `screenshots/reference/desktop_rem75/_sweep.csv` (9 Zeilen, Bucket A/C)
- Harness-Aufruf:
  ```
  ./gradlew :desktopApp:desktopRenderSweep -PsweepArgs="--out screenshots/reference/desktop_rem75 \
    --docs c_image,texture_demo_basic_texture,texture_demo_texture_clock,\
demo_bitmap_drawing_bit_draw1,demo_bitmap_drawing_bit_draw2,stock,particle,hostile_actor1_c,wake_demo_wake_clock"
  ```
- Repro REM-75-Anwendung: `git cherry-pick 7b02cd7` (vom `feature/REM-B-desktop-actuals`-Branch).
