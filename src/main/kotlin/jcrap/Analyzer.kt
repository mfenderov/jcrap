package jcrap

data class Row(val stat: MethodStat, val crap: Double, val failed: Boolean)

/** Filters synthetics + exclude globs, scores CRAP, sorts worst-first. */
fun analyze(stats: List<MethodStat>, max: Double?, exclude: List<String>): List<Row> =
  stats
    .filter { !isSynthetic(it.method) }
    .filter { stat -> exclude.none { matchesGlob(it, stat.className) || matchesGlob(it, stat.method) } }
    .map { stat ->
      val crap = crapScore(stat)
      Row(stat, crap, failed = max != null && crap > max)
    }
    .sortedWith(compareByDescending<Row> { it.crap }.thenBy { it.stat.className }.thenBy { it.stat.method })

private fun isSynthetic(method: String): Boolean =
  method.contains("lambda\$") || method.contains("write\$Self") || method.endsWith("\$default")

private fun matchesGlob(glob: String, value: String): Boolean = Regex(globToRegex(glob)).matches(value)

private fun globToRegex(glob: String): String {
  val out = StringBuilder("^")
  for (ch in glob) {
    when (ch) {
      '*' -> out.append(".*")
      '?' -> out.append(".")
      '.', '^', '$', '+', '{', '}', '[', ']', '(', ')', '|', '\\' -> out.append('\\').append(ch)
      else -> out.append(ch)
    }
  }
  return out.append("$").toString()
}

/** gocrap-style column table plus Average CRAP / Above-threshold summary. */
fun formatReport(rows: List<Row>): String {
  if (rows.isEmpty()) return "no methods to report"
  val lines = mutableListOf("CRAP   COVERAGE   COMPLEXITY   LOCATION")
  for (row in rows) {
    val mark = if (row.failed) "  FAIL" else ""
    lines +=
      "%6.2f   %5.1f%%   %10d   %s::%s:%d%s"
        .format(row.crap, coverage(row.stat), row.stat.complexity, row.stat.className, row.stat.method, row.stat.line, mark)
  }
  val avg = rows.sumOf { it.crap } / rows.size
  lines += "Average CRAP: %.2f".format(avg)
  lines += "Above threshold: %d of %d".format(rows.count { it.failed }, rows.size)
  return lines.joinToString("\n")
}
