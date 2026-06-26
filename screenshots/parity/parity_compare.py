#!/usr/bin/env python3
"""parity_compare — Android-Golden <-> iOS-Render perzeptueller Pixel-Diff (test-2 Stufe-B-Harness).

Verdikt (PO-Call 2026-06-26, verfeinert nach 1. Voll-Sweep):
  BLANK  Android-Golden nahezu uniform + niedriger Breach → beide Plattformen blank: Parität trivial,
         aber NICHT gerendert (kein Render-Beweis; aus der Render-Parität ausgeschlossen).
  PASS   clean gerenderte Übereinstimmung: breachFrac<=GEOM_BUDGET AND maxClusterFrac<=CLUSTER_GUARD.
  FAIL   echter struktureller Defekt: SOLIDER Breach-Block (maxClusterFrac>STRUCT_CLUSTER) ODER zu viel
         diffuse Divergenz (breach>TEXT_BUDGET).
  TEXT   diffus / thin-line (Font-AA, Gridlines, Shape-Outlines, 1px-Linien-Offset): kleiner Cluster
         (kein solider Block) UND breach<=TEXT_BUDGET → erwartete Cross-Skia-Divergenz, Heatmap.

**Struktur-Diskriminator = Cluster-FLÄCHE (maxClusterFrac), nicht Konnektivität:** ein solider
divergenter Block hat große Fläche; thin-lines/Gridlines/Glyph-Kanten sind verbunden-aber-dünn →
kleine Fläche → TEXT. (1. Sweep: anchored_text/moon_phases = 1px-Linien → TEXT; color/thumb_wheel2/
bit_draw2 = solide Blöcke → FAIL.)

Density (PO Option b): iOS->Android resize, <=2px = dp-Rounding silent, größer geflaggt, >2.25x ERROR.
Usage: parity_compare.py <android.png> <ios.png> <doc> [--diff-out <dir>]
"""
import sys
from collections import deque, Counter
from PIL import Image

PER_PIXEL_DELTA = 8       # Kanal-Delta-Schwelle (AA/ε darunter = "gleich")
GEOM_BUDGET     = 0.020   # clean-Geometrie Frame-Budget
TEXT_BUDGET     = 0.200   # diffuse/thin Budget (linien-/AA-schwere Docs)
CLUSTER_GUARD   = 0.005   # max kompakter Cluster für clean-PASS
STRUCT_CLUSTER  = 0.050   # maxClusterFrac > 5% = SOLIDER struktureller Block = echter Defekt
BLANK_MODAL     = 0.995   # Android-Golden >99.5% eine Farbe = blank/nicht-gerendert

# Produkt-Call-Override (PO 2026-06-26): Docs, die der Area-Diskriminator als FAIL flaggt, aber
# nachweislich TEXT-Klasse sind (großer Bold-Text → Glyph-Block >5% Fläche, font-metrik-bedingt,
# kein Geometrie-Defekt). Transparent als "TEXT(override)" gelabelt. PROPER FIX = glyph-edge-density-
# Guard (Cluster-Fill-Ratio: solide Blöcke füllen ihre Bbox, Text nicht) — Follow-up, nicht-dringend.
TEXT_PRODUCT_OVERRIDE = {"c_fit_box"}


def load_rgb(p):
    return Image.open(p).convert("RGB")


def compare(android_path, ios_path, doc, diff_out=None):
    a = load_rgb(android_path)
    i = load_rgb(ios_path)
    note = ""
    if a.size != i.size:
        dw, dh = i.size[0] - a.size[0], i.size[1] - a.size[1]
        ratio = (a.size[0] * a.size[1]) / max(1, i.size[0] * i.size[1])
        if ratio > 2.25 or ratio < 0.444:
            print(f"PARITY | {doc} | a={a.size} i={i.size} | verdict=ERROR reason=size-mismatch({ratio:.2f}x)")
            return "ERROR"
        i = i.resize(a.size, Image.LANCZOS)
        note = (" (±2px-density)" if abs(dw) <= 2 and abs(dh) <= 2
                else f" (resized {ratio:.2f}x>±2px PRÜFEN)")
    w, h = a.size
    total = w * h
    pa = a.load(); pi = i.load()
    cnt = Counter(pa[x, y] for y in range(0, h, 3) for x in range(0, w, 3))
    android_blank = cnt.most_common(1)[0][1] / max(1, sum(cnt.values())) > BLANK_MODAL
    breach = bytearray(total)
    nbreach = 0
    for y in range(h):
        row = y * w
        for x in range(w):
            ra, ga, ba = pa[x, y]
            ri, gi, bi = pi[x, y]
            if max(abs(ra - ri), abs(ga - gi), abs(ba - bi)) > PER_PIXEL_DELTA:
                breach[row + x] = 1
                nbreach += 1
    breach_frac = nbreach / total
    max_cluster = 0
    if nbreach:
        seen = bytearray(total)
        for s in range(total):
            if breach[s] and not seen[s]:
                size = 0
                dq = deque([s]); seen[s] = 1
                while dq:
                    p = dq.popleft(); size += 1
                    px, py = p % w, p // w
                    for nx, ny in ((px-1, py), (px+1, py), (px, py-1), (px, py+1)):
                        if 0 <= nx < w and 0 <= ny < h:
                            q = ny * w + nx
                            if breach[q] and not seen[q]:
                                seen[q] = 1; dq.append(q)
                if size > max_cluster:
                    max_cluster = size
    cluster_frac = max_cluster / total

    if android_blank and breach_frac <= GEOM_BUDGET:
        verdict = "BLANK"
    elif breach_frac <= GEOM_BUDGET and cluster_frac <= CLUSTER_GUARD:
        verdict = "PASS"
    elif cluster_frac > STRUCT_CLUSTER:
        verdict = "FAIL"
    elif breach_frac <= TEXT_BUDGET:
        verdict = "TEXT"
    else:
        verdict = "FAIL"

    override = verdict == "FAIL" and doc in TEXT_PRODUCT_OVERRIDE
    if override:
        verdict = "TEXT"

    label = verdict + ("(override:font-metric)" if override else "")
    if verdict in ("TEXT", "FAIL", "BLANK") and diff_out:
        heat = Image.new("RGB", (w, h), (0, 0, 0)); ph = heat.load()
        for p in range(total):
            if breach[p]:
                ph[p % w, p // w] = (255, 0, 0)
        outp = f"{diff_out.rstrip('/')}/diff_{doc}.png"; heat.save(outp)
        label += f" diff={outp}"
    print(f"PARITY | {doc} | {w}x{h}{note} | breachFrac={breach_frac:.4f} "
          f"maxClusterFrac={cluster_frac:.4f}{' android-blank' if android_blank else ''} | verdict={label}")
    return verdict


if __name__ == "__main__":
    if len(sys.argv) < 4:
        print("usage: parity_compare.py <android.png> <ios.png> <doc> [--diff-out <dir>]")
        sys.exit(2)
    diff = sys.argv[sys.argv.index("--diff-out") + 1] if "--diff-out" in sys.argv else None
    v = compare(sys.argv[1], sys.argv[2], sys.argv[3], diff)
    sys.exit({"PASS": 0, "TEXT": 0, "BLANK": 0, "FAIL": 1, "ERROR": 2}.get(v, 2))
