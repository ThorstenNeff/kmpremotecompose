# REM-139 S2 — LayoutCompute Design-Note (dev-2, PO-scoped)

Kurz-Record der S2-Implementierung (LayoutCompute) + des Gate-Plans. PO-scoped (kein TechSpec; 1 Design-
Punkt resolved). Baut auf S1 (Lookup/Measure-Producer, gemergt `02f542a`).

## Mechanismus (mirror upstream `LayoutComputeOperation.applyToMeasure`)
LayoutCompute ist **Modifier + Container** auf einer Komponente, computet ihre **Measure** (type=0) oder
**Position** (type=1) aus einer 6-Slot-Bounds-Liste:
- **buildTree:** `LAYOUT_COMPUTE` = Container-Modifier → attach an die Komponente + Stack-Sentinel (Balance-
  Fix wie CANVAS_OPS/REM-134) + die (flachen) Kind-Compute-Ops capturen (DynamicFloatList/AnimatedFloat/Update).
- **runCompute (measure-Phase):** seedt die Liste `[x,y,w,h,parentW,parentH]` mit der aktuellen Measure,
  re-runt die Kind-Updates (Expressions überschreiben Slots), liest zurück → setzt `w/h` (MEASURE, in
  measureSizes) bzw. `x/y` (POSITION, in assignPositions — trägt jetzt parentW/H).

## 🔑 Design-Punkt (PO-resolved): S1↔S2-Liste-Timing
`measure()` läuft VOR Phase-A. S2 seedt+computet die LayoutCompute-Kind-Liste in measure. Damit Phase-A das
nicht re-zeroed: **`DataDynamicListFloat.apply` ist jetzt allocate-if-absent (idempotent/Frame)** — eine in
measure bereits existierende Liste wird nicht neu allokiert. LayoutCompute-Kinder = measure-authoritative;
standalone-Listen allokieren in Phase-A normal. **Reconciliation-Test** (Pflicht): computed `list[3]` überlebt
den Phase-A-Walk (`Rem139Slice2Test.reconciliation_*`).

## Verifikation
- `c_modifier_compute_measure` (type=0): Box-H **computed=150** (war fill-500), bg-Rect 100×150.
- `c_modifier_compute_position` (type=1): Box-x/y **computed=200,200**.
- **🔴 Gate-Pflicht — Voll-173-Draw-Fingerprint-Sweep (S2 vs develop `02f542a`): NUR die 2 compute-Docs Δ,
  0 Collateral auf den anderen 171** (S2-Changes sind LayoutCompute-gated). Headless-Fingerprint ist
  necessary-not-sufficient (REM-140-Lehre) → **test-3-Voll-Skia-Render** ist das echte Gate.
- §2 render-only: write/read/equals/hashCode unverändert (diff-verifiziert); Conformance-173 grün (full 721/0/0).
- Tests: `Rem139Slice2Test` (4 grün): measure-compute, position-compute, Reconciliation, 3-Ops-Byte-Round-trip.

## test-3-Gate (Pflicht, REM-134/140-Klasse — Shared-Measure-Touch)
1. 2 Ziel-Docs computen/positionieren korrekt gg. Golden.
2. **0 Collateral-Regress** auf den anderen 171 (full-corpus-byte-Diff vs Goldens) — der buildTree-Balance-Fix
   darf keine Container-Docs umkippen (headless 0-Δ belegt's, test-3 bestätigt real-Skia).
3. §2 (PO-Review) + Conformance.
