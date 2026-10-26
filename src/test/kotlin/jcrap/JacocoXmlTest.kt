package jcrap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JacocoXmlTest {
  private fun sampleXml(): String =
    requireNotNull(JacocoXmlTest::class.java.getResourceAsStream("/sample-jacoco.xml")) {
      "test fixture /sample-jacoco.xml missing"
    }.bufferedReader().readText()

  @Test fun `golden xml yields method stats`() {
    val stats = parseJacocoXml(sampleXml())
    assertEquals(3, stats.size)
    assertEquals(MethodStat("com/example/Drive", "describe", 21, 8, 90, 10), stats.first())
  }

  @Test fun `worse of instruction vs branch wins`() {
    val stats = parseJacocoXml(sampleXml())
    // steer: INSTRUCTION 80/100 vs BRANCH 2/10 -> BRANCH is worse
    assertEquals(MethodStat("com/example/Drive", "steer", 40, 5, 2, 8), stats[1])
  }

  @Test fun `malformed xml fails with named problem`() {
    val e = assertFailsWith<IllegalArgumentException> { parseJacocoXml("<not xml") }
    assertTrue(e.message!!.contains("malformed JaCoCo XML"), "message was: ${e.message}")
  }
}
