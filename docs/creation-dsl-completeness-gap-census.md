# Creation-DSL Completeness — Post-E6 Gap Census (REM-107-Style)

> Author: dev-3, 2026-06-29 (post-REM-144 merge — develop tip `984fca7` at census time).
> **Status:** read-only investigation, no impl. Pre-scopes the next Creation-DSL lane.
> Baseline: `docs/creation-dsl-completeness-audit.md` (2026-06-28). This census is the
> **post-E6-Linie refresh** of that audit + methodology walkthrough using REM-113 as the
> spike-first archetype.

## §1 — Purpose

The 2026-06-28 audit cataloged the procedural Creation-DSL surface vs the corpus-reachable
opcode set and produced a 5-Story ranked roadmap (G1 touch · G2 advanced-draw · G4-matrix · G3
bitmap-font · G4-text-expr). Since then six numbered REMs have landed touching Creation-DSL
surface (REM-96/103 already merged at audit time; REM-113/115/119/126/128/130/141/144 landed
after). E6-Compose-Creation (T1–T4) wrapped 2026-06-29 (`984fca7`).

This census answers:
1. **What did the audit's Story list close?**
2. **What corpus-active gaps remain** (corpus-reach × DSL-emittable)?
3. **What's the methodology lesson** for the next lane (REM-113 walkthrough)?

The audience is the PO (next Creation-DSL lane scoping) and the human (direction-setting).

## §2 — Delta since the 2026-06-28 audit

| REM | Merged | Lane closed | Notes |
|-----|--------|-------------|-------|
| REM-96 | `9083575` | 10 container ops + 14 modifier ops + TouchExpression+FloatConstant auto-by-scroll | Audit Story #1-prereq satisfied. |
| REM-103 | `bd26220` | DATA_PATH (raw-float path) | Audit Story #2-prereq satisfied. |
| REM-113 | (merged) | DRAW_ROUND_RECT helper. DRAW_TEXT_ON_CIRCLE + DRAW_BITMAP_INT helpers landed BUT **empirically corpus-absent** (audit overcounted). | Story #2 partial; §4 walkthrough. |
| REM-115 | `14dd184` | MATRIX_CONSTANT + MATRIX_EXPRESSION + MATRIX_VECTOR_MATH | Audit Story #3 closed. |
| REM-119 | `c622c25` | INTEGER_EXPRESSION + TEXT_MERGE + TEXT_LOOKUP | Audit Story #5 (subset). |
| REM-126 | `be60c26` | Server-Creation §0 (`:server` module + okio disk write) | Out of opcode-coverage scope; closes "create on a server" infrastructure. |
| REM-128 / REM-130 / REM-141 / REM-144 | `7e2f9b8` / `1688f00` / `7e2f9b8` / `984fca7` | E6-Compose-Creation-DSL (T1–T4) — all 14 REM-96 modifiers Compose-accessible; variable primitives (FloatExpression / 7×ColorExpression); higher-level RPN-DSL; dynamic-color background/border full-doc; §6 Maestro create-side. | **No new wire opcodes** — Compose-DSL is an applier on the procedural path. T4-S1 added 2 new procedural helpers (`backgroundColorRef` + `borderColorRef`) for the `flags=2` wire shape — different field values of existing MODIFIER_BORDER / MODIFIER_BACKGROUND ops. |
| REM-139 | `34e1b15` | LayoutCompute (measure/position computed bounds list) | New procedural surface (DataDynamicListFloat / DataMapLookup paths). |

**Net opcode-coverage delta from the audit baseline (66 ops):** the touch-modifier ops claimed
in audit Story #1 are **still open** (G1). REM-113 added a few helpers but two of them are
corpus-absent. REM-115 closed the matrix triplet (G4-matrix). REM-119 closed the text-expr
subset of G4. The matrix and integer/text-expr closures together raise frequency-weighted
coverage materially; absolute unique-opcode count is now in the **~75-80 range** (a clean
re-count is in §3 below).

## §3 — Current procedural-DSL opcode coverage (986fca7)

Verified by listing `add(SomeOp(...))` sites in `shared/src/commonMain/kotlin/com/tneff/
kmpremotecompose/remote/creation/*.kt` (Phase-A grep, then de-dup).

| Family | Helpers / surface | Wire ops emitted |
|--------|-------------------|------------------|
| Document prolog | `document {…}` | HEADER, ROOT_CONTENT_DESCRIPTION |
| Draw shapes | `drawCircle/Rect/Oval/Line/Arc/Sector/RoundRect/TextOnCircle` (DrawHelpers) | DRAW_CIRCLE, DRAW_RECT, DRAW_OVAL, DRAW_LINE, DRAW_ARC, DRAW_SECTOR, DRAW_ROUND_RECT, DRAW_TEXT_ON_CIRCLE *(corpus-absent)* |
| Paint | `paint{}` + 3 gradient builders | PAINT_VALUES |
| Text | `addText / drawTextRun / drawTextAnchored / drawTextOnPath / createTextFromFloat / textMeasure / textMerge / textLookup` | DATA_TEXT, DRAW_TEXT_RUN, DRAW_TEXT_ANCHOR, DRAW_TEXT_ON_PATH, TEXT_FROM_FLOAT, TEXT_MEASURE, TEXT_MERGE, TEXT_LOOKUP |
| Path | `pathCreate / pathAppend* / drawPath / drawTweenPath / addPathData` | PATH_CREATE, PATH_ADD, DATA_PATH, DRAW_PATH, DRAW_TWEEN_PATH |
| Bitmap | `addBitmap / drawBitmap / drawBitmapScaled / drawBitmapInt` *(last is corpus-absent)* | DATA_BITMAP, DRAW_BITMAP, DRAW_BITMAP_SCALED, DRAW_BITMAP_INT |
| Matrix | `matrixSave/Restore/Translate/Scale/Rotate/Skew` + `matrixSaved {…}` + REM-115 `matrixConstant / matrixExpression / matrixVectorMath` | MATRIX_SAVE, MATRIX_RESTORE, MATRIX_TRANSLATE, MATRIX_SCALE, MATRIX_ROTATE, MATRIX_SKEW, MATRIX_CONSTANT, MATRIX_EXPRESSION, MATRIX_VECTOR_MATH |
| Clip | `clipRect / clipPath` | CLIP_RECT, CLIP_PATH |
| Data | `addInt / addDataMapIds / dataMapLookup / dataMapEntry` (REM-139 extended) | DATA_INT, ID_MAP, DATA_MAP_LOOKUP |
| Float expr | `floatExpression(...)` + 25 op constants + 8 sys-var refs | ANIMATED_FLOAT |
| Int expr | `addIntegerExpression(mask, value)` (REM-119) | INTEGER_EXPRESSION |
| Color expr | 7 mode-specific builders + `addNamedVariable` | COLOR_EXPRESSIONS, NAMED_VARIABLE |
| Layout container | `box/boxLeaf/fitBox/column/row/collapsibleColumn/collapsibleRow/flow/state/canvas/root` | LAYOUT_BOX, LAYOUT_FIT_BOX, LAYOUT_COLUMN, LAYOUT_ROW, LAYOUT_COLLAPSIBLE_COLUMN, LAYOUT_COLLAPSIBLE_ROW, LAYOUT_FLOW, LAYOUT_STATE, LAYOUT_CANVAS, LAYOUT_ROOT + LAYOUT_CONTENT, LAYOUT_CANVAS_CONTENT, CONTAINER_END |
| Modifiers | 14 chain methods + `backgroundColorRef` + `borderColorRef` (REM-141/144) | 14 modifier ops + flags=2 variants of MODIFIER_BACKGROUND / MODIFIER_BORDER + TOUCH_EXPRESSION + DATA_FLOAT (auto-by-scroll) |
| Root behaviour | `setRootContentBehavior(...)` | ROOT_CONTENT_BEHAVIOR |

**Unique opcodes emittable: ~73–75.** Against the corpus-reachable set (~155), that's **~48%
by unique opcodes** — and **substantially higher by frequency-weighted corpus reach** (the
covered ops are the most-used ones; 173/173 corpus inflate-only smoke passes since REM-96).

## §4 — REM-113 walkthrough — the "spike-first" archetype

The 2026-06-28 audit ranked G2 advanced-draw shapes as Story #2:

> | DRAW_ROUND_RECT | 51 | gauge / shape demos | small | Rect + 4 corner radii. |
> | DRAW_TEXT_ON_CIRCLE | 57 | circular-text demos | small | `textId + cx + cy + r + startAngle`. |
> | DRAW_BITMAP_INT | 66 | `corpus/bitmap_int_*.rc` | small | Bitmap from device-local pixel int array. |

REM-113 picked it up and **empirically verified each via the visible-skip pattern + 173-doc
inflate probe**:

| Op | Audit claim | REM-113 empirical | Outcome |
|----|-------------|--------------------|---------|
| DRAW_ROUND_RECT (51) | "gauge / shape demos" | **corpus-PRESENT** (control-positive in visibility-check) | Triple-pin landed. |
| DRAW_TEXT_ON_CIRCLE (57) | "circular-text demos" | **corpus-ABSENT** (visible-skip required) | Helper still added (completeness), pinned as double-anchor + visible-skip. |
| DRAW_BITMAP_INT (66) | "`corpus/bitmap_int_*.rc`" | **corpus-ABSENT** (zero fixtures) | Helper added, double-anchor + visible-skip. |

**Methodology lesson:** REM-107 audit relied on filename heuristics; the empirical 173-doc
inflate probe is the truth-source. **2/3 audited-as-corpus-active ops were 0-corpus.** This
became the **REM-119 standard** (Visible-skip-Pattern, control-positive in every triple-pin),
which then propagated to REM-128/130/141/144 (Bug-#2 — `default == corpus-default` is an
assumption, not truth; properties-table empirically verified before byte-anchor).

**For the next Creation-DSL lane:** every gap in §5 below has a *claim* (corpus-reach guess
from filename / heuristic) and a *verify-step* (173-doc empirical probe). The verify-step is
non-negotiable before scope is committed.

## §5 — Remaining gaps (post-E6, ranked)

Audit's G1–G10 framework retained for traceability; each section is annotated with current
status. Ranking favours **corpus-reach × prereq-blocking × small-scope** so the next lane is
a tight win.

### G1 — Touch & event modifiers (HIGH-CORPUS, STILL OPEN)

| Opcode | Int | Corpus claim | Status |
|--------|-----|--------------|--------|
| MODIFIER_TOUCH_DOWN | 219 | `c_modifier_on_touch_down.rc` (corpus-active) | Op-class exists, **no creation helper** |
| MODIFIER_TOUCH_UP | 220 | `c_modifier_on_touch_up.rc` (corpus-active) | Op-class exists, **no creation helper** |
| MODIFIER_TOUCH_CANCEL | 225 | `c_modifier_on_touch_cancel.rc` (corpus-active) | Op-class exists, **no creation helper** |
| MODIFIER_OFFSET | 221 | misc layout fixtures | Audit-claim, needs spike. |
| MODIFIER_GRAPHICS_LAYER | 224 | complex demos | Audit-claim, needs spike. |
| MODIFIER_DIMENSION_CONSTRAINTS | 243 | `c_modifier_*` | Audit-claim, needs spike. |
| TOUCH_EXPRESSION (standalone) | 157 | `corpus/touch_*.rc` | Currently only emitted via scroll() group; needs standalone helper. |
| MODIFIER_MARQUEE | 228 | text-scroll | Audit-claim, needs spike. |
| MODIFIER_RIPPLE | 229 | touch-feedback | Audit-claim, needs spike. |
| MODIFIER_MULTI_CLICK | 83 | tap-counting | Audit-claim, needs spike. |

**Spike-first recommended order**: TOUCH_DOWN/UP/CANCEL (3 confirmed-corpus-active fixtures —
`c_modifier_on_touch_*.rc`); then probe the rest before scope commit.

### G2 — Advanced draw / path / shape (REM-113 partial)

| Opcode | Int | Status |
|--------|-----|--------|
| DRAW_ROUND_RECT | 51 | ✅ closed (REM-113, corpus-active) |
| DRAW_TEXT_ON_CIRCLE | 57 | ✅ helper added (REM-113); pinned **corpus-absent** |
| DRAW_BITMAP_INT | 66 | ✅ helper added (REM-113); pinned **corpus-absent** |
| PATH_COMBINE | 175 | OPEN. Audit: `path_demo_*.rc`. Spike needed. |
| PATH_TWEEN | 158 | OPEN. Audit: `path_demo_path_tween_demo.rc`. Spike needed. |
| PATH_EXPRESSION | 193 | **REM-127 producer-only TechSpec landed (70a73a7)** — creation surface still pending. Spike via REM-127 TechSpec. |
| DRAW_TO_BITMAP | 190 | OPEN. Audit: `texture_demo_*.rc`. Large effort. |

### G3 — Bitmap-font family (PARKED per NOTES.md)

| Op | Status |
|----|--------|
| DATA_BITMAP_FONT, DRAW_BITMAP_FONT_TEXT_RUN, DRAW_BITMAP_FONT_TEXT_RUN_ON_PATH, DRAW_BITMAP_TEXT_ANCHORED, BITMAP_TEXT_MEASURE | **PARKED** (REM-107-corrections #3: audit claimed `bitmap_font_watch.rc` + ~7 related; empirical 1/173 = 0.6% corpus, 3/5 ops 0-corpus). Low corpus reach — defer until the human re-prioritises. |

### G4 — Expression / lookup completion

| Opcode | Status |
|--------|--------|
| INTEGER_EXPRESSION | ✅ closed (REM-119) |
| TEXT_MERGE | ✅ closed (REM-119, 19/173 fixtures = 11%) |
| TEXT_LOOKUP | ✅ closed (REM-119) |
| TEXT_LOOKUP_INT, TEXT_SUBTEXT, TEXT_LENGTH | OPEN. REM-119 NOTES: 0-corpus per probe; PARKED. |
| TEXT_STYLE (242), CORE_TEXT (239), TEXT_TRANSFORM (199) | OPEN. ANDROIDX-overlay, multi-fixture corpus. **Spike candidate.** |
| MATRIX_CONSTANT, MATRIX_EXPRESSION, MATRIX_VECTOR_MATH | ✅ closed (REM-115) |
| MATRIX_FROM_PATH (181) | OPEN. Medium effort. |

### G5 — Animation / timing

| Opcode | Status |
|--------|--------|
| WAKE_IN (191) | OPEN. `wake_demo_*.rc` (ANDROIDX). Medium. |
| LOOP_START (215) | OPEN. Large effort (control flow). |
| SOUND_EXPRESSION (206), PLAY_SOUND (141), DATA_SOUND (169) | OPEN. EXPERIMENTAL. Large. |
| HAPTIC_FEEDBACK (177) | OPEN. `haptic_demo_*.rc`. Medium. |

### G6 / G7 / G8 — Macros, host-actions, attributes (PARKED, large)

Per audit §7 ("Out of immediate-next scope"). Macros (G6) ~1000 LOC; host-actions (G7) ~500 LOC
+ platform-seam contract; attributes (G8) ~250 LOC + host-mutation protocol. **No movement
since audit.** Stays human-flag.

### G9 — Particles / Impulse (IN-FLIGHT — REM-143)

Per develop tip `984fca7` git log — REM-143 TechSpec landed (`d818522`), dev-1/dev-2 are
prototyping. **Out of Dev-3 scope unless PO routes a slice.**

### G10 — Theming / metadata / lists

| Opcode | Status |
|--------|--------|
| THEME (63), COLOR_THEME (196), COLOR_CONSTANT (138) | OPEN, small. `color_theme.rc` / `color_list.rc`. |
| ID_LIST (146), ID_LOOKUP (192), FLOAT_LIST (147) | OPEN, small. |
| DYNAMIC_FLOAT_LIST (197), UPDATE_DYNAMIC_FLOAT_LIST (198) | ✅ Likely closed via REM-139 (LayoutCompute) — needs verify. |
| COMPONENT_VALUE (150), CLICK_AREA (64), DEBUG_MESSAGE (179), REM (185) | OPEN, small. |
| ACCESSIBILITY_SEMANTICS (250) | OPEN, large. |

## §6 — Recommended next lane (ranked)

Ordered by **corpus-reach × small-scope × prereq-clean**. Each lane is gated by a spike-first
empirical probe BEFORE scope commit (REM-113 lesson).

| # | Lane | Group | Effort | Why now |
|---|------|-------|--------|---------|
| 1 | **Touch event modifiers** (TOUCH_DOWN/UP/CANCEL + standalone TOUCH_EXPRESSION) | G1 | ~120 LOC (small) | 3 corpus-active fixtures already exist. Mirror MODIFIER_CLICK pattern (which closed in REM-96). REM-108 already wired the player-side touch eval; the creation-side gap is the bottleneck. |
| 2 | **G2 path completion** (PATH_COMBINE + PATH_TWEEN + PATH_EXPRESSION) | G2 | ~250 LOC (medium) | `path_demo_*.rc` corpus uses these. PATH_EXPRESSION ties into REM-127 producer-side TechSpec already landed. Coordinate with PathExpression render slice. |
| 3 | **Text-style + CoreText** (TEXT_STYLE + CORE_TEXT + TEXT_TRANSFORM) | G4 (text remainder) | ~200 LOC (medium) | Multiple `text_*.rc` corpus fixtures. Closes the text-shaping family. |
| 4 | **G10 theming/lists** (THEME + COLOR_CONSTANT + COLOR_THEME + ID_LIST + FLOAT_LIST + COMPONENT_VALUE) | G10 | ~250 LOC (small-medium) | `color_theme.rc` / `color_list.rc` / `c_state_layout.rc` corpus. Low individual reach but cumulative completeness. |
| 5 | **G1 touch tail** (TOUCH_OFFSET + GRAPHICS_LAYER + DIMENSION_CONSTRAINTS + MARQUEE + RIPPLE + MULTI_CLICK) | G1 | ~250 LOC (small-medium) | Pair with #1 once corpus-presence empirically confirmed for each. |

**Out of immediate-next scope** (no change from audit):
- G3 bitmap-font (parked — low corpus reach)
- G5 animation/sound (large + experimental)
- G6/G7/G8 macros/host-actions/attributes (large + protocol design)
- G9 particles (dev-1/dev-2 lane)

## §7 — Methodology checklist for the next lane

Every Creation-DSL lane post-REM-119 follows this **standard** (proven through REM-126 / E6
T1–T4):

1. **Spike-first 173-doc inflate probe** — verify each candidate op is corpus-active before
   scope commit. Use the existing `RcCorpus.readFixture` + `DocumentReader.inflateWithTrace`
   pattern (transient probe, delete before push).
2. **Decode each fixture empirically** — Bug-#2-Lehre: `default == corpus-default` is
   assumption. Properties-table verified before byte-anchor.
3. **Triple-Pin standard** (REM-96): DSL output ↔ hand-computed expected bytes ↔ corpus fixture
   region. `assertContentEquals` all three. Corpus-absent ops → double-pin + visible-skip
   marker in the test method name.
4. **Visible-skip-Pattern** (REM-119, REM-113 retrofitted): order-INDEPENDENT, MANDATORY for
   every Triple-Pin. Control-positive present-op MUST be found; expectedAbsent-Set explicit.
   Trust the full-inflate probe with control-positive, NOT a green unit-test display.
5. **§5.9 4-target compile gate** — jvm + iosSimulatorArm64 + wasmJs + androidMain +
   androidHostTest. Hard gate, not formality.
6. **§2-by-construction** — additive-only changes; existing pins must stay green; commonMain
   diffs reviewed for byte-impact.

## §8 — Hand-off

This census is doc-only — no code touches, no commitments. Next steps belong to PO:

- Pick a lane from §6's ranked list (or override based on human/mensch direction).
- Route to Dev-3 (or another developer) with the standard scope-draft → TechSpec → impl-slices
  cadence.
- Spike-first gate per §7 #1 — any lane's first slice is a probe + scope-confirm.

---

**Census produced 2026-06-29 against develop `984fca7`.** Re-issue after each Creation-DSL
merge to keep the gap list fresh.
