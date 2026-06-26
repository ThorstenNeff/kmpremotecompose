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
