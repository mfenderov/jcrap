plugins {
  kotlin("jvm") version "2.4.21"
  `java-gradle-plugin`
  `maven-publish`
}

group = "jcrap"
version = "0.1.0-SNAPSHOT"

repositories {
  mavenCentral()
}

dependencies {
  testImplementation(kotlin("test"))
  testImplementation(kotlin("test-junit5"))
  testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
  testImplementation(gradleTestKit())
}

gradlePlugin {
  plugins {
    create("jcrap") {
      id = "jcrap"
      implementationClass = "jcrap.CrapPlugin"
    }
  }
}

tasks.test {
  useJUnitPlatform()
}

kotlin {
  // Keep the plugin loadable by older daemons: lowest toolchain available
  // on this machine (a 25 toolchain forces every consumer's daemon onto 25+).
  jvmToolchain(21)
}
