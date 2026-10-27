package jcrap

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val PASSING_XML =
  """<?xml version="1.0" encoding="UTF-8"?>
<report name="pass">
  <package name="com/example">
    <class name="com/example/Clean">
      <method name="tidy" line="1">
        <counter type="INSTRUCTION" missed="0" covered="100"/>
        <counter type="COMPLEXITY" missed="0" covered="1"/>
      </method>
    </class>
  </package>
</report>"""

private const val FAILING_XML =
  """<?xml version="1.0" encoding="UTF-8"?>
<report name="fail">
  <package name="com/example">
    <class name="com/example/Messy">
      <method name="spaghetti" line="7">
        <counter type="INSTRUCTION" missed="100" covered="0"/>
        <counter type="COMPLEXITY" missed="6" covered="0"/>
      </method>
    </class>
  </package>
</report>"""

private fun checkTaskWith(xml: String, max: Double = 30.0, showAll: Boolean = false): CrapCheckTask {
  val project = ProjectBuilder.builder().build()
  project.pluginManager.apply(CrapPlugin::class.java)
  val task = project.tasks.getByName("crapCheck") as CrapCheckTask
  val file = project.layout.projectDirectory.file("jacoco.xml").asFile
  file.writeText(xml)
  task.reportFile.set(file)
  task.max.set(max)
  task.showAll.set(showAll)
  return task
}

private fun stdoutOf(block: () -> Unit): String {
  val out = ByteArrayOutputStream()
  val original = System.out
  System.setOut(PrintStream(out))
  try {
    block()
  } finally {
    System.setOut(original)
  }
  return out.toString()
}

class CrapPluginTest {
  @Test fun `plugin registers crapCheck with defaults`() {
    val project = ProjectBuilder.builder().build()
    project.pluginManager.apply(CrapPlugin::class.java)
    assertNotNull(project.tasks.findByName("crapCheck"))
  }

  @Test fun `extension has documented defaults`() {
    val project = ProjectBuilder.builder().build()
    project.pluginManager.apply(CrapPlugin::class.java)
    val ext = project.extensions.getByType(CrapExtension::class.java)
    assertEquals(30.0, ext.max.get())
    assertEquals(false, ext.showAll.get())
  }

  @Test fun `crapCheck passes when all methods under threshold`() {
    val task = checkTaskWith(PASSING_XML, max = 30.0)
    val out = stdoutOf { task.check() }
    assertTrue(out.contains("no violations"))
    assertTrue(out.contains("Above threshold: 0 of 1"))
  }

  @Test fun `showAll true prints passing methods with summary`() {
    val task = checkTaskWith(PASSING_XML, max = 30.0, showAll = true)
    val out = stdoutOf { task.check() }
    assertTrue(out.contains("tidy"))
    assertTrue(out.contains("Average CRAP"))
  }

  @Test fun `crapCheck fails when a method is over threshold`() {
    val task = checkTaskWith(FAILING_XML, max = 30.0)
    val error = assertFailsWith<org.gradle.api.GradleException> { stdoutOf { task.check() } }
    assertTrue(error.message!!.contains("spaghetti"))
  }

  @Test fun `showAll false hides passing methods`() {
    val task = checkTaskWith(PASSING_XML, max = 30.0, showAll = false)
    val out = stdoutOf { task.check() }
    assertTrue(!out.contains("tidy"))
  }

  @Test fun `exclude defaults to empty`() {
    val project = ProjectBuilder.builder().build()
    project.pluginManager.apply(CrapPlugin::class.java)
    val ext = project.extensions.getByType(CrapExtension::class.java)
    assertEquals(emptyList(), ext.exclude.get())
  }

  @Test fun `crapCheck honors exclude globs`() {
    val task = checkTaskWith(FAILING_XML, max = 30.0)
    task.exclude.set(listOf("*Messy*"))
    val out = stdoutOf { task.check() }
    assertTrue(out.contains("no methods"), "excluded method should not fail the build")
  }

  @Test fun `malformed xml failure names the report file`() {
    val task = checkTaskWith("<not xml", max = 30.0)
    val reportPath = task.reportFile.asFile.get().path
    val error = assertFailsWith<org.gradle.api.GradleException> { task.check() }
    assertTrue(error.message!!.contains(reportPath), "message was: ${error.message}")
  }

  @Test fun `missing report names file and fix`() {
    val task = checkTaskWith(PASSING_XML)
    task.reportFile.set(java.io.File("/tmp/does-not-exist-jcrap.xml"))
    val err = assertFailsWith<org.gradle.api.GradleException> { task.check() }
    assertTrue(err.message!!.contains("not found"))
    assertTrue(err.message!!.contains("/tmp/does-not-exist-jcrap.xml"))
  }

  @Test fun `malformed prefix appears once`() {
    val task = checkTaskWith("<not xml")
    val err = assertFailsWith<org.gradle.api.GradleException> { task.check() }
    assertEquals(1, err.message!!.split("malformed JaCoCo XML").size - 1)
  }

  @Test fun `report defaults to the standard jacoco location`() {
    val dir = java.nio.file.Files.createTempDirectory("jcrap-default-report").toFile()
    try {
      val project = ProjectBuilder.builder().withProjectDir(dir).build()
      project.pluginManager.apply(CrapPlugin::class.java)
      val task = project.tasks.getByName("crapCheck") as CrapCheckTask
      val path = task.reportFile.get().asFile.path.replace('\\', '/')
      assertTrue(
        path.endsWith("reports/jacoco/test/jacocoTestReport.xml"),
        "default report path was: $path",
      )
    } finally {
      dir.deleteRecursively()
    }
  }
}
