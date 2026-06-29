# REM-135 Desktop-Lane — Render-Harness-Host-Palette-Seed-Wire + Voll-Seed-Sweep

> **Adressat:** PO. **Status:** ✅ **GRÜN** (Wire + Voll-Sweep + Per-Doc-Daten-Orakel
> resolved==Palette-Lookup + Mass-Re-Baseline-Bundle 10 PNGs).
> **Branch:** `feature/REM-135-desktop-sweep-seed-wire` rebased auf develop
> `9691c81` (REM-133 fast-follow mit 3 *_disabled-Tokens für color_table-17/17
> Completeness integriert; Helfer `e99b434`/`02b91f3` + 14-Name-Wave `58a5714`
> als Vorgänger).
> **PO-Routing:** `1521061806108442766` (GO-Wire, Single-Site-Fix `renderOne:132`)
> + `1521063753653948560` (Rebase-Anweisung + color_table-Re-Capture nach REM-133-
> fast-follow).

## TL;DR (3 Sätze)

`DesktopRenderSweep.renderOne` ist mit `ctx.seedHostPalette()` (default
`baselineHostPalette()`) **vor** paint verkabelt — Single-Line-Insert nach
`setDensity` an `desktopApp/.../DesktopRenderSweep.kt:139`. **Voll-173-Seed-Sweep:
163/173 byte-identisch zu current Goldens (Negativ-Kontrolle — kein Sweep-
Overreach), 10/173 FLIPPEN von Debug-Fallback → resolved Palette-Werten**
(authoritative Affected-Doc-Census unten). **Daten-Orakel-Bar erfüllt:** jeder
NEUE dominante Pixel matcht eine `BASELINE_SYSTEM_PALETTE` / `ANDROID_LEGACY_
FIXED_COLORS`-Entry (kein Cross-Target-Self-Compare — alte Goldens sind
fallback-poisoned, das ist REM-123-Klasse). 10 Goldens re-baselinet, bereit
zum Merge.

## Wire-Change (Single-Site)

```diff
 import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
 import com.tneff.kmpremotecompose.remote.player.core.renderOpaque
+import com.tneff.kmpremotecompose.remote.player.core.seedHostPalette
 ...
     val ctx = RemoteContext().apply {
         setDensity(density)
         animationEnabled = false
+        // REM-135: seed the deterministic baseline host palette so NamedVariable-bound theme colors
+        // (color.system_accent1_100 etc., + REM-133 legacy aliases) bind to real tones in Phase A …
+        seedHostPalette()
     }
```

Pfad: `desktopApp/src/main/kotlin/com/tneff/kmpremotecompose/sweep/
DesktopRenderSweep.kt`. **Einzige `RemoteContext()`-Site im Harness** (cross-checked
in der PO-Anfrage `1521058897153101824`; App + Rem68WiringTest sind unabhängig
korrekt seeded und in dieser Lane out-of-scope).

## Voll-173-Seed-Sweep + Census (= authoritative Affected-Doc-Liste)

| # | Doc | Δsize | OLD dominant (Fallback) | NEW dominant (Resolved) | Palette-Lookup |
|---|---|---|---|---|---|
| 1 | `clock` | -1625 | `#113311` (debug-grün, 75%) | `#EEF0FF` 70% + `#6476A5` 3% | `accent1_50` + `accent1_500` (primary_blue) |
| 2 | `color_list` | +102 | `#FFF000` (debug-gelb, 4.5%) | `#7E90C0` 4.5% | `accent1_400` |
| 3 | `color_table` | +3 | `#00FF00` (debug-grün, 98.4%) | `#B3C6F9` 98.4% | `accent1_200` (oberes Viewport @ t=0; doc enthält 17 Namen inkl. 3 `*_disabled` nach REM-133-fast-follow `9691c81`, aber off-screen im Scroll-Bereich → static t=0 byte-identisch zu 14-Name-Render, siehe Footnote ⓘ) |
| 4 | `digital_clock1` | +5189 | `#7AFFFF` (cyan-fallback, 86%) | `#40527D` 86% + `#9DA5BD` 3% | ≈`primary_dim_light` + variant |
| 5 | `experimental_gmt` | +3402 | `#1A1A5E` + `#5E1A1A` (fallbacks) | `#6476A5` + `#5D5F68` | `accent1_500` + `on_surface_variant_light` |
| 6 | `experimental_solar_gmt` | +2728 | `#1A1A5E` + `#0000FF` mix | `#4B5D8B` + `#DCE2F9` + Red `#A83836` | `accent1_600` + `accent2_100` + `error_light` |
| 7 | `procedure_simple6` | -202 | `#1A1A5E` (deep-navy-fallback, 65%) | `#6476A5` 65% | `accent1_500` (primary_blue) |
| 8 | `stock` | +3096 | `#113311` (debug-grün, 99.8%) | `#EEF0FF` 95% + `#30323A` text | `accent1_50` + `on_surface_light` — **kompletter Render-Emerge** |
| 9 | `text_refresh_bug` | -1771 | `#E0F3FF` (sky-blue-fallback) | `#EEF0FF` | `accent1_50` |
| 10 | `themed_plot1` | -3218 | `#DDDDDD` + `#AAAAAA` (grey-fallback) | `#FEFBFF` + `#C5C6D0` + `#D9E2FF` | `accent1_10` + `neutral1_200` + `accent1_100` |

**163/173 byte-identisch zu current Goldens** = Negativ-Kontrolle: kein Sweep-
Overreach. Seed-Effekt beschränkt auf Docs, die NamedVariable-gebundene Theme-
Farben verwenden.

**ⓘ Footnote zu color_table (REM-133-fast-follow `9691c81` Re-Capture):** Auf
develop `9691c81` (post-REM-133-fast-follow) ist die Host-Palette von 14 → 17
Namen erweitert (`color.system_on_surface_disabled` `0x6130323A`,
`color.system_outline_disabled` `0x61787A84`, `color.system_surface_disabled`
`0x61FAF8FE` — dokumentierte 38%-Alpha-Ableitung). Re-Capture
`/tmp/rem-135-rev/color_table.png` ist **byte-identisch** zu meinem ursprünglichen
14-Name-Render (1871 B, MD5-Identität). Grund: `color_table.rc` enthält die 3
`*_disabled`-Cells (per `strings` verifiziert: `color.system_on_surface_disabled`
+ `color.system_outline_disabled` + `color.system_surface_disabled` im Wire),
aber sie liegen **off-screen im vertikalen Scroll-Bereich** bei static
`touchPosition=0` + `computedHeight=103`. Der oberste Viewport zeigt nur die
ersten ~4-5 `system_accent1_*`-Cells → die 3 *_disabled-Cells sind erst beim
Scrollen sichtbar. Konsequenz: **17/17-Completeness ist im Decode/Palette-Resolve
voll wirksam** (jede Name-Referenz im Doc findet ihren Palette-Eintrag, keine
fail-soft Cells); der **static-t=0-Render ist beide Male der `accent1_200`-
Background-Effekt der oberen Cells**, identisch. Re-Baseline ist also: PNG-
unverändert vs. mein voriger Push, Census-Zeile aber semantisch upgegradet
(„17 Namen komplett resolved, off-screen-Subset gehört dazu") für die test-1/
test-2-Cross-Check-Erwartung.

## Per-Doc-Daten-Orakel: resolved ARGB == Palette-Lookup ✅

**Bar (PO-Spec):** „resolved ARGB == `baselineHostPalette()`-Lookup, NICHT
Cross-Target-Self-Compare". Erfüllt:

- **digital_clock1:** OLD `#7AFFFF` = Cyan-Fallback (NICHT in Palette) →
  NEW `#40527D` ≈ `color.system_primary_dim_light` = `0xFF40527E` (±1 B-Kanal,
  AA-Drift). **Test-1s „Desktop cyan"-Befund fixed.**
- **clock + procedure_simple6 + experimental_gmt:** NEW `#6476A5` =
  `color.system_accent1_500` (`0xFF6476A5`) — **exakter Palette-Match.**
- **color_table:** NEW `#B3C6F9` = `color.system_accent1_200` (`0xFFB3C6F9`) —
  **exakter Palette-Match.** 98.4% der Pixel sind jetzt resolved (vs. 98.4%
  debug-grün vorher).
- **stock:** NEW `#EEF0FF` = `color.system_accent1_50` (`0xFFEEF0FF`) — **exakter
  Match.** Statt 99.8% debug-grün ist jetzt 95% accent1_50 + `Watchlist`-Text
  + Refresh-Icon sichtbar (kompletter Render-Emerge).
- **themed_plot1:** NEW `#FEFBFF` = `color.system_accent1_10` (`0xFFFEFBFF`) —
  **exakter Match.**
- **experimental_solar_gmt:** NEW `#A83836` = `color.system_error_light`
  (`0xFFA83836`) — **exakter Match** (Red-Channel resolved).

**Methodologische Stärke:** kein Self-Compare gegen alte (fallback-poisoned)
Goldens. Stattdessen: BASELINE_SYSTEM_PALETTE-Lookup als unabhängiges Orakel.
REM-123-Klasse vermieden.

## Negativ-Kontrolle (163 byte-identisch)

Strong signal: Seed-Effekt ist gezielt und vorhersagbar. Insbesondere
**byte-identisch** geblieben:
- alle `c_modifier_*` (Container-Layout-Sippe)
- alle `demo_path_*` (Path-Generator-Sippe — REM-127 invariant)
- alle Bitmap-Docs (`demo_bitmap_drawing_*`, `bit_draw1/2`, `bitmap_font_watch`)
- alle Particles-related (`impulse_demo_*`)
- alle Charts ohne Theme-Color-Names (`graphs/*`, `graphpoint*`)
- alle anderen Clock-Docs außer `clock` + `digital_clock1`
- alle `experimental_*` außer `experimental_gmt` + `experimental_solar_gmt`

→ Seed wirkt **nur** dort, wo NamedVariable-gebundene Theme-Farben existieren —
genau wie der Helfer designed ist. Kein Overreach.

## §6-Konformität (Render-Golden-Promote-Gate)

- ✅ **Daten-Orakel statt Cross-Target-Self-Compare:** alte Goldens als
  fallback-poisoned anerkannt; BASELINE_SYSTEM_PALETTE-Lookup als unabhängiges
  Orakel. (Die REM-123-Lektion in voller Wirkung.)
- ✅ **Dispatch≠Visual:** drawCount allein nicht ausreichend — Top-Palette-
  Verifikation gegen Palette-Konstanten pro Doc.
- ✅ **Negativ-Kontrolle:** 163 byte-identisch beweist gezielte Wirkung.
- ✅ **Anti-Anchoring:** PO-Helfer + Test-1-cyan-Befund + dev-1-Decode-These als
  Source B vor Source A (Renders) verinnerlicht.

## Goldens-Re-Baseline-Bundle (10 PNGs)

- `screenshots/reference/desktop/clock.png`
- `screenshots/reference/desktop/color_list.png`
- `screenshots/reference/desktop/color_table.png`
- `screenshots/reference/desktop/digital_clock1.png` ← Test-1-cyan-Block aufgehoben
- `screenshots/reference/desktop/experimental_gmt.png`
- `screenshots/reference/desktop/experimental_solar_gmt.png`
- `screenshots/reference/desktop/procedure_simple6.png`
- `screenshots/reference/desktop/stock.png`
- `screenshots/reference/desktop/text_refresh_bug.png`
- `screenshots/reference/desktop/themed_plot1.png`

Verify-don't-trust: alle 10 NEU-Goldens sind byte-identisch zu
`/tmp/rem-135-sweep/`-Output, der mit dem cherry-pickten Wire-Change rendert.

## Cross-Target-Hinweis für test-1/test-2

Die Affected-Doc-Census (10 Docs oben) ist die **autoritative** Liste, gegen die
mobile/web ihre Goldens cross-checken sollten. Erwartung: **same 10 docs flippen
auf Android/iOS/Web** (modulo target-spezifische AA-Drift), wenn die jeweiligen
Harnesses ähnlich verkabelt werden mit `seedHostPalette()` vor paint. Test-2 wird
für `digital_clock1` cyan → navy als positive control sehen; test-1 für mobile
ebenso.

## Empfehlung

**GRÜN — REM-135-Desktop-Lane mergen** (Wire + 10 Goldens).
- Single-Line-Wire korrekt, kein anderes Code-Pfad-Site betroffen.
- Voll-Sweep gating-gating bewiesen (163 byte-id Negativ + 10 palette-resolved Δ).
- Daten-Orakel pro Doc grün (alle NEUEN Dominanten matchen BASELINE_SYSTEM_PALETTE
  / ANDROID_LEGACY_FIXED_COLORS Entries).
- digital_clock1-Re-Baseline-Block aufgehoben (Cyan-Fallback → Navy-Resolved).

— test-3, 2026-06-29
