# REM-134 — Source-B Span-Oracle (dev-2 → test-3)

Erwartete Render-Soll-Werte für `attribute_string.rc` **gegeben unsere Option-C-Impl**. Daten-Orakel-Gate
(TECHSPEC §6): **≥24 Text-Draws** (war 0 = poisoned-blank), je an Soll-Baseline, Row-baseline-aligned.
**Anti-Anchoring:** dieses Soll (Source B) VOR dem Render (Source A) lesen. dispatch≠visual gilt hart.

## Mechanik-Befund (jvmTest, deterministische Fake-Metriken — `Rem134ComponentContentTest`)
Mit no-op `getTextBounds` in jvmTest sind **echte Pixel-Positionen NICHT** prüfbar (Metriken=0) → die
Layout-MATHEMATIK ist mit injizierten deterministischen Metriken verifiziert (x-Advance, Baseline-Align,
Row-Stacking). **Die echten Font-Pixel-Positionen sind test-3s Desktop-Orakel.**

## Soll-Struktur: 24 Spans in 6 visuellen Zeilen (Column von Rows), top→down
```
Zeile 1: "AttributedString Demo:"
Zeile 2: "This is " · "Bold"(bold) · ", this is " · "Italic"(italic) · "."
Zeile 3: "This text is " · "Red"(rot #FFFF0000) · ", this is " · "Blue"(blau #FF0000FF) · ","
Zeile 4: " and this has a " · "Yellow Background"(gelber BG-Rect dahinter) · "."
Zeile 5: "This is " · "Underlined"(+Underline-DrawLine) · ", and this has a " · "Strikethrough"(+Strike-DrawLine) · "."
Zeile 6: "This is " · "Big"(fontSize 92) · ", and this is Superscript" · "²" · "."
```

## Daten-Orakel-Erwartungen (test-3 verifiziert mit echten Metriken)
1. **≥24 `drawTextRun`/`drawComplexText`** total (genau 24 Spans), Text-Inhalt + Reihenfolge wie oben.
2. **Pro Zeile: x-monoton steigend** (Spans nebeneinander, kein Überlapp; jeder Span startet rechts vom vorigen).
3. **Pro Zeile: EINE gemeinsame Baseline** (AlignBy line=NaN). **Zeile 6 ist der harte Fall:** "Big"(92px)
   + die 46px-Spans teilen sich **eine** Baseline (kleinere Spans nach unten geschoben, NICHT top-aligned).
4. **Zeilen stapeln vertikal** top→down (Row-Baselines streng steigend).
5. **z-order:** Zeile-4-"Yellow Background"-Text liegt SICHTBAR ÜBER dem gelben BG-Rect (der decode-bewiesene
   Pure-B-Z-Order-Bug — Text an DrawContent-Position[nach BG], nicht an TextLayout-Deklaration[vor BG]).
6. **Underline/Strike:** 2 `drawLine`s (Zeile 5) an den jetzt-gemessenen Span-Bounds (ComponentValue löst
   Breite/Höhe auf — vorher 0).

## 🔴 Golden-Poisoning (REM-123-Gate — PFLICHT)
`attribute_string`-Goldens (alle 3 Targets) sind **blank-vergiftet** (desktop 500×500/1702 B/weiß bestätigt).
**Re-Baseline ERST nach Fix + NUR via diesem Daten-Orakel**, kein Cross-Target-Self-Compare. Re-Baseline-
Owner: **test-3**. Bis dahin Golden = known-poisoned (nicht als Pass werten).

## Caveat
Doc-space, vor per-Komponenten-Matrix. Vollständige first/last-x je Span liefere ich als CSV on-demand,
sobald test-3 mit echten Metriken eine Disambiguierung braucht (Muster REM-127).
