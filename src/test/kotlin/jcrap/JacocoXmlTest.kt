package jcrap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JacocoXmlTest {
    private fun sampleXml(): String =
        requireNotNull(JacocoXmlTest::class.java.getResourceAsStream("/sample-jacoco.xml")) {
                "test fixture /sample-jacoco.xml missing"
            }
            .bufferedReader()
            .readText()

    @Test
    fun `golden xml yields method stats`() {
        val stats = parseJacocoXml(sampleXml())
        assertEquals(3, stats.size)
        assertEquals(MethodStat("com/example/Drive", "describe", 21, 8, 90, 10), stats.first())
    }

    @Test
    fun `worse of instruction vs branch wins`() {
        val stats = parseJacocoXml(sampleXml())
        // steer: INSTRUCTION 80/100 vs BRANCH 2/10 -> BRANCH is worse
        assertEquals(MethodStat("com/example/Drive", "steer", 40, 5, 2, 8), stats[1])
    }

    @Test
    fun `malformed xml fails with named problem`() {
        val e = assertFailsWith<IllegalArgumentException> { parseJacocoXml("<not xml") }
        // Friendly "malformed JaCoCo XML" prefix is added only by CrapCheckTask; the parser
        // propagates the raw cause message here.
        assertTrue(!e.message.isNullOrBlank(), "message was: ${e.message}")
        assertFalse(e.message!!.contains("malformed JaCoCo XML"), "message was: ${e.message}")
    }

    @Test
    fun `real-world doctype report parses`() {
        // Real JaCoCo XML always carries this DOCTYPE; the parser must accept it.
        val xml =
            sampleXml()
                .replaceFirst(
                    "<report ",
                    "<!DOCTYPE report PUBLIC \"-//JACOCO//DTD Report 1.1//EN\" \"report.dtd\"><report ",
                )
        val stats = parseJacocoXml(xml)
        assertEquals(3, stats.size)
        assertEquals(MethodStat("com/example/Drive", "describe", 21, 8, 90, 10), stats.first())
    }

    @Test
    fun `external entity is rejected not expanded`() {
        val xml =
            """<?xml version="1.0" encoding="UTF-8"?>
      <!DOCTYPE report [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
      <report name="evil"><package name="p"><class name="p/Evil" sourcefilename="Evil.kt">""" +
                """<method name="&xxe;" line="1"><counter type="COMPLEXITY" missed="0" covered="1"/>""" +
                """<counter type="INSTRUCTION" missed="0" covered="1"/></method>""" +
                "</class></package></report>"
        val e = assertFailsWith<IllegalArgumentException> { parseJacocoXml(xml) }
        // Friendly prefix lives in CrapCheckTask; here the raw parser message must survive.
        assertTrue(!e.message.isNullOrBlank(), "message was: ${e.message}")
        assertFalse(e.message!!.contains("malformed JaCoCo XML"), "message was: ${e.message}")
    }
}
