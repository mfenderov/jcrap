package jcrap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun statsWithLambdaAndPlain(): List<MethodStat> =
  listOf(
    MethodStat("com/example/A", "doThing", 10, complexity = 6, covered = 0, missed = 100),
    MethodStat("com/example/A", "lambda\$doThing\$0", 12, complexity = 2, covered = 0, missed = 10),
    MethodStat("com/example/A", "write\$Self", 14, complexity = 3, covered = 10, missed = 0),
    MethodStat("com/example/A", "foo\$default", 16, complexity = 2, covered = 5, missed = 5),
  )

class AnalyzerTest {
  @Test fun `synthetics filtered, threshold flags rows`() {
    val rows = analyze(statsWithLambdaAndPlain(), max = 10.0, exclude = emptyList())
    assertTrue(rows.none { it.stat.method.contains("lambda\$") })
    assertTrue(rows.none { it.stat.method.contains("write\$Self") })
    assertTrue(rows.none { it.stat.method.endsWith("\$default") })
    assertEquals(1, rows.count { it.failed })
  }

  @Test fun `empty report prints a hint, not a crash`() {
    assertTrue(formatReport(analyze(emptyList(), max = 10.0, exclude = emptyList())).contains("no methods"))
  }

  @Test fun `exclude globs drop matching classes`() {
    val stats =
      listOf(
        MethodStat("com/example/Gen", "a", 1, complexity = 6, covered = 0, missed = 10),
        MethodStat("com/example/Keep", "b", 2, complexity = 6, covered = 0, missed = 10),
      )
    val rows = analyze(stats, max = null, exclude = listOf("com/example/Gen*"))
    assertEquals(1, rows.size)
    assertEquals("com/example/Keep", rows.single().stat.className)
  }

  @Test fun `crap exactly at max passes, just over fails`() {
    // complexity 1 at 0% coverage scores exactly 2.0 (1^2 x 1^3 + 1).
    val stats = listOf(MethodStat("com/example/A", "f", 1, complexity = 1, covered = 0, missed = 10))
    val atMax = analyze(stats, max = 2.0, exclude = emptyList())
    assertEquals(1, atMax.size)
    assertEquals(false, atMax.single().failed)
    val overMax = analyze(stats, max = 1.99, exclude = emptyList())
    assertEquals(true, overMax.single().failed)
  }
}
