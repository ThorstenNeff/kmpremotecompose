# Render-Golden Promote-Gate — Upstream-Player-Oracle für dynamische Kurven-Docs

> **Status:** Working-Rule (PO-verankert im STATUS, bestätigt 2026-06-28 via Discord
> `1520876838862196909`). **Nicht** in PROJECT_CONTEXT §6 hochgezogen — der Schritt zur
> Invariante geht über den Menschen (PROJECT_CONTEXT §6 = Invarianten-Änderung).
> **Aktivierung:** ab REM-121-Fix verbindlich. **Geltung:** team-weit (assist, test-1,
> test-2, test-3) für jeden Golden-Promote im Geltungsbereich, nicht plattform-spezifisch.

> **Verhältnis zu PROJECT_CONTEXT §6:** §6 fordert das **Upstream-JVM = Orakel**-Prinzip
> auf der **Byte-Ebene** (jedes `.rc` muss byte-identisch zur JVM-Referenz sein). Diese
> Working-Rule erweitert das gleiche Prinzip auf die **Render-Golden-Ebene** für eine
> klar abgegrenzte Klasse von Docs. Die Erweiterung ist konservativ: §6 bleibt unverändert
> als alles-überschreibende Invariante; die Render-Erweiterung wird empirisch erprobt und
> erst bei Bewährung dem Menschen zur §6-Anhebung vorgeschlagen.

---

## 1. Warum die Regel existiert

In-repo-Cross-Target-Sweeps (Desktop ↔ Android ↔ iOS, drawCount > 0, attribuierbar,
RENDERS-FULL auf allen N Targets) sind **nicht hinreichend** als Promote-Gate für jede
Klasse von Render-Goldens. Sie fangen Bugs nur dann, wenn die Targets **divergieren**.
**Cross-Platform-Bugs, die auf allen Targets identisch falsch rendern, schlüpfen durch.**

**Vorfall, der die Regel erzwingt** (2026-06-28, gemeldet von PO Discord
`1520876392169078794`):
- REM-117 Desktop-Golden-Baseline-Refresh promoted (develop-Tip `bbf1f00` zur Promote-Zeit,
  Branch `feature/REM-117-desktop-baseline-refresh-bbf1f00`, Commit `8e8013d`, gemergt
  als `14dd184`).
- Sweep grün: 0 Regressionen, 0 surfaceW/H-Drift, 18/18 Δs attribuiert,
  RENDERS + draws>0 auf allen Targets.
- **Trotzdem:** Das frisch eingebackene Desktop-Golden für `heart_rate` ist
  **strukturell falsch** — eine Flachlinie statt der echten Kurve.
- Ursache (dev-2 / REM-121): `PathAppend` in einer Loop akkumuliert rohe `var`-Refs →
  80 identische Punkte. Bug rendert auf **allen** Targets identisch falsch →
  Cross-Target-Self-Compare blind.
- Lehre: `RENDERS + draws>0 + attribuierbar` ≠ visuell-korrekt; Cross-Target-Diff
  geht blind, wenn der Bug cross-platform ist.

---

## 2. Geltungsbereich — wann der Gate greift

Die Working-Rule gilt für `.rc`-Docs mit **dynamischer / akkumulierender Kurven-
Generierung**. Klassifikation kommt aus dem strukturellen Scan durch assist; die folgende
Liste ist exemplarisch und wird mit jedem neuen Befund erweitert:

- **Loop + `PathAppend`** (jeder Pfad-Build innerhalb einer Loop, der `var`-Refs oder
  computed Coords akkumuliert).
- **`PathArc` innerhalb einer Loop** (jeder akkumulierende Arc/Quad/Cubic-Build).
- **Allgemein: jede Op, die im Loop-Body Path-State akkumuliert** (sukzessive
  `moveTo`/`lineTo`/`quadTo`/`cubicTo` über `var`-Iteration).

**Nicht-betroffen** (in-repo-Cross-Target-Sweep bleibt ausreichender Gate):
- Statisch gemalte Docs (keine Loop-getriebene Path-Akkumulation).
- Docs mit deferred-tags-Coverage (Tag-basierter Sweep fängt Pfad-Klassen-Drift).
- Time-driven Docs ohne Loop+PathAppend (3-Sweep-cross-time bleibt der Gate für Zeit).

**Authoritative Quarantäne-Liste:** der strukturelle Scan durch assist. **Nicht raten** —
Quarantäne nur, wenn assist das Doc als Loop+PathAppend-Kandidat (oder Äquivalent)
markiert hat. Routing geht über den PO (Hub-and-Spoke, PROJECT_CONTEXT §7).

---

## 3. Der Gate — 4-Schritt-Re-Verify gegen Upstream-Player

Für jedes Doc im Geltungsbereich (vor Promote oder Re-Promote nach Fix), auf jedem
Target, das einen Golden hält (Android, iOS, Desktop, später Web):

**Schritt 1 — Upstream-JVM-Referenz-Frame erzeugen.**
Den Upstream-`remote-creation-core` + `remote-player-core` (oder den entsprechenden
JVM-Player aus `./androidx/compose/remote/`) nutzen, um das Referenz-Frame des Docs
zu erzeugen. **Dieses Frame ist das Orakel** — nicht das in-repo-PNG, das wir gerade
promoten wollen, und auch nicht ein KMP-Render eines anderen Targets.

**Schritt 2 — KMP-Render gegen Upstream-Frame diffen.**
Den (gefixten) KMP-Render durch `parity_compare.py` o.ä. gegen das Upstream-Frame
fahren. Identische Tolerance-Klasse wie der bestehende Cross-Plat-Vergleich
(Stufe B: Δ≤8/255, ≤2.0 % Pixel, ≤0.5 % Cluster — Werte aus dem etablierten
Cross-Plat-Sweep, ggf. Klasse anpassen bei AA-empfindlichen Docs).

**Schritt 3 — Erst bei grünem Upstream-Match: Golden einbacken.**
Wenn (und nur wenn) Schritt 2 PASS verdiktet, das KMP-Render als neues Golden in
`screenshots/reference/<platform>/<doc>.png` einbacken und das bisherige Golden
überschreiben. **PASS auf in-repo-Self-Compare ≠ PASS auf Upstream-Match** —
nicht durchwinken.

**Schritt 4 — Cross-Density-Sweep-Re-Run zur Absicherung.**
Cross-Density-Sweep auf einem Target fahren (`density=1.0` + `density=3.0` über
das betroffene Doc-Subset, billig + sharp). Bestätigt, dass das neu eingebackene
Golden density-invariant ist und keine versteckte density-getriebene Klasse mehr
offen ist. Methode aus dem REM-93-Cross-Density-Vorgang (`--density <float>`
CLI in der Desktop-Sweep-Harness).

---

## 4. Quarantäne-Handling vor Aktivierung

Bis ein Doc im Geltungsbereich durch Schritte 1–4 gelaufen ist, gilt das in-repo-Golden
als **suspect** und darf **nicht** als Referenz für andere Sweeps/Promotes verwendet
werden. Quarantäne sauber markieren (Listing in der Status-Doku des verantwortlichen
Test-Lanes, evtl. CSV-Spalte `promote_gate=PENDING_UPSTREAM`), damit kein nachfolgender
Sweep versehentlich den suspect-Golden propagiert.

**Granularität:** Quarantäne gilt **per Doc**, nicht per ganzem Promote-Batch. Docs
außerhalb des Geltungsbereichs (statisch, kein Loop+PathAppend) bleiben trustworthy.
REM-117 als Ganzes ist nicht zurückzurollen — nur die Loop+PathAppend-Subset.

---

## 5. Verhältnis zu anderen Methodologien

- **PROJECT_CONTEXT §6 — Upstream-JVM = Orakel (Byte-Ebene):** unverändert. Diese Regel
  ist die Render-Ebenen-Erweiterung desselben Prinzips, aber **eingeschränkt** auf die
  Loop+PathAppend-Klasse, nicht universell.
- **Cross-Density-Sweep auf einem Target:** bleibt der Gate für density-getriebene
  Klassen (REM-93-Vorgang); Schritt 4 oben ruft ihn als Absicherung auf, ersetzt
  ihn nicht.
- **Pre/Post-Differential auf gleicher Base:** unverändert; greift bei file-disjunkten
  Fixes (wie REM-93). Hier nicht direkt anwendbar, weil REM-121 den Render-Pfad selbst
  berührt und damit nicht file-disjunkt zum Golden-Build ist.
- **3-Sweep-cross-time** für time-driven Docs: unverändert; orthogonal zur Upstream-
  Oracle-Regel (ein Doc kann beides brauchen, dann beide Gates seriell anwenden).

---

## 6. Wann der Mensch das §6-Upgrade entscheidet

Sobald die Working-Rule **mindestens einen kompletten Re-Verify-Zyklus** (REM-121-Fix
→ alle quarantänierten Docs durch Schritte 1–4 → grünes Re-Promote) erfolgreich
durchgelaufen ist, schlägt der PO dem Menschen vor, die Regel in PROJECT_CONTEXT §6 zu
heben (Invariante-Änderung = Menschen-Entscheidung). Bis dahin: Working-Rule, im STATUS
verankert.
