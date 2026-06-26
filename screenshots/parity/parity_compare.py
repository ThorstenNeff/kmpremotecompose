#!/usr/bin/env python3
"""parity_compare — Android-Golden <-> iOS-Render perzeptueller Pixel-Diff (test-2 Stufe-B-Harness).

Vertrag (render-parity-tolerance.md §4b/§5): zwei Bilder gelten als parität, wenn die Cross-Skia-
Divergenz (AA/Sub-Pixel/ε-Farbe) im Toleranzband bleibt UND kein kompakter Struktur-Cluster auftritt.

  PASS  <=>  breachFrac <= FRAME_BUDGET  AND  maxClusterFrac <= MAX_CLUSTER_FRAC

- breach: ein Pixel, dessen max. Kanal-Delta > PER_PIXEL_DELTA (AA/ε darunter = "gleich").
- breachFrac: Anteil breach-Pixel (Cross-Skia-AA-Budget).
- maxClusterFrac: größter zusammenhängender breach-Cluster / Fläche (Struktur-Wächter: ein
  verschobener/fehlender Op = kompakter Cluster [rot]; AA-Rauschen = diffus [grün]).

Startwerte empirisch kalibrieren am ersten echten Lauf (NICHT als final behandeln).

Usage:  parity_compare.py <android.png> <ios.png> <doc-name> [--diff-out <dir>]
Exit:   0 = PASS, 1 = FAIL, 2 = error (size mismatch nach Resize / Ladefehler)
Report: eine maschinen-parsebare Zeile auf stdout (wie der P2-Sweep-Dump).
"""
import sys
from collections import deque
from PIL import Image

PER_PIXEL_DELTA  = 8       # max Kanal-Delta, darunter = "gleich" (AA/ε-Farbe)
FRAME_BUDGET     = 0.020   # max Anteil breach-Pixel (2.0%)
MAX_CLUSTER_FRAC = 0.005   # max zusammenhängender breach-Cluster (0.5% Fläche)


def load_rgb(path):
    return Image.open(path).convert("RGB")


def compare(android_path, ios_path, doc, diff_out=None):
    a = load_rgb(android_path)
    i = load_rgb(ios_path)
    resized = ""
    if a.size != i.size:
        # Cross-Density (Android-Emulator vs iOS-Sim): auf gemeinsame Größe normalisieren, dann
        # perzeptuell vergleichen. Großer Größenunterschied (>1.5x) = echter Layout/Density-Bug.
        ratio = (a.size[0] * a.size[1]) / max(1, i.size[0] * i.size[1])
        if ratio > 2.25 or ratio < 0.444:
            print(f"PARITY | {doc} | a={a.size} i={i.size} | verdict=ERROR reason=size-mismatch({ratio:.2f}x)")
            return 2
        i = i.resize(a.size, Image.LANCZOS)
        resized = f" (ios resized {ratio:.2f}x)"
    w, h = a.size
    total = w * h
    pa = a.load(); pi = i.load()
    breach = bytearray(total)  # 1 = breach
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
    # größter zusammenhängender breach-Cluster (4-Nachbarschaft, iterativer Flood-Fill)
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
                    for nx, ny in ((px-1,py),(px+1,py),(px,py-1),(px,py+1)):
                        if 0 <= nx < w and 0 <= ny < h:
                            q = ny * w + nx
                            if breach[q] and not seen[q]:
                                seen[q] = 1; dq.append(q)
                if size > max_cluster:
                    max_cluster = size
    cluster_frac = max_cluster / total
    ok = breach_frac <= FRAME_BUDGET and cluster_frac <= MAX_CLUSTER_FRAC
    verdict = "PASS" if ok else "FAIL"
    if not ok and diff_out:
        heat = Image.new("RGB", (w, h), (0, 0, 0))
        ph = heat.load()
        for p in range(total):
            if breach[p]:
                ph[p % w, p // w] = (255, 0, 0)
        outp = f"{diff_out.rstrip('/')}/diff_{doc}.png"
        heat.save(outp)
        verdict += f" diff={outp}"
    print(f"PARITY | {doc} | {w}x{h}{resized} | breachFrac={breach_frac:.4f} "
          f"maxClusterFrac={cluster_frac:.4f} ({max_cluster}px) | verdict={verdict}")
    return 0 if ok else 1


if __name__ == "__main__":
    if len(sys.argv) < 4:
        print("usage: parity_compare.py <android.png> <ios.png> <doc> [--diff-out <dir>]")
        sys.exit(2)
    diff = None
    if "--diff-out" in sys.argv:
        diff = sys.argv[sys.argv.index("--diff-out") + 1]
    sys.exit(compare(sys.argv[1], sys.argv[2], sys.argv[3], diff))
