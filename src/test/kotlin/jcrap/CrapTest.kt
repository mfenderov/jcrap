package jcrap

import kotlin.test.Test
import kotlin.test.assertEquals

class CrapTest {
  @Test fun `gocrap reference vector`() {
    val m = MethodStat("pkg/Agent", "Handle", 122, complexity = 15, covered = 13, missed = 27)
    assertEquals(32.5, coverage(m), 0.05)
    // Exact: 225 x 0.675^3 + 15 = 84.198... (brief said 84.1, outside the 0.05 delta)
    assertEquals(84.2, crapScore(m), 0.05)
  }

  @Test fun `no counters means zero coverage, not a crash`() {
    val m = MethodStat("pkg/A", "f", 1, complexity = 4, covered = 0, missed = 0)
    assertEquals(0.0, coverage(m), 0.0)
    // Exact: 16 x 1 + 4 = 20.0 (brief said 68.0, contradicting its own comment)
    assertEquals(20.0, crapScore(m), 0.05) // conservative: no counters -> 0% coverage
  }
}
