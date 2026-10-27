plugins {
  kotlin("jvm") version "2.4.21"
  `java-gradle-plugin`
  `maven-publish`
  signing
}

group = "com.github.mfenderov"
version = providers.gradleProperty("releaseVersion").getOrElse("0.1.0-SNAPSHOT")

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
      displayName = "jcrap CRAP metric check"
      description = "Fails the build when JaCoCo-covered methods exceed the CRAP threshold."
      website = "https://github.com/mfenderov/jcrap"
    }
  }
}

java {
  withSourcesJar()
  withJavadocJar()
}

signing {
  // Signs only when a key is provided (release CI). Local builds stay green.
  val privateKey = System.getenv("GPG_PRIVATE_KEY")
  if (!privateKey.isNullOrBlank()) {
    useInMemoryPgpKeys(privateKey, System.getenv("GPG_PASSPHRASE").orEmpty())
    sign(publishing.publications)
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
