package com.tneff.kmpremotecompose.conformance

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * REM-7 — **Korpus-Loader-Mechanik** (dev-2). Signatur von [RcCorpus] **fix vorgegeben von test-1**:
 *
 * ```
 * object RcCorpus {
 *     const val DIR = "rc-corpus"
 *     fun readFixture(name: String): ByteArray   // synchron via okio, wirft bei Fehlen, KEIN suspend
 *     fun manifest(): List<FixtureEntry>          // MANIFEST.tsv (# = Kommentar)
 *     fun corpusNames(): List<String>             // P2: alle unter rc-corpus/corpus/
 * }
 * ```
 *
 * Die reale IO läuft per okio über [OkioCorpusIo] (≙ test-1: `FileSystem.SYSTEM.read((root/DIR/name))`).
 * Die Lese-Logik in [RcCorpusReader] hängt nur am schmalen [CorpusIo]-Seam und ist damit ohne
 * physischen Korpus voll unit-testbar (in-memory Fake — siehe `RcCorpusReaderTest`).
 *
 * Hinweis: `okio-fakefilesystem` wird bewusst NICHT als Test-Dep genutzt — es ist über
 * `kotlinx-datetime` (`Clock.System`) inkompatibel mit der von `compose-material3` erzwungenen
 * datetime-0.7.x-Linie. Der [CorpusIo]-Seam umgeht das ohne fragile transitive Abhängigkeit.
 *
 * [fixtureRoot] löst portabel über die build-zeit-generierte Konstante `GENERATED_CORPUS_RESOURCES_ROOT`
 * (absoluter Pfad zu `src/commonTest/resources`, pro Build neu erzeugt) auf — funktioniert auf Host
 * (jvmTest) UND iOS-Simulator (beide laufen auf der Build-Maschine). [rootOverride] hat Vorrang (für
 * Spezial-Setups). Das ist **Test-Harness-Infra**; App-/Runtime-Resource-Loading ist separat (REM-8).
 */
object RcCorpus {
    const val DIR: String = "rc-corpus"

    /**
     * Übersteuerung der Korpus-Wurzel für Test-Setups, solange die plattformspezifische [fixtureRoot]
     * noch nicht final ist. Tester/Bindings setzen das im Setup; Default: nicht gesetzt.
     */
    var rootOverride: Path? = null

    private fun reader(): RcCorpusReader =
        RcCorpusReader(OkioCorpusIo(FileSystem.SYSTEM, fixtureRoot()))

    fun readFixture(name: String): ByteArray = reader().readFixture(name)

    fun manifest(): List<FixtureEntry> = reader().manifest()

    fun corpusNames(): List<String> = reader().corpusNames()

    /**
     * Wurzelverzeichnis, das `rc-corpus/` enthält. [rootOverride] hat Vorrang; sonst der
     * build-zeit-generierte absolute Pfad zu `src/commonTest/resources` — portabel auf Host + iOS-Sim.
     */
    private fun fixtureRoot(): Path = rootOverride ?: GENERATED_CORPUS_RESOURCES_ROOT.toPath()
}

/**
 * Schmaler IO-Seam über dem `rc-corpus/`-Verzeichnis (Pfade **relativ** zu `rc-corpus/`).
 * Entkoppelt die Loader-Logik vom konkreten Dateisystem → in-memory testbar.
 */
interface CorpusIo {
    fun exists(relative: String): Boolean
    fun readBytes(relative: String): ByteArray
    fun readText(relative: String): String

    /** Dateinamen (nur reguläre Dateien) direkt unter [relativeDir]; leer, wenn das Verzeichnis fehlt. */
    fun listFiles(relativeDir: String): List<String>
}

/**
 * okio-Implementierung des [CorpusIo] — exakt der von test-1 vorgegebene Lesepfad:
 * `FileSystem.SYSTEM.read((root/DIR/<relative>)) { readByteArray() }`.
 */
class OkioCorpusIo(
    private val fs: FileSystem,
    private val root: Path,
) : CorpusIo {
    private val dir: Path get() = root / RcCorpus.DIR

    override fun exists(relative: String): Boolean = fs.exists(dir / relative)

    override fun readBytes(relative: String): ByteArray = fs.read(dir / relative) { readByteArray() }

    override fun readText(relative: String): String = fs.read(dir / relative) { readUtf8() }

    override fun listFiles(relativeDir: String): List<String> {
        val d = dir / relativeDir
        if (!fs.exists(d)) return emptyList()
        return fs.list(d)
            .filter { fs.metadata(it).isRegularFile }
            .map { it.name }
            .sorted()
    }
}

/**
 * Reine Lese-Logik gegen den [CorpusIo]-Seam — keine Plattform- oder FS-Annahmen.
 *
 * Verzeichnis-Layout (TechSpec §3.1):
 * ```
 * <root>/rc-corpus/
 *   ├── MANIFEST.tsv         # Provenienz/Metadaten (test-1)
 *   ├── <fixtures...>.rc     # benannte Golden-/Smoke-Fixtures
 *   └── corpus/              # P2: alle 173 „laden fehlerfrei"
 *       └── <...>.rc
 * ```
 */
class RcCorpusReader(
    private val io: CorpusIo,
) {
    /** Liest `rc-corpus/<name>` synchron. Wirft, wenn die Datei fehlt (kein stilles `null`). */
    fun readFixture(name: String): ByteArray {
        if (!io.exists(name)) throw okio.IOException("Fixture fehlt: ${RcCorpus.DIR}/$name")
        return io.readBytes(name)
    }

    /** Parst `rc-corpus/MANIFEST.tsv`. Wirft, wenn die Datei fehlt. */
    fun manifest(): List<FixtureEntry> {
        if (!io.exists(MANIFEST_FILE)) throw okio.IOException("MANIFEST fehlt: ${RcCorpus.DIR}/$MANIFEST_FILE")
        return ManifestParser.parse(io.readText(MANIFEST_FILE))
    }

    /** Alle regulären Dateinamen unter `rc-corpus/corpus/` (P2-Smoke-Set), sortiert; leer wenn fehlend. */
    fun corpusNames(): List<String> = io.listFiles(CORPUS_SUBDIR)

    companion object {
        const val MANIFEST_FILE: String = "MANIFEST.tsv"
        const val CORPUS_SUBDIR: String = "corpus"
    }
}
