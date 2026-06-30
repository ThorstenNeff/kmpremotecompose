# iOS Render Baseline — develop `340aed5` (REM-143)

**Owner:** test-1 (QA). **Captured:** 2026-06-29. **Device:** iPhone 17 sim
(EDC99396-AD21-4DF9-B9A9-4BEF7D3408E2, iOS 26.5). **App:** rebuilt + installed at `340aed5`
(`xcodebuild -scheme iosApp -sdk iphonesimulator` → `simctl install`; the previously-installed
app was from an earlier commit — verified rebuild for a faithful 340aed5 anchor).

## Purpose
iOS counterpart to the Android baseline (`../../android/340aed5/`) — current-state attribution
anchor so post-S2 **iOS** render collateral is cleanly attributable. Same intent: not a golden,
not a self-compare; committed goldens are partly stale → divergence = flag-not-auto-fail.

## Method & the iOS asymmetry (honestly flagged)
- Capture: `xcrun simctl openurl <udid> 'kmprc://render?rc=<doc>'` (static — no `live=1`) →
  `simctl io screenshot`. (`hide_error_dialogs` is Android-only; iOS sim has no launcher-ANR issue.)
- **No `idb`/`idb_companion` on this host → no a11y-tree CLI dump** (the iOS equivalent of Android's
  `uiautomator dump`). So there is **no full 173-doc textual draw-count table** for iOS — that's the
  one asymmetry vs. the Android baseline. The iOS full-173 anchor is therefore the **PNG set**
  (visual collateral oracle: re-capture post-S2 + diff the canvas region per doc → changed docs).
- The full **173 iOS PNGs** were captured locally (0 bad, 0 zero-byte) but are **not committed here**
  (~30 MB binary; the team shares only git). They live in the capture scratchpad; reproduce via the
  recipe above for the post-S2 diff. **Committed = the 8-doc particle/impulse delta-zone PNGs** (the
  post-S2 change zone) + the delta-zone draw-count table below.

## Delta-zone — A↔iOS draw-count parity (the headline finding)
`delta_zone_drawcounts.tsv`: all 8 particle/impulse seed-frame draw-counts are **identical across
Android and iOS** → the shared `commonMain` draw path emits the same draw-ops on both targets (same
`.rc` → same ops), strong cross-target consistency.

| doc | iOS | Android |
|---|---|---|
| impulse_demo_confetti_demo | 102 | 102 |
| impulse_demo_hearts_demo | 52 | 52 |
| particle | 83 | 83 |
| maze | 7 | 7 |
| maze1 | 7 | 7 |
| maze2 | 126 | 126 |
| haptic_demo_demo_haptic1 | 44 | 44 |
| heart_rate_timeline | 13 | 13 |

Draw-counts read from the on-canvas honesty hooks visible in each PNG (`rendered / <n> / <doc>`);
seed-frames visually verified (confetti cluster, hearts, particle sprites, maze grids, haptic grid,
HR wave). Same **post-S1-seed-state** note as Android: S1 is already in at 340aed5 → these are seed
draw-counts; live evolution (S2) is the §5b data-oracle's job, not this static anchor.
