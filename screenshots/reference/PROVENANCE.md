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
