# REM-138 Desktop — RenderTimePins-Wire + 14-Clock-Golden-Re-Baseline

> **Adressat:** PO. **Status:** ✅ **GRÜN** (Wire + 14 Re-Baselines + Daten-
> Orakel-Verifikation). Branch:
> `feature/REM-138-desktop-sweep-timepin-wire` von develop `c9f0013` (REM-138
> Map-Landing). **PO-Routing:** `1521143488232558735`.
>
> **⚠️ Zusatz-Finding (Collateral, NICHT REM-138):** Voll-173-Sweep zeigt
> **11 unerwartete Δ-Docs**, die NICHT in der RenderTimePins-Map sind und
> auch nicht von meinem Wire-Change beeinflusst werden (alle landen weiter
> bei cfg.staticTime=0). **Vermutung: REM-134-TextLayout/DrawContent-
> Collateral** (Docs mit Text-Labels rendern jetzt mehr Inhalt). Im
> Verdikt-Doc-Abschnitt unten dokumentiert; **NICHT** mit re-baselined —
> braucht separate Triage.

## Wire-Change (Atomic mit 14 Re-Baselines)

```diff
 import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
+import com.tneff.kmpremotecompose.remote.player.core.RenderTimePins
 import com.tneff.kmpremotecompose.remote.player.core.renderOpaque
 import com.tneff.kmpremotecompose.remote.player.core.seedHostPalette
 ...
     for ((i, name) in docs.withIndex()) {
         ...
-        val result = renderOne(rcFile.readBytes(), cfg.staticTime, cfg.density)
+        // REM-138 Per-Doc-Time-Pin: zeit-sensitive Docs … RenderTimePins liefert
+        // den cross-target-safe Pin (CLOCK_SAFE_TIME_SECONDS=36630) …
+        val pinnedTime = RenderTimePins.timeFor(name, default = cfg.staticTime)
+        val result = renderOne(rcFile.readBytes(), pinnedTime, cfg.density)
```

Pfad: `desktopApp/src/main/kotlin/com/tneff/kmpremotecompose/sweep/
DesktopRenderSweep.kt` (Eine Stelle, identische Pattern zu REM-135-
`seedHostPalette`-Wire). Eliminiert explizit-`--t 36630`-Workaround:
zukünftige Default-Sweeps pinnen die 16 Map-Docs (clock + digital_clock1 +
14 neue) automatisch.

## 14 Re-Baselined Goldens — Daten-Orakel-Verifikation

Alle 14 Docs sind in `RenderTimePins.pins` (REM-138 Map-Commit `c9f0013` +
ursprüngliches `effe8ed`). Auto-Pin → `CLOCK_SAFE_TIME_SECONDS=36630`. Der
Re-Render zeigt **identische Bytes wie meine fancy-clock-census Δ-Werte**
(explicit-`--t 36630` Render mit gleichem PNG-Output), was die Wire-Korrektheit
bestätigt.

| # | Doc | OLD (t=0 degeneriert) | NEW (t=36630 gespreizt) | Δ Bytes |
|---|---|---|---|---|
| 1 | `clock_demo1_clock1` | 51153B (12:00-Kollaps) | 53476B (Zeiger gespreizt ~10:10) | +2323 |
| 2 | `clock_demo2_jancy_clock2` | 86272B | 91501B | +5229 |
| 3 | `clock_demo2_jclock2` | 127601B (World-Clock alle bei 8:10-Init) | 129007B (per-Stadt-Times sichtbar) | +1406 |
| 4 | `experimental_fancy_clock` | 48583B | 51385B | +2802 |
| 5 | `experimental_gmt` | 96270B (Hands gestapelt vertikal, Droid doppelt) | 106425B (gespreizt) | +10155 |
| 6 | `experimental_solar_gmt` | 114867B | 120636B | +5769 |
| 7 | `experimental_sweep_clock1` | 51153B (alias clock_demo1_clock1) | 53476B | +2323 |
| 8 | `fancy_clock2` | 86272B (alias jancy_clock2) | 91501B | +5229 |
| 9 | `fancy_clocks_fancy_clock1` | 68295B (Hands alle bei 12) | 70718B | +2423 |
| 10 | `fancy_clocks_fancy_clock2` | 86170B | 91789B | +5619 |
| 11 | `fancy_clocks_fancy_clock3` | 48583B | 51385B | +2802 |
| 12 | `server_clock` | 5100B (Hands gestapelt 12:00) | 9903B (gespreizt) | +4803 |
| 13 | `texture_demo_texture_clock` | 113002B (Hand vertikal 12) | 116136B | +3134 |
| 14 | `wake_demo_wake_clock` | 131869B (Hand vertikal 12) | 135375B | +3506 |

**Daten-Orakel-Methode (NICHT Cross-Target-Self-Compare):**
- Alle Goldens waren `t=0`-degeneriert (12:00-Hand-Kollaps, kein gültiges
  Cross-Target-Orakel — sie waren ja deshalb in der Census-Liste).
- Re-Baseline auf `t=36630` = REM-69-cross-platform-safe Clock-Pin =
  gespreizte Zeiger = visuell-meaningful + cross-target deterministisch.
- Census-Spot-Checks (4 Docs visuell verifiziert in REM-138-Census-Routing
  `1521142337957269664`): clock_demo1_clock1 / server_clock /
  fancy_clocks_fancy_clock1 / texture_demo_texture_clock — alle 4 Zeiger
  visuell von gestapelt-12 → gespreizt-10:10.
- Wire-Korrektheits-Beweis: Δ-Bytes der 14 Docs in diesem atomic-Sweep
  matchen exakt die Δ-Bytes meiner fancy-clock-census mit explicit `--t 36630`
  → der Auto-Pin produziert dieselben Renders wie explicit-CLI-Arg.

## Voll-173-Sweep Negativ-Kontrolle

- **148/173 byte-identisch** zu pre-Wire-Goldens (kein Sweep-Overreach).
- **14/173 die REM-138-Map-Docs** flippen — wie erwartet, alle palette-resolved
  + jetzt-gepinnt.
- **11/173 unerwartete Δ** — siehe Collateral-Sektion unten (NICHT durch
  REM-138-Wire verursacht, separate Triage erforderlich).

## ⚠️ Collateral-Finding (separate Triage, NICHT in diesem Bundle)

Voll-173-Sweep zeigt 11 Docs außerhalb der REM-138-Map mit Δ vs. current
Goldens. Diese Docs sind **NICHT in RenderTimePins** und werden vom Wire-Change
**nicht beeinflusst** (alle rendern weiter @ cfg.staticTime=0, default). Die
Δ kommt daher von **anderen post-Golden-Commits**, vermutlich **REM-134
TextLayout/DrawContent-Render-Pfad** (Docs mit Text-Labels rendern jetzt
mehr Inhalt; player_info/text_baseline rendern weniger — möglicherweise
strukturelle Änderung).

| Doc | Δ Bytes | Klassifikations-Hinweis |
|---|---|---|
| `color_list` | +47515 | wahrsch. REM-134-Text-Gain (color-name labels) |
| `color_table` | +69473 | wahrsch. REM-134-Text-Gain (cell labels + RED-channel-text mehr sichtbar) |
| `color_theme` | +43595 | wahrsch. REM-134-Text-Gain (theme-name labels) |
| `demo_graphs1` | +40245 | wahrsch. REM-134-Text-Gain (graph axes labels) |
| `demo_text_transform` | +7812 | wahrsch. REM-134-Text-Gain |
| `player_info` | **-54236** | 🔴 **REGRESS-Verdacht**: 55938B → 1702B (massiver Inhaltsverlust) |
| `text_baseline` | **-23295** | 🔴 **REGRESS-Verdacht**: 36585B → 13290B (Inhaltsverlust) |
| `c_column` | +7 | AA-noise |
| `c_modifier_align_by_baseline` | -159 | moderat |
| `c_row` | 0 | AA-noise (byte-distinct, same size) |
| `stock` | -80 | AA-noise |

**Empfehlung:** `player_info` + `text_baseline` als möglicher REM-134-Regress
flaggen für PO-/dev-2-Triage. Die 5 +X-Δ-Docs sind vermutlich REM-134-
Render-Verbesserung (Text rendert jetzt korrekt) → können nach Triage
re-baselined werden. 4 AA-Noise-Docs sind harmlos.

**Diese 11 Docs sind NICHT Teil des REM-138-Bundles** — sie warten auf
separate Triage. Bundle scope = nur die 14 RenderTimePins-Map-Docs.

## Empfehlung

✅ **GRÜN — REM-138 Wire + 14 Goldens mergen** (atomar; Wire kapselt die
14 Pins, Goldens entsprechen den 14 Pins → kein recurring false-Δ in
Default-Voll-Sweeps).

⚠️ **Collateral-Triage:** PO bitte ggf. an dev-2 relayen — 11 unerwartete
Δ-Docs (insbesondere 2 mögliche Regresse `player_info` und `text_baseline`).
Diese sind außerhalb des REM-138-Scopes.

— test-3, 2026-06-29
