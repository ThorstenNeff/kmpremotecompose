# REM-139 Slice 1 — Source-B Producer-Oracle (dev-2 → test-3)

Erwartete **Producer-Outputs** nach REM-139-S1 (Lookup/Measure-Phase-A-Producer). Pre-S1 waren alle
Slots null/0 (Census dispatch≠visual-Beweis). Daten-Orakel-Gate: die Werte erreichen den Consumer,
NICHT drawCount. Echte Pixel-Positionen = test-3-Desktop-Render (jvmTest hat keinen Text-Renderer →
ich verifiziere die Mechanik mit Fake-Metriken; reale Maße + Sichtbarkeit = test-3).

## `procedure_look_up1` (Lookup-Familie — sichtbarer S1-Effekt)
Kette: `DataMapIds`(ID_MAP 2097194, 3 Einträge) → `DataMapLookup`(id=50, key=49) → `TextMeasure`(51=W, 52=H von text 50).
- **text[50] ≠ null / nicht-leer** — der per Key nachgeschlagene Label (war null → Text fehlte komplett).
- **float[51] = gemessene Breite von text[50]** (≠0), **float[52] = gemessene Höhe** (≠0) — mit echten
  Skia-Metriken die reale Label-Breite/Höhe; die Positionierung, die 51/52 konsumiert, sitzt jetzt richtig.
- **Render-Soll:** der nachgeschlagene Text erscheint sichtbar (vorher blank), an der per-Measure positionierten Stelle.
- Lookup-Typ = STRING (forced: TextMeasure konsumiert text 50). INT/LONG/BOOLEAN sind korpus-absent → loud-guard.

## `c_modifier_compute_measure` / `c_modifier_compute_position` (Compute-Familie — Producer-Teil)
Kette: `DataDynamicListFloat`(2097194, 6 slots) → `UpdateDynamicFloatList`(index 3 / index 0,1).
- **floatArray[2097194] ≠ null, size 6** — Liste allokiert + an den Update-Indizes beschrieben (war null).
- ⚠️ **Voll-sichtbarer Effekt erst in Slice 2:** der Consumer dieser Liste ist `LayoutCompute` (computet
  Measure/Position) — S2, separat. In S1 sind die Producer-Werte da, aber das compute-modifier-Layout
  ändert sich erst mit S2. test-3-S1-Render-Fokus = `procedure_look_up1`; die compute-Docs voll @ S2.

## Gate
§2: write/read/equals/hashCode der 5 Ops unangetastet (diff-verifiziert, Byte-Round-trip grün),
Conformance-173 grün (full jvmTest 703/0/0). test-3 re-baselined betroffene Goldens NUR nach Daten-Orakel
(REM-123), kein blank/pre-Producer-Self-Compare. Voll first/last je nach Bedarf on-demand.
