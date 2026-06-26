# Render goldens — provenance (test-1 / QA)

Per-platform golden reference images for the §6 render parity gate (render_parity.yaml Part A +
parity_compare.py Part B).

## procedure_simple1 (F1 — the byte-proven P0 doc)
- Source doc: `procedure_simple1.rc`, 61 B, SHA256 `5474bfb6…` — **byte-identical** to the P0
  conformance oracle and to both bundled app copies (androidApp asset + iOS Resources). The
  rendered doc is provably the same one byte-proven in P0; render correctness builds on a
  verified oracle.
- Doc content: `drawCircle(150, 150, 150)` in a 600×600 document → black circle (default paint),
  top-left quadrant.
- Captured: 2026-06-26 on Android emulator Pixel_8a (1080×2400) + iOS sim iPhone 17 / iOS 26.5
  (1206×2622), via the REM-8 touchable-app shell rendering through the L2 player.
- Crop: rc-canvas region, 600×600 px both platforms.

## Render verification result (first render gate)
- Android render smoke (render_smoke.yaml): green (rc-rendered post-frame-commit, rc-draw-count=1).
- iOS render smoke: green (same gate, iOS bundle id).
- Density-at-image: circle = 300×282 px on BOTH platforms — IDENTICAL. No UIScreen.scale vs
  injected LocalDensity divergence manifests; both render the doc coordinate space at the same scale.
- A↔iOS parity: 100.000% pixel-match (tol=16/255) — far exceeds the 99.5% tolerance band.

These goldens make render_parity.yaml live (was test-first-red without goldens). Re-freeze only
on an intentional render change; a drift is a render regression to investigate (NOT a byte issue —
the .rc bytes are proven equal).

## Golden round 2 (2026-06-26, REM-34 render-by-name) — 3 pixel-confirmed FULL docs
ANDROID goldens (real Maestro cropOn rc-canvas, doc-sized), via deep-link kmprc://render?rc=<name>
with rc-doc identity assertion:
- procedure_simple1.png  600x600  (re-frozen detection->cropOn = batched re-freeze, Android side)
- path_procedural_checks_basic_path.png  300x300  (new)
- path_procedural_checks_all_path.png    300x300  (new)
All three render PRISTINE byte-identical bundled docs (provenance clean).

iOS goldens for basic_path/all_path: BLOCKED on the Maestro iOS driver (port 22087 down since REM-8 —
launchApp/inspect/cropOn all fail to connect). simctl-openurl fallback can't render-by-name on iOS:
it triggers the system "Open in app?" dialog which needs the Maestro driver (openLink autoVerify) to
dismiss; without it the app stays on the default doc. Needs the Maestro iOS driver restarted, then
re-run the iOS cropOn captures. procedure_simple1's iOS golden + 100% A<->iOS parity already exist
(merged REM-8). Only the 2 new path docs' iOS goldens + their parity are pending the driver fix.

## Golden round 3 (2026-06-26) — 137-doc visual-correctness baseline (the 138-render block)
ANDROID goldens (cropOn rc-canvas, doc-sized surface) for the rendering corpus, captured on develop
`0ea4a90` (window+time+deref+E-Layout-1+E-Layout-2, render-order complete) via the verified-173-asset
APK (asset-count hardening: `unzip -l app.apk | grep -c assets/rc = 173`) and the race-free rc-doc==RC
sweep (rsweep_b0-3.yaml, settle + stale-frame guard).

- Count: **137** docs (the 138 rendering-something docs MINUS cube3d).
- Scale/position: **correct-by-design** (dev-2 source-verified: c_box=fixed size(200), FILL fills parent,
  top-left = upstream RootLayout default). surface = doc-sized → Android/iOS coincident (REM-8 §1 holds).
- Distinctness: race-free sweep + distinctness cross-check (near-neighbors confirmed legit dups, e.g.
  maze1≈maze2, clock_slow≈fast). 3 spot-verified visually correct via color_theme-first stale test:
  c_box (red box), c_modifier_background (red+blue), c_column.
- **EXCLUDED — cube3d**: on 0ea4a90 it renders a flat dark disc (pre-3D-matrix-engine). Its golden is
  frozen only AFTER REM-? cube3d-3D (5c333a4) merges + test-2's wireframe-correctness check passes.
- Nature: these are **current-render drift-detection baselines** (distinct, not stale, positions
  correct-by-design). The 3 spot-checked are correctness-confirmed; the rest are current-state baselines
  to catch future render regressions.
- CI re-render note: full render_parity re-rendering needs the doc set bundled in the committed app
  (currently 67-68 committed: 19 showcase + 48 c_* + cube3d-branch). Bundling the full rendering set is
  the follow-up that makes all 137 CI-reproducible; the captures themselves are of byte-identical corpus docs.
- iOS goldens for this set: pending the Maestro iOS driver restart (test-2 lane).

## Golden round 3b (2026-06-26) — cube3d (the deferred 138th), NOW the real 3D cube
ANDROID golden for cube3d, captured on develop `e0b1b04` (cube3d-3D Matrix-Expression-Engine + E-D3a-1
operators merged) via verified-173-APK + rc-doc==RC stale-guarded capture (color_theme-first).
- Render: **genuine 3D cube, front-on at t=0** — blue front face + 4 perspective-foreshortened side faces
  (cyan top / magenta bottom / green left / yellow right) with black wireframe edges, on the dark bg circle.
  This is the "3D-wow" that was missing; the earlier flat disc was only the background circle (cube now
  projects on top). VISUALLY VERIFIED CORRECT (eye-check) — not a draws-pixels-but-wrong case.
- STATIC (t=0): the spin (rotation over time) needs dev-1's E-D1 time-advance. This golden is the
  static front-on projection; an animated/spin golden follows E-D1 with the fixed golden-time (t=0) convention.
- Completes the 138-doc visual baseline (137 round-3 + cube3d).

## Golden round 4 (2026-06-26) — text-complete CORRECTNESS goldens (develop 167de86, TextStyle merged)
Captured on develop 167de86 (text-complete: c_text + ColorExpression + TextStyle-Read) via clean-uninstall+install
(hardening #4) on a rebooted device (#5), verified-173-APK, race-free rc-doc==RC sweep. Full sweep = 140/173 CONFIRMED
(0 regression vs the 138+c_text+dynamic_border expectation).
- 6 goldens (re)frozen as CORRECTNESS goldens (styling now correct — color/size/italic/weight, pixel-verified on c_text:
  Basic Text black/bold/40, Italic Blue blue(0,0,255)/italic/30):
    text_baseline, color_theme, pressure_gauge, shader_calendar (text-styled), c_text (NEW), c_modifier_dynamic_border (NEW,
    computed black border — color source-grounded via ColorExpressionTest; pixel-PARITY pending Maestro-iOS-restart).
  → golden total now 140 (138 baseline + c_text + dynamic_border; 4 text docs updated with correct styling).
- THROW-LOGGING finding (not 0 throws): 31 docs log NON-FATAL render-throws (count still 140; cube3d/pie_chart/stock verified
  MATCH their goldens → render completes despite the caught throw). ~14 gradient/color (procedure_gradient1-4, demo_graphs1,
  graph_graph2, stock, pie_chart, hydration_wave, thumb_wheel1/2, texture_basic, stock_sparkline, use_of_global) → 6b8a119
  (Color-Consumer fail-soft) target. ~11 clock/matrix/other (clocks, cube3d, winding, gmt, haptic, touch, impulse) = separate latent.

## Golden round 5 (2026-06-26) — re-capture stale goldens on current develop 517fd38 (post ColorExpression+TextStyle+colorseed)
The round-3 goldens (frozen on 0ea4a90) predated ColorExpression/TextStyle/colorseed merges → some stale. Re-captured all 140
on current develop 517fd38 (fresh emulator boot, clean-install, race-guard sweep); 7 had changed → re-frozen current:
  color, color_list, color_table (ColorExpression now resolves colorIds → matches iOS), pie_chart, c_modifier_align_by_baseline,
  c_text_auto_size (TextStyle), c_fit_box.
- color FAIL → FIXED (was stale golden; current-Android now resolves colors = current-iOS).
- VERIFY-DON'T-TRUST CATCH: dev-2's "4 color-cluster FAILs all stale" holds ONLY for `color`. stock_sparkline(65%), hydration_wave(82%),
  thumb_wheel2(33%) are UNCHANGED on current develop and STILL diverge from current-iOS → GENUINE current cross-platform divergences
  (Android doesn't fill the path-area/gradient, iOS does), NOT stale goldens. ColorExpression fixed colorIds but not these path/gradient
  fills. Route as real Android path-fill/gradient gap (or iOS over-fill — needs upstream reference), NOT a re-capture.
- demo_bitmap (iOS bitmap) + flow_control (conditional) = deferred features (both platforms). The other 133 goldens unchanged.
