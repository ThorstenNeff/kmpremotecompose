# TECHSPEC — REM-128 S3 Compose-Creation Modifier-Suite (FINAL)

> **Status:** FINAL (assist-finalized from dev-3 draft `TECHSPEC-REM-128-S3-modifier-DRAFT.md`,
> commit `15d91f8`). Supersedes the DRAFT. Census + T4-claim + anchor fixtures decode-verified
> against the live tree; upstream `./androidx` read as behavior-reference (PROJECT_CONTEXT §5).
>
> **§2-safe by construction:** S3 lives entirely in `:creation-compose`; `:shared` commonMain is
> untouched → 173 byte-conformance intact. The `RemoteModifier` wrapper emits **nothing** — at
> render it drives the byte-proven REM-96 `LayoutModifier`/op path.

## 0. Leitprinzip — `RemoteModifier` wrapper on the byte-proven REM-96 surface
Same §0 lock as S1+S2: no second encoder. `RemoteModifier` holds a list of **structural modifier
elements**; at Phase-B render each element calls the existing byte-proven REM-96 modifier emission
(`WidthModifier`/`HeightModifier`/`BackgroundModifier` via the `LayoutModifier` path). The only
byte-producing site is the procedural-helper call, exactly as S2.

## 1. Census — decode-VERIFIED (assist, all 173)
T1 counts confirmed in-tree (match dev-3's census exactly): **WIDTH 149/173 (86%) · HEIGHT 147 (85%)
· BACKGROUND 93 (54%)**. Full distribution: PADDING 17 · SCROLL 11 · CLIP_RECT 9 · ROUNDED_CLIP_RECT 5
· BORDER 3 · VISIBILITY 3 · ALIGN_BY 3 · WIDTH_IN 2 · CLICK 2 · TOUCH_DOWN 2 · HEIGHT_IN/TOUCH_UP/
TOUCH_CANCEL/ZINDEX/COLLAPSIBLE_PRIORITY 1 each.
- **T1 (MVP):** width / height / background → **86% coverage with 3 modifiers.**
- **T2 (extension, S3b):** padding / scroll / clip_rect / rounded_clip_rect / border / visibility /
  align_by → ~95%.
- **T3 (long-tail, defer):** width_in / click / touch_* / height_in / zindex / collapsible_priority.
- **T4 (NEVER build — loud-guard):** MULTI_CLICK / OFFSET / GRAPHICS_LAYER / MARQUEE / RIPPLE /
  DRAW_CONTENT / DIMENSION_CONSTRAINTS. **assist decode-verified all 7 = 0/173** → the never-build +
  loud-reach-guard decision is §2-justified (no corpus consumer can trip the guard).

## 2. Open-question decisions (§5)

**Q2 — API resolution for the RemoteBox default → Resolution A (two composables).** This resolves the
S2 with-children byte-divergence I flagged. Mirror upstream's double signature 1:1:
- `RemoteBox(modifier, horizontal = POS_START, vertical = POS_TOP, content)` — **content form**, START/TOP
  default (= upstream `box(modifier,h,v,content)` @3537, REM-96 `box()`). Always routes to `box()`
  (LAYOUT_CONTENT + dual end), even for empty `content {}`.
- `RemoteBoxLeaf(modifier, horizontal = POS_CENTER, vertical = POS_CENTER)` — **leaf form**, no content
  param, CENTER default (= upstream `box(modifier)` no-content @4043/4061, REM-96 `boxLeaf()`). Always
  routes to `boxLeaf()` (single end, no LAYOUT_CONTENT).
- **Explicit, no children-detection magic.** This **supersedes S2's `RemoteBoxNode` render-time
  children-routing** — each composable maps 1:1 to its procedural helper, so the default is byte-correct
  for *both* forms (the S2 footgun is gone, not just documented). Reject B (POS_AUTO runtime-magic =
  implicit, hard-to-reason divergence surface) and C (status-quo = byte-divergence-as-default).
- **Refactor S2 anchor tests:** the childless cases (`c_box`, the inner boxes of `c_column`/`c_row`)
  switch `RemoteBox(...)` → `RemoteBoxLeaf(...)`. Low cost (S2 just merged; only dev-3's own test
  consumes the API). The S2 byte-pins must stay green after the refactor (regression gate).

**Q1 — Color handling → both overloads; `Int` is canonical.** `RemoteModifier.background(color: Int)`
is the byte-proven path (REM-96 takes ARGB `Int`). `.background(color: Color)` is additive convenience
= `color.toArgb()` → the `Int` path. **The §2 byte-anchor pins the `Int` path.** If the `Color` overload
ships, add a test proving `Color(0xFFFF0000).toArgb() == 0xFFFF0000` produces byte-identical output (guard
the sRGB/precision round-trip — no premultiply/colorspace surprise).

**Q3 — MVP scope → T1-only (S3a).** width/height/background (86%, 3 modifiers), census-driven like
REM-127. T2 = S3b extension-slice. Focused, clean 3-anchor triple-pin.

**Q4 — Modifier `equals`/`hashCode` → YES, via a structural element-list (NOT opaque lambdas).** This is
the one design lock that matters: `RemoteModifier` must hold a list of **data-carrying elements** (e.g.
`WidthElement(type, value)`, `BackgroundElement(argb)`) each with `equals`/`hashCode` **and** an
`apply(context)` — mirror upstream `Modifier.Element`. A lambda-only buffer cannot have structural
equality → it would break Compose `ComposeNode { update { set(modifier) } }` change-detection and paint
the design into a corner for S4-streaming (where recomposition stability is load-bearing). The element
also carries the apply path, so there's no lambda-vs-element tension — one representation does both.
(equals isn't strictly S3a-MVP-blocking under single-capture, but the **element-list representation is** —
locking it now avoids an S4 refactor.)

**Q5 — pin all 3 T1 fixtures separately.** width/height/background vs `c_modifier_width.rc` /
`c_modifier_height.rc` / `c_modifier_background.rc` (all decode-verified present) — separate anchors for
failure attribution even though width/height op-sequences are near-identical.

## 3. §2-Anker (Triple-Pin, Stage-2 = the gate)
MVP-T1 pin-set (3 tests), each `assertContentEquals(RcCorpus.readFixture("corpus/c_modifier_X.rc"),
produced)` — non-vacuous against the real oracle:
1. `stage2_width_matchesOracle` vs `c_modifier_width.rc` (128 B).
2. `stage2_height_matchesOracle` vs `c_modifier_height.rc` (128 B).
3. `stage2_background_matchesOracle` vs `c_modifier_background.rc` (247 B; Background + nested
   Column-in-Box → also exercises the S2 container walk).
Plus Stage-1 (Compose==procedural cross-surface) + Stage-3 (capture determinism), as S1/S2.
**Bug-#2 lesson pre-applied:** before each pin, empirically verify the oracle's `properties` table
(apiLevel/profile/contentDescription) — all 21 `c_modifier_*.rc` are apiLevel=7/PROFILE_ANDROIDX; do
**not** assume DSL defaults (the same contentDescription="" zero-length-prop trap as boxLeaf/S2).

## 4. Harte Regeln (review-enforced)
- **`:shared` commonMain untouched** → `git diff develop..BR -- 'shared/src/commonMain/**'` EMPTY.
  RemoteModifier lives in `:creation-compose` commonMain (CMP deps OK, java-free).
- **No second encoder** — every modifier element's `apply(context)` calls the existing REM-96
  `LayoutModifier` method / `context.add(<Modifier>Op(...))`; no new path to the buffer.
- **No wire/equals/hashCode touch** on existing `:shared` modifier ops → 173 + all existing tests
  unchanged (additive-only).
- **Path B (CMP-Modifier bridge) banned** (silent-drop ambiguity = the §2-killing divergence).
- **T4 = loud-reach-guard, never build** (decode-verified 0-corpus); same for any unrecognized modifier.
- **§5.9 4-target compile gate:** jvm + iosSimulatorArm64 + wasmJs + androidMain.

## 5. Explicitly NOT in scope
- T3 modifiers (defer, own ticket — REM-119-long-tail pattern).
- T4 modifiers (never — 0 corpus, no render path).
- CMP-Modifier bridge (Path B, banned).
- CustomModifier factory / user extension points.
- S4 streaming (own epic) — but the element-list design (Q4) keeps the door open.

## 6. Verification (the "done" bar)
- Triple-Pin MVP-T1 green 3/3 (Stage-2 vs the 3 `c_modifier_*` oracles is the gate) + Stage-1/3.
- S1 + S2 triple-pins **stay green** after the Resolution-A refactor (regression gate).
- 173/173 conformance + commonMain diff EMPTY (§2 by-construction).
- §5.9 4-target compile clean.

## 7. Impl slicing (for dev-3)
- **S3a (MVP, closes §0 T1):** `RemoteModifier` (structural element-list) + width/height/background
  elements + Resolution-A `RemoteBox`/`RemoteBoxLeaf` split + RemoteColumn/Row modifier-param refactor
  + refactor S2 anchor tests (childless → `RemoteBoxLeaf`, stay green) + Triple-Pin vs 3 `c_modifier_*`.
- **S3b (extended, T2):** padding/scroll/clip_rect/rounded_clip_rect/border/visibility/align_by → ~95%.
- **S3c (T3 long-tail):** own low-prio ticket.

## 8. assist verdict
**GO on the design.** Census + T4-0-corpus + anchor fixtures decode-verified (no faith). Path-A
(`RemoteBox`/`RemoteBoxLeaf` split) is the right §4 call — it makes the default byte-correct for both
container forms (resolving the S2 footgun I flagged, not just documenting it) and is the §0-faithful
mirror of the procedural box/boxLeaf distinction; it supersedes S2's children-detection routing and
requires a low-cost S2-test refactor (which must stay green = regression gate). The one design lock
beyond the draft: **RemoteModifier as a structural element-list, not opaque lambdas** (Q4) — gives
structural equality + the apply path in one representation and avoids an S4 corner. T1-only MVP,
Int-canonical color (Color convenience byte-verified), 3 separate anchors, T4 loud-guard. §2 holds by
construction (commonMain empty). MVP ungated — S3a can start.
