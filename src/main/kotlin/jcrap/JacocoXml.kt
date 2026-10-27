package jcrap

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

/** Parses a JaCoCo XML report into one [MethodStat] per `<method>`, in document order. */
fun parseJacocoXml(xml: String): List<MethodStat> {
    val doc =
        try {
            val factory = DocumentBuilderFactory.newInstance()
            // Real JaCoCo reports carry a DOCTYPE, so allow it but neutralize XXE:
            // no external entities, no external DTD, no XInclude, no expansion.
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            factory.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false,
            )
            factory.isXIncludeAware = false
            factory.isExpandEntityReferences = false
            factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        } catch (e: Exception) {
            throw IllegalArgumentException(e.message, e)
        }
    doc.documentElement.normalize()
    val stats = mutableListOf<MethodStat>()
    val classes = doc.getElementsByTagName("class")
    for (i in 0 until classes.length) {
        val cls = classes.item(i) as Element
        val className = cls.getAttribute("name")
        val methods = cls.getElementsByTagName("method")
        for (j in 0 until methods.length) {
            stats += toMethodStat(className, methods.item(j) as Element)
        }
    }
    return stats
}

private fun toMethodStat(className: String, method: Element): MethodStat {
    val counters = countersOf(method)
    val coveredToMissed = worseOf(counters["INSTRUCTION"], counters["BRANCH"])
    val complexity = counters["COMPLEXITY"]
    return MethodStat(
        className = className,
        method = method.getAttribute("name"),
        line = method.getAttribute("line").toIntOrNull() ?: 0,
        complexity = (complexity?.first ?: 0) + (complexity?.second ?: 0),
        covered = coveredToMissed.first,
        missed = coveredToMissed.second,
    )
}

/** Counter type -> (covered, missed) for the direct `<counter>` children. */
private fun countersOf(method: Element): Map<String, Pair<Int, Int>> {
    val out = mutableMapOf<String, Pair<Int, Int>>()
    val counters = method.getElementsByTagName("counter")
    for (i in 0 until counters.length) {
        val counter = counters.item(i) as Element
        if (counter.parentNode != method) continue
        out[counter.getAttribute("type")] =
            Pair(
                counter.getAttribute("covered").toIntOrNull() ?: 0,
                counter.getAttribute("missed").toIntOrNull() ?: 0,
            )
    }
    return out
}

/** Worse coverage fraction of INSTRUCTION vs BRANCH; BRANCH absent -> INSTRUCTION only. */
private fun worseOf(instruction: Pair<Int, Int>?, branch: Pair<Int, Int>?): Pair<Int, Int> {
    if (instruction == null) return branch ?: Pair(0, 0)
    if (branch == null) return instruction
    if (fraction(branch) < fraction(instruction)) return branch
    return instruction
}

private fun fraction(counter: Pair<Int, Int>): Double {
    val total = counter.first + counter.second
    if (total == 0) return 1.0
    return counter.first.toDouble() / total
}
