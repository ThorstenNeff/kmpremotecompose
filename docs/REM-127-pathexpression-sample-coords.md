# REM-127 — Source-B Sample-Path-Coords (dev-2 → test-3)

Erwartete @ t=0 (static) PathExpression-Geometrie pro Doc, **gegeben unsere Eval** (REM-127-Impl
`434a099`). Doc-Space (vor DrawPath-Matrix), aus `getPathData(id)` nach `RemoteComposePlayer.paint`.
Format pro Pfad: `id mode polar count · pts/distinct · first · last · bbox`. **Diskriminator:**
non-degeneriert = `distinct ≈ count` + reale bbox-Spanne; **degeneriert** = `distinct=1`, bbox=[0,0,0,0].
**Anti-Anchoring:** Source B (dies) VOR Source A (Render) lesen. dispatch≠visual gilt hart — DrawPath
wird IMMER dispatched; nur das Pixel/diese-Coords zeigen die echte Kurve.

## Non-degenerierte Pfade (erwartete sichtbare Kurve — Haupt-Verifikationsmenge)
```
clock        id63 SPL POLAR c64  64/64  bbox=[1.61,0.41 .. 500,499.58]
             id74 SPL POLAR c64  64/64  bbox=[75.21,75.05 .. 425,424.94]
demo_graphs0 id89 LIN cart  c128 128/128 first=(30,199.73) last=(476,199.73) bbox=[30,13.16 .. 476,429.38]
demo_path_expression_path_test1  id52/55/56/57/58/60/61  alle distinct≈60, reale bbox (Detail im Probe-Log)
demo_path_expression_path_test2  id50/51  120/120, bbox≈[75.8,83 .. 424,416] / [124.5..375.4]
demo_path_expression_path_test3  id50/51/52  20/20, reale bbox
linear_regression id98 LIN cart c128 128/128 first=(30,319.8) last=(446,45.74) bbox=[30,45.74 .. 446,319.8]  (= die Regressions-LINIE)
paths_demos  id144/157/158 SPL/LIN POLAR (bbox≈±49/±38), id179 (r=20, ±20), id181 (r=40, [-40,0..40,37.86])
plot2/plot3/themed_plot1  id44/47/61 SPL cart c64 64/64 first=(50,228.23) last=(450,271.76) bbox=[50,210.11 .. 450,289.88]
plot4        id49 SPL POLAR c64 64/64 bbox=[141.25,141.37 .. 357.86,358.17]
plot_wave    id67/72/77/82 LIN cart c300 300/300, bbox y∈[175..325] (die 4 Wellen: cos/sin·sin/sin/step)
```
→ **Verifikations-Erwartung:** test-3 sieht in diesen Docs eine **non-degenerierte Kurve**, die die
oben gelistete bbox-Spanne überspannt und an `first`/`last` ansetzt. (Vollständige first/last je Pfad:
Probe-Log — kann ich als CSV nachliefern.)

## ⚠️ paths_demos — degenerierter Subset @ static t=0 (HONEST FLAG, nicht von der Mechanik)
```
id146/160 (29-len SPL/LIN POLAR), id173/176/177/178/183 (SPL)  →  distinct=1, alle Punkte (0,0)
```
**Befund (verify-don't-trust, gegroundet):** min/max sind **gültige Bereiche** (z.B. 0.07–6.35, 0–6.28
— KEIN Range-Collapse). Die Radius-/Coord-Expressions evaluieren @ t=0 zu ~0 → Pfad kollabiert auf den
Mittelpunkt. **Hypothese: animations-amplituden-getriebene Pfade** (Amplitude 0 @ t=0, animieren in
LIVE) — konsistent damit, dass die **statisch-radius**-Pfade im selben Doc (id179 r=20, id181 r=40)
korrekt non-degeneriert rendern. **NICHT der PathExpression-render-apply-Mechanismus** (30+ Instanzen
inkl. ALLER Plots/Clocks rendern korrekte Kurven). **Alternative:** ein static-unseeded Var (Daten/
Component-Dim), das diese Expr referenzieren. **test-3 disambiguiert per-doc:** ist der Pfad @ t=0
legitim unsichtbar (animiert) → erwartet-degeneriert (kein Bug); soll er @ t=0 sichtbar sein → separater
Seed-Gap (eigenes Ticket, NICHT REM-127-Mechanik). Ich untersuche den Subset auf Ansage als Follow-up.

## Caveat
Doc-Space-Coords (vor der per-DrawPath-Matrix: Plots meist direkt/translate; Polar-Clocks rotiert).
test-3 mappt wie bei REM-121/124/125. Voll-first/last-Liste je Pfad liefere ich als CSV on-demand.
