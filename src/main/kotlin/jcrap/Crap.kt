package jcrap

data class MethodStat(
    val className: String,
    val method: String,
    val line: Int,
    val complexity: Int,
    val covered: Int,
    val missed: Int,
)

/** Covered share as a percentage, 0.0..100.0. No counters means 0.0, never a crash. */
fun coverage(stat: MethodStat): Double {
    val total = stat.covered + stat.missed
    if (total == 0) return 0.0
    return stat.covered.toDouble() / total * 100.0
}

/** CRAP(m) = complexity^2 x (1 - coverage)^3 + complexity. */
fun crapScore(stat: MethodStat): Double {
    val miss = 1.0 - coverage(stat) / 100.0
    val c = stat.complexity.toDouble()
    return c * c * miss * miss * miss + c
}
