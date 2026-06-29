# Android Render Baseline — develop `340aed5` (REM-143)

**Owner:** test-1 (QA). **Captured:** 2026-06-29. **Device:** emulator-5554 / Pixel_8a (API 37).

## Purpose
Current-state **attribution anchor** for the mobile (Android) render path at `340aed5`, the
mobile counterpart to test-3's clean Desktop baseline. After REM-143 **S2** lands, re-run the same
capture and **diff `draw_count` per doc** → the delta pinpoints exactly which docs changed, so any
mobile render shift is cleanly attributable (target-doc change vs. unintended collateral on the
shared `commonMain` draw path, per TechSpec §5 "0 Collateral").

**NOT a golden / NOT a self-compare.** Committed mobile goldens are partly stale (old `commonMain`;
test-2 finding) — this baseline is the *current* render state, not a pass/fail oracle. A render that
diverges from a committed golden is **probably a stale golden, flag-not-auto-fail**; real golden
refresh runs via the data-oracle (REM-123 / §5b), not pixel self-compare.

## Contents
- `baseline_340aed5.tsv` — per-doc table for all **173** corpus docs:
  `doc · state · draw_count · error · doc_id_match`.
- `png/` — visual before-state for the **8-doc particle/impulse delta-zone**
  (confetti, hearts, particle, maze, maze1, maze2, haptic_demo, heart_rate_timeline).

## Method (reproducible)
1. **Emu setup (mandatory):** `adb shell settings put global hide_error_dialogs 1` — suppresses the
   chronic "Pixel Launcher isn't responding" ANR dialog that otherwise steals the top window and
   poisons uiautomator/screencap. (A wedged launcher needs `adb reboot` first.)
2. Per doc: warm deep-link `am start -a VIEW -d 'kmprc://render?rc=<doc>'` (**static** — no `live=1`,
   so the window idles and uiautomator can read; this also makes captures deterministic).
3. `uiautomator dump` → parse the on-screen honesty hooks (`rc-doc / rc-draw-count / rc-rendered /
   rc-error`), with an **inline retry until `rc-doc==<doc>`** (kills the warm-transition race where a
   heavy doc renders slower than the settle and the dump catches the previous frame).
4. `draw_count` = number of draw primitives in the committed static frame (deterministic per doc).

## Caveats / known limits
- **`shader_calendar`**: row = `rendered-visual / draw=n/a`. It renders correctly (full multi-month
  calendar + animated wave shader — visually verified, see commit), but it is a **very tall doc** so
  the rc-* hook nodes render *below* the canvas, off the visible viewport → uiautomator (visible-tree
  only) can't read its hooks. This is a capture-method limit, **not** a render bug.
- All other **172/173** docs: `doc_id_match=OK`, `draw_count` valid.
- `draw_count` is the **static seed/t=0 frame** primitive count. Animated docs are pinned at t=0.

## Notable finding (context for "post-S2 delta")
At `340aed5`, the particle/impulse docs **already render non-blank seed frames** — REM-143 **S1**
(particle seed-render) has landed (cf. `Rem143S1SeedConvergenceOracleTest`). So this baseline anchors
the **post-S1 seed state**, not the original all-blank state. Delta-zone static seed draw-counts:

| doc | draw_count |
|---|---|
| impulse_demo_confetti_demo | 102 |
| impulse_demo_hearts_demo | 52 |
| particle | 83 |
| maze | 7 |
| maze1 | 7 |
| maze2 | 126 |
| haptic_demo_demo_haptic1 | 44 |
| heart_rate_timeline | 13 |

S2 = evolution + impulse-timeline (LIVE) — the **static** seed-frame draw-counts here may stay stable
while the live multi-frame behavior changes; live-evolution correctness is the §5b data-oracle's job,
not this static baseline.
