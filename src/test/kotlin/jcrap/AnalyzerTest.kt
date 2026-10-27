package jcrap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun statsWithLambdaAndPlain(): List<MethodStat> =
    listOf(
        MethodStat("com/example/A", "doThing", 10, complexity = 6, covered = 0, missed = 100),
        MethodStat(
            "com/example/A",
            "lambda\$doThing\$0",
            12,
            complexity = 2,
            covered = 0,
            missed = 10,
        ),
        MethodStat("com/example/A", "write\$Self", 14, complexity = 3, covered = 10, missed = 0),
        MethodStat("com/example/A", "foo\$default", 16, complexity = 2, covered = 5, missed = 5),
    )

class AnalyzerTest {
    @Test
    fun `synthetics filtered, threshold flags rows`() {
        val rows = analyze(statsWithLambdaAndPlain(), max = 10.0, exclude = emptyList())
        assertTrue(rows.none { it.stat.method.contains("lambda\$") })
        assertTrue(rows.none { it.stat.method.contains("write\$Self") })
        assertTrue(rows.none { it.stat.method.endsWith("\$default") })
        assertEquals(1, rows.count { it.failed })
    }

    @Test
    fun `empty report prints a hint, not a crash`() {
        assertTrue(
            formatReport(analyze(emptyList(), max = 10.0, exclude = emptyList()))
                .contains("no methods")
        )
    }

    @Test
    fun `hidden passing rows still count in summary`() {
        val stats =
            statsWithLambdaAndPlain() +
                MethodStat("com/example/A", "trivial", 20, complexity = 2, covered = 10, missed = 0)
        val rows = analyze(stats, max = 10.0, exclude = emptyList())
        val report = formatReport(rows, showAll = false)
        assertTrue(!report.contains("trivial"), "passing rows stay hidden")
        assertTrue(report.contains("Above threshold: 1 of 2"))
    }

    @Test
    fun `clean report keeps summary when hidden`() {
        val rows =
            analyze(
                listOf(MethodStat("com/example/A", "trivial", 20, 2, 10, 0)),
                max = 10.0,
                exclude = emptyList(),
            )
        val report = formatReport(rows, showAll = false)
        assertTrue(report.contains("Above threshold: 0 of 1"))
        assertTrue(report.contains("Average CRAP"))
    }

    @Test
    fun `exclude globs drop matching classes`() {
        val stats =
            listOf(
                MethodStat("com/example/Gen", "a", 1, complexity = 6, covered = 0, missed = 10),
                MethodStat("com/example/Keep", "b", 2, complexity = 6, covered = 0, missed = 10),
            )
        val rows = analyze(stats, max = null, exclude = listOf("com/example/Gen*"))
        assertEquals(1, rows.size)
        assertEquals("com/example/Keep", rows.single().stat.className)
    }

    @Test
    fun `dot-form class matched by slash glob`() {
        val stats = listOf(MethodStat("com.example.Gen", "a", 1, 6, 0, 10))
        assertEquals(0, analyze(stats, max = null, exclude = listOf("com/example/Gen*")).size)
    }

    @Test
    fun `accessor synthetic filtered, invokeSuspend kept`() {
        val stats =
            listOf(
                MethodStat("com/example/A", "access\$getFoo", 1, 2, 0, 10),
                MethodStat("com/example/A", "invokeSuspend", 1, 6, 0, 10),
            )
        val rows = analyze(stats, max = null, exclude = emptyList())
        assertTrue(rows.none { it.stat.method.contains("access\$") })
        assertTrue(rows.any { it.stat.method == "invokeSuspend" })
    }

    @Test
    fun `crap exactly at max passes, just over fails`() {
        // complexity 1 at 0% coverage scores exactly 2.0 (1^2 x 1^3 + 1).
        val stats =
            listOf(MethodStat("com/example/A", "f", 1, complexity = 1, covered = 0, missed = 10))
        val atMax = analyze(stats, max = 2.0, exclude = emptyList())
        assertEquals(1, atMax.size)
        assertEquals(false, atMax.single().failed)
        val overMax = analyze(stats, max = 1.99, exclude = emptyList())
        assertEquals(true, overMax.single().failed)
    }
}
