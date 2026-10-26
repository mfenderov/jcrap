package jcrap

import java.util.Locale
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/** Thresholds and inputs for the `crapCheck` task. */
abstract class CrapExtension @Inject constructor(objects: ObjectFactory) {
  val max: Property<Double> = objects.property(Double::class.java).convention(30.0)
  val showAll: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
  val exclude: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
  val report: RegularFileProperty = objects.fileProperty()
}

/** Reads a JaCoCo XML report, prints the CRAP report, fails the build over threshold. */
@DisableCachingByDefault(because = "writes the CRAP report to the console")
abstract class CrapCheckTask : DefaultTask() {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val reportFile: RegularFileProperty

  @get:Input abstract val max: Property<Double>

  @get:Input abstract val showAll: Property<Boolean>

  @get:Input abstract val exclude: ListProperty<String>

  @TaskAction
  fun check() {
    val report = reportFile.asFile.get()
    val parsed =
      try {
        parseJacocoXml(report.readText())
      } catch (e: IllegalArgumentException) {
        throw GradleException("malformed JaCoCo XML ($report): ${e.message}", e)
      }
    val rows = analyze(parsed, max.get(), exclude.get())
    println(formatReport(rows, showAll.get()))
    val failed = rows.filter { it.failed }
    if (failed.isNotEmpty()) {
      val violations = failed.joinToString("\n") { String.format(Locale.US, "%.2f %s::%s", it.crap, it.stat.className, it.stat.method) }
      throw GradleException("CRAP threshold exceeded (max ${max.get()}):\n$violations")
    }
  }
}

class CrapPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = project.extensions.create("crap", CrapExtension::class.java)
    extension.report.convention(project.layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml"))
    project.tasks.register("crapCheck", CrapCheckTask::class.java) { task ->
      task.reportFile.convention(extension.report)
      task.max.convention(extension.max)
      task.showAll.convention(extension.showAll)
      task.exclude.convention(extension.exclude)
    }
  }
}
