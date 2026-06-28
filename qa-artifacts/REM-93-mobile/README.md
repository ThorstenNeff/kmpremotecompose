# REM-93 Mobile Render Bundle — for test-3 (3-Target × 2-Density Final Gate)

**Source:** test-1 (QA mobile). Branch is an **ephemeral QA artifact branch** (`qa/REM-93-mobile-renders`),
**NOT for develop** — delete after the gate.

## What this is
Android + iOS on-device renders of dev-2's REM-93 fix (`6be04a9`, "ID_DENSITY = generation density"),
each density doc forced to **`&density=1.0`** and **`&density=3.0`** via the REM-91 deep-link param
(`kmprc://render?rc=<doc>&t=0&density=<d>`). Use the `*-d1` sets as the **doc-native px canon** to
compare against your Desktop renders; use `d1`-vs-`d3` to confirm density-invariance.

## Layout
| dir | platform | forced density |
|---|---|---|
| `android-d1/` | Android (emulator-5554, Pixel_8a API37) | 1.0 |
| `android-d3/` | Android | 3.0 |
| `ios-d1/` | iOS sim (iPhone 17, iOS 26.5) | 1.0 |
| `ios-d3/` | iOS | 3.0 |

6 docs each: `moon_phases, heart_rate_timeline, pressure_gauge, stock_sparkline, activity_rings, pie_chart2`.

## test-1 mobile verdict: **GO**
- **Density-invariance: @3.0 == @1.0 = 0.00%** on all 6 docs, both platforms.
- **Differential proof (param bites, not a no-op):** built pre-fix parent `9866b96` (REM-91 param present,
  REM-93 fix absent) → pre-fix `d1`-vs-`d3` leaks (moon_phases **74.3%**, pressure_gauge 23.3%,
  heart_rate_timeline 17.8%, stock_sparkline 15.6%, activity_rings 6.0%, pie_chart2 1.4%) → post-fix
  **0.00%**. See `_differential_proof.png`.
- **Cross-platform @1.0 parity: 95.7–98.7%** (residual = font/Skiko-AA + known REM-51 dp-rounding:
  Android 500² / iOS 498²). Dimensions are doc-native px, density-independent (REM-51).

## Notes for your Desktop comparison
- Canonical reference = **density=1.0 doc-native px** (per PO density-canon).
- Expect Android/iOS `d1` to match your Desktop `--density 1.0` render modulo AA.
- The ±1–2px dim delta (498 vs 500) is the documented REM-51 dp→px rounding, not a regression.
