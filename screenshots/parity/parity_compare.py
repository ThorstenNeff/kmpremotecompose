#!/usr/bin/env python3
"""parity_compare — Android-Golden <-> iOS-Render perzeptueller Pixel-Diff (test-2 Stufe-B-Harness).

Vertrag (render-parity-tolerance.md §4b/§5): zwei Bilder gelten als parität, wenn die Cross-Skia-
Divergenz im Toleranzband bleibt. Graduiertes Verdikt (PO-Call 2026-06-26):

  PASS   geometrie-clean: breachFrac <= GEOM_BUDGET  AND  maxClusterFrac <= CLUSTER_GUARD
  TEXT   diffuse Font-/AA-Divergenz (erwartet, Android↔iOS verschiedene Text-Renderer): nicht clean,
         aber **diffus** (Breach gestreut, kein kompakter Cluster) und breachFrac <= TEXT_BUDGET
         → known-text-divergence, Heatmap, akzeptabel-pending-Review (kein FAIL).
  FAIL   strukturell: konzentrierter Breach-Cluster (verschoben/fehlend/falsch-farbig) ODER zu viel Breach.

Diffus vs strukturell **selbst-klassifiziert** über die Cluster-Konzentration (maxCluster/nBreach):
diffuse Font-AA streut über viele Glyph-Kanten (kleiner größter Cluster); ein struktureller Defekt
konzentriert sich in einem Block. → kein Doc-Listen-Pflege nötig.

  PER_PIXEL_DELTA  Kanal-Delta-Schwelle (AA/ε darunter = "gleich")
  GEOM_BUDGET      Frame-Budget clean-Geometrie (Cross-Skia-AA)
  TEXT_BUDGET      Frame-Budget diffuse Text/Font (breiter)
  CLUSTER_GUARD    max kompakter Cluster für clean-PASS
  DIFFUSE_RATIO    maxCluster/nBreach-Schwelle: darunter = diffus (Text/AA), darüber = strukturell

Density-Handling (PO-Call Option b): iOS-Render wird auf Android-Golden-Größe **resized** (Δ≤2px =
reines dp-Density-Rounding iOS-Dichte 3 vs Android 2.625 — Host-Canvas-Artefakt, kein Content-Bug;
Resize-within-±2px ist legitime Behandlung). Größerer Diff wird auch resized aber geflaggt; >2.25x = ERROR.

Usage:  parity_compare.py <android.png> <ios.png> <doc> [--diff-out <dir>]
Exit:   0 = PASS|TEXT, 1 = FAIL, 2 = ERROR
"""
import sys
from collections import deque
from PIL import Image

PER_PIXEL_DELTA = 8       # max Kanal-Delta, darunter = "gleich"
GEOM_BUDGET     = 0.020   # 2.0% clean-Geometrie
TEXT_BUDGET     = 0.150   # 15% diffuse Text/Font (breiter, Startwert — am 1. Text-Batch kalibrieren)
CLUSTER_GUARD   = 0.005   # 0.5% max kompakter Cluster für clean-PASS
DIFFUSE_RATIO   = 0.30    # maxClusterPx/nBreach < 0.30 = diffus (Text/AA), >= = strukturell


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
        note = (" (±2px-density-resize)" if abs(dw) <= 2 and abs(dh) <= 2
                else f" (resized {ratio:.2f}x — GRÖSSER als ±2px, prüfen)")
    w, h = a.size
    total = w * h
    pa = a.load(); pi = i.load()
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
    concentration = (max_cluster / nbreach) if nbreach else 0.0

    if breach_frac <= GEOM_BUDGET and cluster_frac <= CLUSTER_GUARD:
        verdict = "PASS"
    elif concentration < DIFFUSE_RATIO and breach_frac <= TEXT_BUDGET:
        verdict = "TEXT"          # diffuse Font/AA-Divergenz, akzeptabel-pending-Review
    else:
        verdict = "FAIL"          # strukturell (konzentriert) oder zu viel Breach

    if verdict in ("TEXT", "FAIL"):
        if diff_out:
            heat = Image.new("RGB", (w, h), (0, 0, 0))
            ph = heat.load()
            for p in range(total):
                if breach[p]:
                    ph[p % w, p // w] = (255, 0, 0)
            outp = f"{diff_out.rstrip('/')}/diff_{doc}.png"
            heat.save(outp)
            verdict += f" diff={outp}"
    print(f"PARITY | {doc} | {w}x{h}{note} | breachFrac={breach_frac:.4f} "
          f"maxClusterFrac={cluster_frac:.4f} conc={concentration:.2f} | verdict={verdict}")
    return verdict.split()[0]


if __name__ == "__main__":
    if len(sys.argv) < 4:
        print("usage: parity_compare.py <android.png> <ios.png> <doc> [--diff-out <dir>]")
        sys.exit(2)
    diff = sys.argv[sys.argv.index("--diff-out") + 1] if "--diff-out" in sys.argv else None
    v = compare(sys.argv[1], sys.argv[2], sys.argv[3], diff)
    sys.exit({"PASS": 0, "TEXT": 0, "FAIL": 1, "ERROR": 2}.get(v, 2))
