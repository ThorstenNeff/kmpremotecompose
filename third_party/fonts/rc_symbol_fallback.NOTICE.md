# `rc_symbol_fallback.ttf` — provenance & license

**Bundled at:** `shared/src/commonMain/composeResources/font/rc_symbol_fallback.ttf` (≈2.6 KB)
**Used by:** REM-110 — symbol-glyph fallback in `ComposeTextRenderer` (the CMP default **web** font
lacks these glyphs → tofu on wasm; Android/iOS fall back via the system font).

## Source

A **subset + merge** of two upstream Noto fonts (both **SIL Open Font License 1.1**, no Reserved Font
Name — subsetting and renaming are permitted):

- **Noto Sans Symbols 2** (`NotoSansSymbols2-Regular`, v2.008) — glyphs `U+2022 •`, `U+25B2 ▲`,
  `U+2665 ♥`, `U+26A1 ⚡`, `U+2764 ❤`, `U+2B29 ⬩`
- **Noto Sans Symbols** (`NotoSansSymbols-Regular`) — glyphs `U+2191 ↑`, `U+2193 ↓`

The included codepoints are exactly the non-ASCII glyphs found in genuine `DATA_TEXT` strings across the
173-doc conformance corpus that the default web font does not cover (the Latin-1 glyphs `° ² ·` and the
bullet `•` are left to the default font; `•` is carried here only as a safety net and does not trigger
the per-run fallback). Census is pinned by `Rem110SymbolFallbackTest`.

## Recipe (reproducible with `fonttools`)

```sh
pyftsubset NotoSansSymbols2-Regular.ttf --unicodes=2022,25B2,2665,26A1,2764,2B29 \
    --name-IDs='*' --no-hinting --desubroutinize --layout-features='' --glyph-names --output-file=sub2.ttf
pyftsubset NotoSansSymbols-Regular.ttf  --unicodes=2191,2193 \
    --name-IDs='*' --no-hinting --desubroutinize --layout-features='' --glyph-names --output-file=sub1.ttf
pyftmerge sub2.ttf sub1.ttf   # -> merged.ttf == rc_symbol_fallback.ttf
```

`--name-IDs='*'` preserves the Noto copyright + OFL notice in the font's `name` table (name IDs 0/13/14).

## License

SIL Open Font License, Version 1.1 — full text in [`OFL.txt`](./OFL.txt). Copyright 2022 The Noto
Project Authors (https://github.com/notofonts/symbols).

> **Release-packaging note (flagged to PO):** the OFL requires the license text to accompany the font
> in distribution. The `.ttf` already embeds the OFL reference in its `name` table; for shipped app
> bundles, confirm `OFL.txt` is included in the distributable (or referenced from an in-app licenses
> screen). This is a release concern, not a build concern.
