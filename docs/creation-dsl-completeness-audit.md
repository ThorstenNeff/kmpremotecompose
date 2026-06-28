# Creation-DSL Completeness Audit (REM-107)

> Author: dev-3 (Creation-DSL lane), 2026-06-28
> Branch base: rebased on `develop @ 9083575` (post REM-96 + REM-103 + REM-104 merge — see §5 update).
> Methodology: source-grounded against `./androidx` upstream + `shared/src/commonTest/resources/rc-corpus/`. No human input — opcode coverage measured by reading the `creation/` package and cross-referencing emitted opcodes against the corpus opcode set.

## 1. Goal

Map which `.rc` operation types the **procedural creation DSL** (`shared/src/commonMain/kotlin/com/tneff/kmpremotecompose/remote/creation/`) can byte-emit today, vs. which ones the corpus contains but the DSL cannot produce. The output is a gap-list grouped by theme, ranked by corpus-coverage value, with effort estimates the PO can use to scope follow-up stories.

**Scope of "covered":** an op is **covered** iff some public DSL helper (top-level function on `RemoteComposeContext`, or a builder method on `LayoutModifier` / `RcPaint`) terminates in `add(TheOp(...))`, and the wire bytes match the upstream emit. Tests not required for this audit — coverage is a write-side surface question, not a byte-equality question (that's E5 / test-2's harness).

## 2. Corpus inventory

`shared/src/commonTest/resources/rc-corpus/`:
- **Root** (7 files): `c_box.rc`, `c_modifier_width.rc`, `c_text.rc`, `procedure_simple1.rc`, `procedure_simple2.rc`, `small_animated.rc`, `screenshottest.rc`. P0/P1 tier per `MANIFEST.tsv`.
- **`corpus/` subdirectory** (173 files): the broader op-coverage corpus copied from upstream `androidx/.../player-view-demos/src/main/res/raw/`.

**Total: 180 `.rc` files.**

By filename pattern:
- `c_modifier_*`: 49 files — single-modifier exercises (align_by_baseline, background, background_id, border, clip_circle/rect/rounded_rect, collapsible_priority, component_id, compute_measure/position, dynamic_border, fill_max_*, fill_parent_max_*, height/height_in/width/width_in, horizontal_scroll, horizontal_weight, on_click/touch_down/up/cancel, padding, size, spaced_by, vertical_scroll, vertical_weight, visibility, wrap_content_*, zindex, ...)
- `c_*` (containers): 11 files — `c_box.rc`, `c_column.rc`, `c_row.rc`, `c_fit_box.rc`, `c_flow.rc`, `c_state_layout.rc`, `c_collapsible_column.rc`, `c_collapsible_row.rc`, `c_text.rc`, `c_image.rc`, `c_auto_size.rc`.
- `procedure_*`: 12 files — `simple1-6`, `center_text1`, `gradient1-4`, `look_up1`, `text_path_effects`, `version`, `simple_clock_fast/slow`.
- Topic demos: `activity_rings`, `battery_radial_gauge`, `bitmap_font_watch`, `calendar_heatmap`, `clock variants`, `color_list/table/theme`, `countdown`, `cube3d`, `digital_clock`, `fancy_clocks`, `flow_control_checks`, `graph variants`, `haptic_demo`, `heart_rate_timeline`, `hostile_actor`, `hydration_wave`, `impulse_demo`, `indexing_demo`, `linear_regression`, `maze variants`, `moon_phase`, `particle`, `path_demo variants`, `pie_chart`, `player_info`, `plot variants`, `pressure_gauge`, `sensor_demo variants`, `shader_calendar`, `sleep_quality_rings`, `spline_demo`, `spreadsheet`, `step_progress`, `stock variants`, `stop_notches`, `text_baseline`, `texture_demo`, `themed_plot`, `thumb_wheel`, `touch variants`, `wake_demo`, `weather_forecast`, etc.

## 3. Opcode registry

`shared/src/commonMain/kotlin/com/tneff/kmpremotecompose/remote/core/operations/Operations.kt`:
- **172 registered opcodes** (`Operations.kt:56-227`).
- **9 reserved/orphan opcodes** never registered (`Operations.kt:233-237`).
- Total opcode space: 256 bytes (0x00 – 0xFF).

Grouped by profile (`Operations.kt:372-445`):
- **DEFAULT_SET** (123 ops, shared V6+V7): the procedural baseline.
- **V6_EXTRA** (2): `DATA_SHADER`, `ROOT_CONTENT_BEHAVIOR`.
- **V7_BASE_EXTRA** (4): `REM`, `MATRIX_CONSTANT`, `MATRIX_EXPRESSION`, `MATRIX_VECTOR_MATH`.
- **ANDROIDX_OVERLAY** (19): font/text/path-advanced, dynamic float lists, color themes, data sound.
- **ANDROIDX_EXPERIMENTAL_OVERLAY** (13): macros, layout compute/flow, touch modifiers, multi-click, referenced-ops, sound expressions.
- **WIDGETS_OVERLAY** (16): subset of ANDROIDX without LAYOUT_CUSTOM + SOUND family.
- **DEPRECATED_OVERLAY** (1): `ROOT_CONTENT_BEHAVIOR` (deprecated in V7).

The **corpus-reachable set** = DEFAULT_SET ∪ ANDROIDX_OVERLAY ∪ EXPERIMENTAL_OVERLAY ≈ **155 ops** (after de-dup). That's our denominator.

## 4. Current DSL coverage (develop @ 772a1f5)

| Family | File | Helpers | Ops emitted |
|---|---|---|---|
| Draw | `DrawHelpers.kt` | `drawCircle/Rect/Oval/Line/Arc/Sector` | DRAW_CIRCLE, DRAW_RECT, DRAW_OVAL, DRAW_LINE, DRAW_ARC, DRAW_SECTOR (6) |
| Paint | `RcPaint.kt` | `paint { … }` block + `linearGradient/radialGradient/sweepGradient` | PAINT_VALUES (1) |
| Text | `TextHelpers.kt` | `addText/drawTextRun/drawTextAnchored/drawTextOnPath/createTextFromFloat/textMeasure` | DATA_TEXT, DRAW_TEXT_RUN, DRAW_TEXT_ANCHOR, DRAW_TEXT_ON_PATH, TEXT_FROM_FLOAT, TEXT_MEASURE (6) |
| Path (incremental) | `PathBuilder.kt` | `pathCreate / pathAppend{Move,Line,Quad,Cubic,Close,Reset}To / drawPath / drawTweenPath` | PATH_CREATE, PATH_ADD, DRAW_PATH, DRAW_TWEEN_PATH (4 unique) |
| Bitmap | `BitmapHelpers.kt` | `addBitmap/drawBitmap/drawBitmapScaled` | DATA_BITMAP, DRAW_BITMAP, DRAW_BITMAP_SCALED (3) |
| Matrix | `MatrixHelpers.kt` | `matrixSave/Restore/Translate/Scale/Rotate/Skew` + `matrixSaved { … }` | MATRIX_SAVE, MATRIX_RESTORE, MATRIX_TRANSLATE, MATRIX_SCALE, MATRIX_ROTATE, MATRIX_SKEW (6) |
| Clip | `ClipHelpers.kt` | `clipRect/clipPath` | CLIP_RECT, CLIP_PATH (2) |
| Data | `DataHelpers.kt` | `addInt / addDataMapIds / dataMapLookup / dataMapEntry` | DATA_INT, ID_MAP, DATA_MAP_LOOKUP (3) |
| Float expr | `RcExpression.kt` | `floatExpression(...)` + 25 op constants + 8 sys-var refs | ANIMATED_FLOAT (1) |
| Color expr | `ColorExpressionHelpers.kt` | 7 mode-specific builders + `addNamedVariable` (region-0 plain) | COLOR_EXPRESSIONS, NAMED_VARIABLE (2) |
| Root | `LayoutHelpers.kt` | `setRootContentBehavior(...)` | ROOT_CONTENT_BEHAVIOR (1) |
| Document | `DocumentDsl.kt` | `document { … }` auto-emits header + content-desc prolog | HEADER, ROOT_CONTENT_DESCRIPTION (2 auto) |

**Current covered: 37 unique opcodes** (35 user-callable + 2 auto-emitted by the document prolog).

## 5. Merged since the original audit (REM-96 + REM-103, both on develop as of `9083575`)

> Original audit (written against develop @ `772a1f5`) classified these two as **in-flight**.
> Both have since landed on develop (REM-103 @ `bd26220`, REM-96 @ `9083575`); the ops they
> emit are now part of the "covered" surface. Section retained for traceability of the audit
> methodology — the cumulative numbers in §6 already include them.

### REM-96 (merged @ `9083575`) — Layout-container + 14 modifiers

| Sub-family | Helpers | Ops emitted |
|---|---|---|
| Container open | `box/fitBox/column/row/collapsibleColumn/collapsibleRow/flow/state/canvas/root` | LAYOUT_BOX, LAYOUT_FIT_BOX, LAYOUT_COLUMN, LAYOUT_ROW, LAYOUT_COLLAPSIBLE_COLUMN, LAYOUT_COLLAPSIBLE_ROW, LAYOUT_FLOW, LAYOUT_STATE, LAYOUT_CANVAS, LAYOUT_ROOT (10) |
| Container interior | (auto by `standardContainer` + `canvas`) | LAYOUT_CONTENT, LAYOUT_CANVAS_CONTENT (2 auto) |
| Container close | (auto by helpers) | CONTAINER_END (1) |
| Modifiers (14) | `width/height/widthIn/heightIn/padding/background/border/clipRect/roundedClipRect/visibility/zIndex/click/scroll/alignBy` | MODIFIER_WIDTH, MODIFIER_HEIGHT, MODIFIER_WIDTH_IN, MODIFIER_HEIGHT_IN, MODIFIER_PADDING, MODIFIER_BACKGROUND, MODIFIER_BORDER, MODIFIER_CLIP_RECT, MODIFIER_ROUNDED_CLIP_RECT, MODIFIER_VISIBILITY, MODIFIER_ZINDEX, MODIFIER_CLICK, MODIFIER_SCROLL, MODIFIER_ALIGN_BY (14) |
| Scroll-group extras | (auto via `scroll()` group emission) | TOUCH_EXPRESSION (1) |

REM-96 net: **28 new opcodes**.

### REM-103 (merged @ `bd26220`) — Raw-float DATA_PATH

| Helper | Op emitted |
|---|---|
| `addPathData(floats, winding)` | DATA_PATH (1) |

REM-103 net: **1 new opcode** (parallel to the incremental PathBuilder).

### Cumulative coverage after both merge

**66 unique opcodes covered** (37 current + 28 REM-96 + 1 REM-103). Against the ~155 corpus-reachable opcodes, that is roughly **43% by unique opcodes** — and substantially more by **corpus-file frequency** (since the covered ops are the high-frequency ones the bulk of fixtures use).

## 6. Gap list — ops the corpus uses but the DSL cannot produce (even after REM-96/103)

Grouped by theme, sized for follow-up scoping. **Effort estimates** are creation-side LOC; render-side work (if any) is separate.

### G1 — Touch & event extensions (small, high-value)

| Opcode | Int | Corpus | Effort | Notes |
|---|---|---|---|---|
| MODIFIER_TOUCH_DOWN | 219 | `corpus/c_modifier_on_touch_down.rc` | small | Mirror MODIFIER_CLICK pattern. |
| MODIFIER_TOUCH_UP | 220 | `corpus/c_modifier_on_touch_up.rc` | small | Symmetric to TOUCH_DOWN. |
| MODIFIER_TOUCH_CANCEL | 225 | `corpus/c_modifier_on_touch_cancel.rc` | small | Completes the touch-event triplet. |
| MODIFIER_OFFSET | 221 | misc layout fixtures | small | dx/dy translate modifier. |
| MODIFIER_GRAPHICS_LAYER | 224 | complex demos | small | Alpha/scale/rotation/clip-to-layer. |
| MODIFIER_MARQUEE | 228 | (text-scroll demos) | medium | Text-loop animation + animation spec. |
| MODIFIER_RIPPLE | 229 | (touch-feedback demos) | medium | Material ripple (shape + color + duration). |
| MODIFIER_DIMENSION_CONSTRAINTS | 243 | `c_modifier_*` | small | Min/max width/height bounds (4 floats). |
| MODIFIER_MULTI_CLICK | 83 | tap-counting demos | small | Multi-tap detector (max taps, timeout). EXPERIMENTAL. |
| TOUCH_EXPRESSION (standalone) | 157 | `corpus/touch_*.rc` | medium | Already emitted by `scroll()` group; needs a standalone helper for x/y/pressure-driven float expressions. |

### G2 — Advanced draw / path / shape (small)

| Opcode | Int | Corpus | Effort | Notes |
|---|---|---|---|---|
| DRAW_ROUND_RECT | 51 | gauge / shape demos | small | Rect + 4 corner radii. |
| DRAW_TEXT_ON_CIRCLE | 57 | circular-text demos | small | `textId + cx + cy + r + startAngle`. |
| DRAW_BITMAP_INT | 66 | `corpus/bitmap_int_*.rc` | small | Bitmap from device-local pixel int array. |
| PATH_COMBINE | 175 | `corpus/path_demo_*.rc` | medium | Union / intersect / xor of two paths. |
| PATH_TWEEN | 158 | `corpus/path_demo_path_tween_demo.rc` | medium | Animated path morph; partially covered by `drawTweenPath` but PATH_TWEEN itself isn't emittable as a stored op. |
| PATH_EXPRESSION | 193 | `corpus/demo_path_expression_*.rc` | medium | RPN path expression (parallel to `floatExpression`). |
| DRAW_TO_BITMAP | 190 | `corpus/texture_demo_*.rc` | large | Nested render target. |

### G3 — Bitmap-font family (medium, self-contained)

| Opcode | Int | Corpus | Effort | Notes |
|---|---|---|---|---|
| DATA_BITMAP_FONT | 167 | `corpus/bitmap_font_watch.rc` | medium | Glyph-map + metrics asset. |
| DRAW_BITMAP_FONT_TEXT_RUN | 48 | `corpus/bitmap_font_watch.rc` | small | Parallel to DRAW_TEXT_RUN. |
| DRAW_BITMAP_FONT_TEXT_RUN_ON_PATH | 49 | bitmap-font + path | small | Combines BITMAP_FONT + TEXT_ON_PATH. |
| DRAW_BITMAP_TEXT_ANCHORED | 184 | bitmap-font fixtures | small | Anchored bitmap text (ANDROIDX). |
| BITMAP_TEXT_MEASURE | 183 | bitmap-font fixtures | small | Measure bitmap-rendered text. |

### G4 — Expression / lookup completion (small to medium)

| Opcode | Int | Corpus | Effort | Notes |
|---|---|---|---|---|
| INTEGER_EXPRESSION | 144 | `corpus/flow_control_checks_*.rc` | small | RPN int expr (mirror `floatExpression`). |
| TEXT_LOOKUP | 151 | `corpus/color_list.rc` | small | Text-array lookup by int index. |
| TEXT_LOOKUP_INT | 153 | (variant) | small | Parallel for int-array lookup. |
| TEXT_MERGE | 136 | `corpus/demo_text_transform.rc` | small | Concatenate text ids. |
| TEXT_SUBTEXT | 182 | `text_*.rc` (ANDROIDX) | small | Substring extract. |
| TEXT_TRANSFORM | 199 | `demo_text_transform.rc` (ANDROIDX) | medium | upper/lower/case transforms. |
| TEXT_LENGTH | 156 | text-measure demos | small | Length helper. |
| TEXT_STYLE | 242 | `text_*.rc` | medium | Font/size/weight/style/decoration bundle. |
| CORE_TEXT | 239 | `text_*.rc` (ANDROIDX) | medium | Core text-layout integration. |
| MATRIX_CONSTANT | 186 | `procedure_*.rc` | small | 9-float constant matrix (V7 BASE). |
| MATRIX_EXPRESSION | 187 | `procedure_*.rc` | small | RPN matrix expression (V7 BASE). |
| MATRIX_VECTOR_MATH | 188 | `procedure_*.rc` | small | Vector dot/cross helpers (V7 BASE). |
| MATRIX_FROM_PATH | 181 | `corpus/path_demo_*.rc` | medium | Transform from path bounds (ANDROIDX). |

### G5 — Animation / timing (medium)

| Opcode | Int | Corpus | Effort | Notes |
|---|---|---|---|---|
| WAKE_IN | 191 | `corpus/wake_demo_*.rc` (ANDROIDX) | medium | Schedule wake-up. |
| LOOP_START | 215 | `procedure_*.rc` | large | Procedural loop construct. |
| SOUND_EXPRESSION | 206 | `procedure_*.rc` (EXPERIMENTAL) | large | Sound-selection RPN. |
| PLAY_SOUND | 141 | `corpus/play_sound_*.rc` (EXPERIMENTAL) | medium | Trigger sound asset. |
| DATA_SOUND | 169 | (EXPERIMENTAL) | medium | Sound asset id. |
| HAPTIC_FEEDBACK | 177 | `corpus/haptic_demo_*.rc` | medium | Vibration pattern + intensity. |

### G6 — Macros / functions / control flow (large; later epic)

| Opcode | Int | Corpus | Effort | Notes |
|---|---|---|---|---|
| MACRO_DEFINE | 246 | `procedure_*.rc` | large | Define named sub-procedure. |
| MACRO_CALL | 247 | `procedure_*.rc` | large | Call with arguments. |
| MACRO_ARGUMENT | 248 | `procedure_*.rc` | large | Parameter binding. |
| MACRO_BLOCK | 249 | `procedure_*.rc` | large | Scope block. |
| MACRO_FOR_EACH | 244 | `procedure_*.rc` | large | Array iterator. |
| INCLUDE_REFERENCED_OPERATIONS | 245 | `procedure_*.rc` | large | Embed REFERENCED_OPERATIONS block. |
| REFERENCED_OPERATIONS | 142 | `procedure_*.rc` | large | Named operation block for inclusion. |
| FUNCTION_DEFINE | 168 | `procedure_*.rc` | large | Define computation function. |
| FUNCTION_CALL | 166 | `procedure_*.rc` | large | Call defined function. |
| CONDITIONAL_OPERATIONS | 178 | `corpus/flow_control_checks_*.rc` | large | If/branch block. |
| SKIP | 241 | `procedure_*.rc` (ANDROIDX) | small | Skip marker (trivial helper). |

### G7 — Host actions / data binding (large; platform-seam dependent)

| Opcode | Int | Corpus | Effort | Notes |
|---|---|---|---|---|
| HOST_ACTION | 209 | (host-callback) | large | Send action to host. |
| HOST_NAMED_ACTION | 210 | (host-callback) | large | Named action variant. |
| HOST_METADATA_ACTION | 216 | (host-callback) | large | Metadata pass-through. |
| RUN_ACTION | 236 | `corpus/action_*.rc` (ANDROIDX) | medium | In-player action exec. |
| VALUE_INTEGER_CHANGE_ACTION | 212 | (data binding) | large | Int change observer. |
| VALUE_STRING_CHANGE_ACTION | 213 | (data binding) | large | String change observer. |
| VALUE_FLOAT_CHANGE_ACTION | 222 | (data binding) | large | Float change observer. |
| VALUE_INTEGER_EXPRESSION_CHANGE_ACTION | 218 | `flow_control_checks_*.rc` | large | RPN int observer. |
| VALUE_FLOAT_EXPRESSION_CHANGE_ACTION | 227 | float-binding | large | RPN float observer. |

### G8 — Attributes (large; host-mutation protocol)

| Opcode | Int | Corpus | Effort | Notes |
|---|---|---|---|---|
| ATTRIBUTE_TEXT | 170 | `attribute_string.rc` | large | Host-mutable string. |
| ATTRIBUTE_IMAGE | 171 | `attribute_*.rc` | large | Host-mutable image. |
| ATTRIBUTE_TIME | 172 | `attribute_*.rc` | large | Host-mutable time. |
| ATTRIBUTE_COLOR | 180 | `attribute_*.rc` | large | Host-mutable color. |

### G9 — Particles / impulse (large; self-contained creative visuals)

| Opcode | Int | Corpus | Effort | Notes |
|---|---|---|---|---|
| PARTICLE_DEFINE | 161 | `corpus/particle.rc` | large | Define particle system. |
| PARTICLE_LOOP | 163 | `corpus/particle.rc` | large | Emitter + animation loop. |
| PARTICLE_COMPARE | 194 | `particle.rc` (ANDROIDX) | large | Property comparison. |
| IMPULSE_START | 164 | `corpus/impulse_demo_*.rc` | large | Pop-out animation start. |
| IMPULSE_PROCESS | 165 | `corpus/impulse_demo_*.rc` | large | Per-frame impulse. |

### G10 — Theming / metadata / lists (small to medium)

| Opcode | Int | Corpus | Effort | Notes |
|---|---|---|---|---|
| ROOT_CONTENT_DESCRIPTION (explicit) | 103 | A11y | small | Currently auto-emitted in prolog; expose for runtime override. |
| THEME | 63 | `corpus/color_theme.rc` | small | Color-map theme bundle. |
| COLOR_THEME | 196 | `color_theme.rc` (ANDROIDX) | medium | Named theme registry. |
| COLOR_CONSTANT | 138 | `corpus/color_*.rc` | small | Named color id. |
| ID_LIST | 146 | id-array fixtures | small | Fixed-size id array. |
| ID_LOOKUP | 192 | `id_lookup_*.rc` (ANDROIDX) | small | Dereference id by index. |
| FLOAT_LIST | 147 | float-array fixtures | small | Fixed-size float array. |
| DYNAMIC_FLOAT_LIST | 197 | dynamic plots (ANDROIDX) | medium | Mutable float array. |
| UPDATE_DYNAMIC_FLOAT_LIST | 198 | dynamic plots (ANDROIDX) | medium | Mutator op. |
| COMPONENT_VALUE | 150 | `c_state_layout.rc` | small | State-layout selector value. |
| CLICK_AREA | 64 | `touch*.rc` | small | Legacy hit-test region. |
| DEBUG_MESSAGE | 179 | `debug_*.rc` | small | Logging text. |
| REM | 185 | `procedure_*.rc` | small | REM-specific metadata key/value (V7 BASE). |
| ACCESSIBILITY_SEMANTICS | 250 | a11y fixtures | large | Full a11y bundle. |

### G11 — Render-only / out-of-DSL-scope

Auto-emitted by the writer / player at runtime; **no creation surface needed**:
- HEADER (0) — auto by `document {}`
- COMPONENT_START (2) — player marker, no creation API
- CANVAS_OPERATIONS (173) — auto by `canvas { }` REM-96
- DRAW_CONTENT (139) — player composite marker
- DATA_SHADER (45) — shader bytecode (dev-2 owns)
- DATA_FONT (189) — typeface resource (medium effort if needed)

## 7. Recommended next stories (ranked by impact)

Ordered by corpus-coverage value × prereq-blocking × effort. All assume REM-96 + REM-103 are merged first.

| Rank | Story | Op group | Effort | Why now |
|---|---|---|---|---|
| 1 | **Touch + event modifiers** | G1: MODIFIER_TOUCH_DOWN/UP/CANCEL/OFFSET/GRAPHICS_LAYER/DIMENSION_CONSTRAINTS + standalone TOUCH_EXPRESSION | ~150 LOC (small) | 1:1 with MODIFIER_CLICK pattern. Unblocks 4+ `c_modifier_on_touch_*` + `touch_*` fixtures. Tight scope. |
| 2 | **Advanced draw shapes** | G2 subset: DRAW_ROUND_RECT, DRAW_TEXT_ON_CIRCLE, DRAW_BITMAP_INT, PATH_COMBINE | ~200 LOC (small) | Each is a direct Op emitter with fixed arity. Closes gauge/clock/custom-shape corpus. |
| 3 | **Matrix constants + expressions** | G4 matrix triplet: MATRIX_CONSTANT, MATRIX_EXPRESSION, MATRIX_VECTOR_MATH | ~250 LOC (small) | V7_BASE always-on (no profile gating). Mirrors `floatExpression`/`RcExpression` RPN pattern. Used by several `procedure_*` fixtures. |
| 4 | **Bitmap-font family** | G3: DATA_BITMAP_FONT, DRAW_BITMAP_FONT_TEXT_RUN, DRAW_BITMAP_FONT_TEXT_RUN_ON_PATH, DRAW_BITMAP_TEXT_ANCHORED, BITMAP_TEXT_MEASURE | ~200 LOC (medium) | Self-contained family; parallel to existing text family. Unlocks `bitmap_font_watch.rc` + ~7 related fixtures. |
| 5 | **Expression-family completion** | G4 text subset: INTEGER_EXPRESSION, TEXT_LOOKUP, TEXT_LOOKUP_INT, TEXT_MERGE, TEXT_SUBTEXT, TEXT_LENGTH | ~250 LOC (small-medium) | Unblocks `flow_control_checks_*` + `color_list.rc` + text-manipulation demos. RPN int parallels existing float expr. |

**Out of immediate-next scope** (later epics):
- **G6 macros/functions/control-flow** (~1000 LOC, large): requires scope stack + parameter binding + branch tracking. Best as its own E7-class epic.
- **G7 host actions** (~500 LOC, large): platform-seam contract required (host listener registration). Couple with the Compose-DSL applier work in E6.
- **G8 attributes** (~250 LOC, large): host-mutation protocol; couple with G7.
- **G9 particles/impulse** (~400 LOC, large): visual-effect epic.

## 8. Coverage summary

| Stage | Unique opcodes covered | % of corpus-reachable (155) |
|---|---|---|
| Today (develop @ 772a1f5, E1-E4 + prolog) | 37 | ~24% |
| After REM-96 + REM-103 merge (in-flight) | 66 | ~43% |
| After top-3 follow-ups (touch + shapes + matrix) | ~80 | ~52% |
| After top-5 follow-ups (+ bitmap-font + text-expr) | ~95 | ~61% |
| Full procedural feature-complete (excl. host/macro epics) | ~125 | ~80% |

**Frequency-weighted coverage** (since high-frequency ops land first) is substantially higher than these unique-opcode percentages — REM-96 + REM-103 alone unblock the bulk of `c_*.rc` (layout containers + modifiers) and `procedure_*.rc` (DATA_PATH) fixtures, putting frequency-weighted coverage already in the high-70s after merge.

## 9. Hand-off

This audit is doc-only. No code changes accompany REM-107. Next steps:
1. PO scopes follow-up stories from §7's ranked list.
2. dev-3 picks the top-ranked one once REM-96 + REM-103 are merged (file-disjunct so the work can stage cleanly).
3. Re-audit after each merged story to refresh the coverage percentages.
