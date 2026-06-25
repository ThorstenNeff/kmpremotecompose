package com.tneff.kmpremotecompose.conformance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ManifestParserTest {

    private val sample = """
        # id	targetName	bytes	tier	role	sourcePath	generator	semantic
        # Kommentarzeile wird ignoriert
        F1	procedure_simple1.rc	142	P0	writer-byte-equality	upstream/demos/procedure_simple1.rc	jvm-ref	einfacher Kreis

        F7	screenshottest.rc	218	P1	decode-only	remote-testing/.../screenshottest.rc	jvm-ref	Text+Layout
    """.trimIndent()

    @Test
    fun parses_skippingCommentsAndBlankLines() {
        val entries = ManifestParser.parse(sample)
        assertEquals(2, entries.size)
        val f1 = entries[0]
        assertEquals("F1", f1.id)
        assertEquals("procedure_simple1.rc", f1.targetName)
        assertEquals(142, f1.bytes)
        assertEquals("P0", f1.tier)
        assertEquals("writer-byte-equality", f1.role)
        assertEquals("jvm-ref", f1.generator)
        assertEquals("einfacher Kreis", f1.semantic)
        assertEquals("F7", entries[1].id)
        assertEquals(218, entries[1].bytes)
    }

    @Test
    fun wrongColumnCount_throwsWithLineNumber() {
        val bad = "F1\tonly\tthree\tcols"
        val ex = assertFailsWith<IllegalArgumentException> { ManifestParser.parse(bad) }
        // Fehlermeldung nennt die Zeilennummer für schnelle Lokalisierung.
        assertEquals(true, ex.message!!.contains("Zeile 1"))
    }

    @Test
    fun nonIntBytes_throws() {
        val bad = "F1\ta.rc\tNOTANUMBER\tP0\trole\tsrc\tgen\tsem"
        assertFailsWith<IllegalArgumentException> { ManifestParser.parse(bad) }
    }

    @Test
    fun columnsConstant_matchesEntryArity() {
        assertEquals(8, ManifestParser.COLUMNS.size)
    }
}
