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

## Golden round 6 (2026-06-26) — gradient/float-slot fix re-capture (dev-2 9a5422b, both platforms)
9a5422b (resolve NaN var refs in PaintData bundle float slots) made gauge/gradient docs render proper fills/strokes on BOTH
platforms (commonMain). Re-froze 11 Android goldens + re-rendered 11 iOS on 9a5422b (rebuilt iOS app):
  stock_sparkline, hydration_wave, thumb_wheel2 (the 3 path-fill FAILs → now FILL, FLIP to PASS), + activity_rings,
  battery_radial_gauge, calendar_heatmap_grid, moon_phase_dial, paths_demos, pressure_gauge, step_progress_arc, thumb_wheel1
  (also improved: proper gradient/stroke instead of faint outlines/degenerate). All 11 current-A↔current-iOS = 96-99% PASS.
- SLOT-FIDELITY verified: non-gradient docs (procedure_simple/c_box/maze/path) 0% diff unchanged; throws DROPPED 21→2; no
  cursor-desync/shifted paint-ops. The float-slot fix is clean.

## Golden round 7 (2026-06-27) — REM-47 quick-win renders-now goldens (develop 0b1f546)
PO listed 9 docs as "render now, need goldens". Verified each (fresh env, visual): only 4 produce VISIBLE pixels →
frozen A+iOS (99% A↔iOS): c_modifier_fill_max_size (yellow fill), c_modifier_fill_parent_max_size (yellow), c_modifier_vertical_scroll,
c_state_layout (blue fill). Real color-fill layout demos, not degenerate.
The other 5 are NOT visible-pixel quick-wins (dispatch≠visual or by-design) — reported, NOT frozen:
- c_modifier_wrap_content_size: white-on-white BY-DESIGN (correctly invisible, like empty doc).
- path_demo_path_tween_demo: designed-overflow BY-DESIGN (blank).
- c_modifier_background_id: blank, DEFERRED color-seed (real gap pending color-seed redo).
- text_refresh_bug: drawCount=5 but VISUALLY BLANK = dispatch≠visual (text invisible/off-canvas) — potential gap, NOT quick-win.
- simple_java_anim: drawCount=1 but blank (even &live=1) = dispatch≠visual — potential gap.

## Golden round 8 (2026-06-27) — REM-57 static-time-pin: 16 now-deterministic dynamic-text goldens (develop 144ec70)
With all 4 eval levers merged (REM-53 text-values + REM-54 text-position + REM-41 conditional-gate + REM-57
static-time-pin), the time-driven docs render DETERMINISTICALLY (static-mode pins timeSeed=0 → clocks at 12:00,
fixed values) — no longer wall-clock-flaky, so freezable. Captured on develop 144ec70, full `gradle clean` (the
:androidApp:clean-doesn't-rebuild-shared gotcha), verified-173-APK, race-guarded sweep.
- 16 frozen (each VERIFIED deterministic: identical across 2 fresh captures, <1% diff):
  base, count_down, countdown, experimental_gmt, experimental_solar_gmt, player_info, plot2, plot3, plot4,
  plot_wave, procedure_simple_clock_fast, procedure_simple_clock_slow, server_clock, texture_demo_texture_clock,
  texture_demo_texture_clock_test, wake_demo_wake_clock. (server_clock hand straight-up = 12:00 = timeSeed=0 confirmed.)
- These are the VISIBLE subset (dispatch≠visual): of ~23 headless-text-resolved docs, 16 render visible pixels.
- NOT frozen (routed to dev-1): flow_control (black in BOTH static & live on-device — green LT-branch not rendering
  despite timeSeed=0 proven via the 12:00 clock = a real conditional-render gap, NOT a capture-mode issue) +
  12 complex clocks (clock, digital_clock1, fancy_clocks_*, spline_demo = headless-resolve but no visible glyphs).
- iOS parity of the 16: pending the Maestro iOS driver (port 22087 down).

================================================================================
## REM-69 — durable QA cross-platform golden baseline (develop 36f9d18, 2026-06-28)
================================================================================
Authoritative baseline superseding all prior ad-hoc goldens. Branch: feature/REM-69-golden-baseline
off develop 36f9d18 (REM-64 full-corpus-bundle + REM-60 offscreen-flush + REM-61/REM-67 color-id-resolve, all merged).

FROZEN: 151 docs x2 platforms (screenshots/reference/{android,ios}/), = 142 REM-67-UNCHANGED faithful docs
(re-validated 153-pixel-identical to round-11 baseline on 36f9d18, minus 10 blank fancy-clocks + flow_control held)
+ 9 REM-67 color-id wins.
- 9 wins: clock, stock, text_refresh_bug, simple_java_anim, themed_plot1 (blank->visible flips) + good_pie_chart,
  pie_chart2, battery_radial_gauge, plot_wave (chart/gauge color-id). A<->iOS parity mean 94.07% / median 97.51%
  (plot_wave 74.8% / battery 86.9% = Android-Canvas vs iOS-Skiko font/line-AA on detail-dense docs; content matches).
- The 142 unchanged include the by-design correct-blank layout docs (c_modifier_fill_max_size,
  c_modifier_fill_parent_max_size, c_state_layout, attribute_string, path_demo_path_tween_demo) — faithful blank.
- Provenance: captures from the 36f9d18-render full sweep; REM-60 is 0-delta (re-verified — the 9 wins matched the
  e69c284 combined-branch capture at 100%), so render == committed 36f9d18. Reproducible-from-git.

HELD / PENDING (22 docs — NOT frozen; goldens land as follow-up updates):
- via REM-65 (1): flow_control_checks_test_conditional. ROOT-CAUSED: iOS painted a spurious NEGATIVE-RADIUS circle;
  Android (=text) is CORRECT per the upstream oracle. Faithful golden @&t=0 = TEXT on BOTH, frozen AFTER the REM-65 fix.
  (Discovered via deterministic &t-pin: at the same pinned t=0 Android=text/iOS=circle — a real divergence, not jitter.)
- via REM-68 theme-palette (12): digital_clock1 (solid-cyan over-fill, no digits), calendar_heatmap_grid (solid-green
  over-fill) + 10 blank fancy/analog clocks (clock_demo1_clock1, clock_demo2_jancy_clock2, clock_demo2_jclock2,
  experimental_fancy_clock, experimental_sweep_clock1, fancy_clock2, fancy_clocks_fancy_clock1/2/3,
  spline_demo_spline_demo1) — need the host system_accent theme palette to render a visible dark face. NOTE: clock pin
  at &t=36630 is cross-platform-SAFE (probe: server_clock/procedure_simple_clock_fast A<->iOS 99%+, no TIME_IN_SEC
  offset) — freeze clocks at &t=36630 once visible.
- REM-67-changed, pending oracle/theme review (9): color, color_list, color_table, count_down, demo_text_transform,
  experimental_gmt, experimental_solar_gmt, procedure_simple6, weather_forecast_bars (fallback-color changes, not yet
  confirmed final-correct).
KNOWN-GAP docs that ARE frozen (tracked, faithful-current): c_modifier_wrap_content_size (REM-66 iOS white-on-white
gap), c_modifier_background_id (deferred color-seed) — carried from round-11 with these notes.

## REM-69 update — flow_control golden unfrozen (develop 5fbed28, post-REM-70/REM-65, 2026-06-28)
REM-70 (incl. REM-65) merged → develop 5fbed28: the circle-r<=0 guard makes iOS skip the spurious negative-radius circle,
so flow_control_checks_test_conditional now renders the TEXT branch on iOS too — matching Android (the upstream oracle).
FROZEN (was held under REM-65): flow_control_checks_test_conditional, both platforms, as TEXT-on-both.
- Source: the &t=0 deterministic capture (Android mean 19,19,9 / iOS 17,17,8 = text), build d959bc4 == merged 5fbed28
  render (re-verify was 4/4 GREEN: heart_rate bands restored, Android pixel-identical, A<->iOS flow_control 93.7%).
- ⚠️ IMPORTANT: this golden is the **&t=0-PINNED** render. The default deep-link (no &t) for this doc is time-driven
  ((TIME_IN_SEC%3) gates circle/text/red) → non-deterministic. The conformance/screenshot test for this doc MUST pin &t=0,
  else it will flake. (Per the REM-62 deterministic frame-pin, now on develop.)
Baseline now: 152 frozen + 21 held (was 151 frozen + 22 held). REMAINING HELD (21): REM-68 theme-palette (12:
digital_clock1, calendar_heatmap_grid + 10 blank fancy/analog clocks) + fallback-color pending-oracle (9: color, color_list,
color_table, count_down, demo_text_transform, experimental_gmt, experimental_solar_gmt, procedure_simple6, weather_forecast_bars).

## REM-69 update — REM-68 accent goldens + re-freeze (develop 0fd62c9, post-REM-68 host-theme-palette, 2026-06-28)
REM-68 (system_accent palette wiring) merged → develop 0fd62c9. QA verdict: GO (Leg-2/138→69 clean — only color/theme docs
changed, 0 layout/geometry; accent fixes correct).
RE-FROZE (REM-68 changed these REM-67-win goldens, all improvements): stock (now real Watchlist UI vs prior dark-green blob),
text_refresh_bug + themed_plot1 (accent bg-tint). From bl_*_68 (= 0fd62c9 render; js-rebase is pixel-neutral).
RE-FROZEN (clock was already a REM-69 win-golden, now updated for accent): clock (hand → accent2_50 blue). NEWLY FROZEN
(unfrozen from held): digital_clock1 (cyan over-fill GONE → dark accent face). Both platforms.
color_table = EXEMPT/held (non-visualizable stacked-swatch doc; ~195 swatches overlap, topmost _1000-black hides the
correctly-resolved near-white accent1_100; dev-2 confirmed commonMain resolve CORRECT, pre-existing layout overlap = REM-88).
Baseline now: 153 frozen (152 + digital_clock1; clock/stock/text_refresh_bug/themed_plot1 updated-in-place) + 20 held
(21 - digital_clock1; color_table stays held-exempt under REM-88).

## REM-77 — shader_calendar golden = BLUE (develop 61b7d07, DATA_SHADER AGSL→SkSL merged, 2026-06-28)
REM-77 (DATA_SHADER apply-path + AGSL→SkSL) merged. shader_calendar (only DATA_SHADER doc) now renders the wave-bands+gradient
shader. Canonical color = BLUE (4-fold proven: dev-2 AGSL source-math rules out gray; Android RuntimeShader/AGSL = blue;
iOS Skiko/SkSL = blue; dev-2 desktop-jvm-Skiko = blue). The prior gray golden was the stale PRE-REM-77 gray-fallback (shader
not applied) — a code-state mismatch, not a render bug.
RE-FROZEN (gray-fallback → blue shader), both platforms: shader_calendar. Captured on REM-77 branch 175485f (= 61b7d07
render). Android RuntimeShader (emu API 37) + iOS Skiko both blue; A↔iOS = 93.2% when scroll-aligned (~120px month-phase
offset; raw 71% was scroll-start only — Android-AGSL == iOS-SkSL confirmed). test-3 refreshes the Desktop golden (blue) in parallel.
Note: shader_calendar is a tall scrollable doc (mobile crop shows ~May-Aug visible region). Baseline frozen count +0 (in-place update).

## REM-105 — Mobile texture-golden refresh (develop 59a9c37, post-REM-94/98 FILL_AND_STROKE+TEXTURE, 2026-06-28)
test-2's Web-sweep flagged the mobile texture goldens as STALE (pre-REM-98). Confirmed via diff vs fresh §6 post-REM-98
renders (develop 59a9c37, &t=0): the 4 texture goldens showed the pre-texture render — basic_texture = solid-GREEN (78.9%
green), texture_clock + wake_clock = BLACK/empty, texture_clock_test = plain-GRAY — while Web + Desktop + my §6 mobile render
all show the TEXTURE. Diffs 53–79% both platforms; dims unchanged (300×300 A+iOS).
RE-FROZEN (stale pre-texture → real bitmap texture), both platforms, from bl_*_dev (= 59a9c37 §6 captures, feature
verified via zoom + center-hi-freq 35–55 ruling out plain-fill): texture_demo_basic_texture (→ teal bubble texture),
texture_demo_texture_clock (→ grainy noise face), texture_demo_texture_clock_test (→ blue bg + bubble texture + checkered
hand), wake_demo_wake_clock (→ grainy noise face). Analogous to test-3's REM-102 Desktop refresh.
NOT refreshed: path_demo_remote_construction (FILL_AND_STROKE golden already CURRENT — diff 0.2% vs fresh, red stroke-ring
matches). experimental_gmt / experimental_solar_gmt — NO golden (intentionally HELD = fallback-color pending-oracle, time-
driven GMT hands); NOT added (would need PO/oracle sign-off, out of REM-105 scope).
Baseline frozen count +0 (4 in-place updates; held set unchanged).

## REM-112 — Mobile animated/data-doc golden refresh (develop 902c358, stale REM-69-era goldens, 2026-06-28)
test-2's Web-sweep couldn't prove parity/defect for 4 animated/data-driven docs because the mobile goldens didn't match
Web/Desktop. ROOT CAUSE (verify-before-route): NOT a frame-pin ambiguity — the goldens were STALE (moon_phases last frozen
REM-69 `cd770e2`/develop 36f9d18, pre-REM-89/93/98). Current render is deterministic+reproducible (bl_a_d1[REM-93,d=1.0] ==
pin[902c358,&t=0] = 0.0% cross-branch; &density inert for these 4 on 902c358 → plat==d=1.0=0%). Pin = &t=0/static/d=1.0.
RE-FROZEN from fresh 902c358 renders (&t=0), both platforms, A<->iOS verified, dims unchanged:
  • moon_phases — old golden = REM-89 density-LEAK (blown-up clipped band) → now correct half-lit moon phase (post-REM-93). 73.9%.
  • thumb_wheel2 — old = stale green-bar + clutter → clean blue indicator. 63.2%.
  • heart_rate_timeline — text re-sized (REM-74/density); heart element present on mobile. 15.0%. NOTE: keeps the separate
    REM-110 wasm-only gap (heart missing on Web only; Desktop/Mobile show it) — REM-112 fixes the stale-mobile part, REM-110 the Web part.
  • text_refresh_bug — accent green-clover + arrow now present (REM-68). 10.5%.
A<->iOS parity 92.6–98.4% (residual = AA + REM-51 dp-round: A 500/500/475/300 · iOS 498/498/474/300). Frozen count +0 (in-place).

## REM-135 — Mobile baseline-seed re-baseline: digital_clock1 + clock @t=36630 (branch feature/REM-135, off develop 58a5714, 2026-06-29)
REM-135 added the `&palette=baseline` capture param (RcRouter.forceBaselinePalette → RemoteComposeApp seeds baselineHostPalette()
instead of the live device accent — the palette analogue of the `&density=1.0` capture pin). Goldens are now captured with the
deterministic BASELINE host palette on every target, so a device-specific Material-You accent can never bake into a golden.
VERIFY-BEFORE-ROUTE: the 5-doc REM-135 census (test-3) carries DESKTOP/Web palette flips (fallback-cyan→resolved, the
harness-never-seeded bug). On MOBILE the goldens were ALWAYS seeded (RemoteComposeApp app-path) → never poisoned: stock /
text_refresh_bug / themed_plot1 mobile goldens were already baseline (MAD 0.00 / 0.56 / 0.66 vs fresh baseline capture) → NOT
re-frozen (re-freeze = pure AA churn). Only two mobile goldens changed, and the change is **stale RENDER (new content), not
palette**:
  • digital_clock1 — the digit scroll-wheel now renders (old golden was blank navy, pre-wheel). New: navy bg + "10" wheel @t=36630.
  • clock — the accent1_50 scalloped face now renders (old golden was white-bg, no face). New: accent1_50 face + spread hands @t=36630.
PIN = &palette=baseline + &t=36630 (REM-69 cross-platform-safe clock pin; the old goldens were degenerate &t=0 12:00-collapse) +
platform density (matches the existing 500/498-px dp-round convention). Cross-target t aligned with test-3's desktop re-capture
(develop f4b41e0, also @t=36630) — verify-before-route flagged the t-pin trap before the push.
DATA-ORACLE (resolved == BASELINE palette constant, NOT cross-target self-compare), all green both platforms:
  • digital_clock1: bg #40527D = system_primary_dim_light; digit #FAF8FE = system_background_light.
  • clock: face #EEF0FF = system_accent1_50; hand #6476A5 = system_accent1_500; 2nd hand #404659 = accent2_700; dot #836E99 = accent3_500.
RE-FROZEN (both platforms): digital_clock1 (android 500×1500 · ios 498×1500), clock (android 500×500 · ios 498×498).
A<->iOS parity: digital_clock1 99.99% (MAD 0.02), clock 99.7% (MAD 1.56). Frozen count +0 (2 docs × 2 platforms, in-place).
NOTE on this emu: Android `Resources.getSystem()` does NOT reflect the runtime Material-You overlay in-process (cmd-overlay-lookup
showed a forced red accent #ffd64145 while systemAccentPalette() still returned baseline 0xFF6476A5), so live==baseline HERE and
the param shows no pixel flip on this emulator — the flip was proven via on-device logcat instrumentation (forceBaseline=true →
baselineHostPalette → a1_500=0xFF6476A5). The determinism guarantee holds by construction for any device where the accent diverges.
