package jcrap

import java.util.Locale

data class Row(val stat: MethodStat, val crap: Double, val failed: Boolean)

/** Filters synthetics + exclude globs, scores CRAP, sorts worst-first. */
fun analyze(stats: List<MethodStat>, max: Double?, exclude: List<String>): List<Row> {
    val excludeRegexes = exclude.map { Regex(globToRegex(it.replace('.', '/'))) }
    return stats
        .filter { !isSynthetic(it.method) }
        .filter { stat ->
            excludeRegexes.none {
                it.matches(stat.className.replace('.', '/')) || it.matches(stat.method)
            }
        }
        .map { stat ->
            val crap = crapScore(stat)
            Row(stat, crap, failed = max != null && crap > max)
        }
        .sortedWith(
            compareByDescending<Row> { it.crap }
                .thenBy { it.stat.className }
                .thenBy { it.stat.method }
        )
}

private fun isSynthetic(method: String): Boolean {
    if (method == "invokeSuspend") return false
    return method.contains("lambda\$") ||
        method.contains("write\$Self") ||
        method.endsWith("\$default") ||
        method.contains("access\$")
}

private fun globToRegex(glob: String): String {
    val out = StringBuilder("^")
    for (ch in glob) {
        when (ch) {
            '*' -> out.append(".*")
            '?' -> out.append(".")
            '.',
            '^',
            '$',
            '+',
            '{',
            '}',
            '[',
            ']',
            '(',
            ')',
            '|',
            '\\' -> out.append('\\').append(ch)
            else -> out.append(ch)
        }
    }
    return out.append("$").toString()
}

/**
 * gocrap-style column table plus Average CRAP / Above-threshold summary. Display honors showAll
 * (violations only otherwise); the summary always counts the full analyzed set so "F of N" stays
 * truthful.
 */
fun formatReport(rows: List<Row>, showAll: Boolean = true): String {
    if (rows.isEmpty()) return "no methods to report"
    val shown = if (showAll) rows else rows.filter { it.failed }
    val avg = rows.sumOf { it.crap } / rows.size
    val summary =
        listOf(
            String.format(Locale.US, "Average CRAP: %.2f", avg),
            String.format(
                Locale.US,
                "Above threshold: %d of %d",
                rows.count { it.failed },
                rows.size,
            ),
        )
    if (shown.isEmpty()) return (listOf("no violations") + summary).joinToString("\n")
    val lines = mutableListOf("CRAP   COVERAGE   COMPLEXITY   LOCATION")
    for (row in shown) {
        val mark = if (row.failed) "  FAIL" else ""
        lines +=
            String.format(
                Locale.US,
                "%6.2f   %5.1f%%   %10d   %s::%s:%d%s",
                row.crap,
                coverage(row.stat),
                row.stat.complexity,
                row.stat.className,
                row.stat.method,
                row.stat.line,
                mark,
            )
    }
    lines += summary
    return lines.joinToString("\n")
}
