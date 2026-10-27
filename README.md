# jcrap

Machine-checked CRAP for JVM builds. A Gradle plugin that scores every method
from a JaCoCo XML report, prints a ranked table, and fails the build over
threshold. It ports the `gocrap` contract (table shape, threshold flag,
fail-over-threshold exit) from Go to the JVM.

## Install

Requires Gradle 9.x (a Java 17+ runtime to drive it; the Foojay toolchain
resolver provisions the build JDK automatically).

**JitPack (recommended).** No accounts, no keys — point at a git ref:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

```kotlin
// build.gradle.kts
plugins {
    id("com.github.mfenderov.jcrap") version "<tag or commit SHA>"
}
```

Use a full commit SHA instead of a moving tag when reproducibility matters.

**Option B — composite build (local development).** In the consumer's
`settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("../jcrap")
}
```

then `plugins { id("jcrap") }` in the module build file. This is how the
project dogfoods itself.

**Option B — mavenLocal.** In `jcrap`:

```bash
./gradlew publishToMavenLocal
```

then in the consumer's `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}

plugins {
    id("jcrap") version "0.1.0-SNAPSHOT"
}
```

## Usage

1. Produce a JaCoCo XML report (JaCoCo 0.8.13+ for recent bytecode):

```kotlin
plugins {
    jacoco
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
    }
}
```

```bash
./gradlew test jacocoTestReport
```

2. Point `crap` at the report (optional — defaults to the standard JaCoCo path):

```kotlin
plugins {
    id("jcrap")
}

crap {
    report.set(layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml"))
    max.set(30.0)
    showAll.set(false)
    exclude.set(listOf("*Generated*", "com/example/*Test*"))
}
```

| Setting   | Default | Meaning                                                    |
|-----------|---------|------------------------------------------------------------|
| `report`  | `build/reports/jacoco/test/jacocoTestReport.xml` | JaCoCo XML file to score. |
| `max`     | `30.0`  | Methods with CRAP above this fail the build.               |
| `showAll` | `false` | Print passing methods too; default prints only violations. |
| `exclude` | `[]`    | Glob filters (`*`, `?`) matched against class/method names.|

3. Run it:

```bash
./gradlew crapCheck
```

`crapCheck` prints the report to the console and throws a `GradleException`
listing every method over `max`. The task is configuration-cache compatible.

## Example output

Real run over `drive-iam-service` (27 tests green, `max = 10.0`, head of 75 rows):

```text
CRAP   COVERAGE   COMPLEXITY   LOCATION
 44.03    39.4%           12   com/example/drive/api/FoldersRouteKt$foldersRoute$1::invokeSuspend:16  FAIL
 42.00     0.0%            6   com/example/drive/ApplicationKt::dataSourceFromEnv:57  FAIL
 12.00     0.0%            3   com/example/drive/ApplicationKt::main:21  FAIL
 12.00     0.0%            3   com/example/drive/ApplicationKt::toJdbcUrl:66  FAIL
  7.77    75.0%            7   com/example/drive/service/DescribeService::subtreeSize:109
  6.00     0.0%            2   com/example/drive/api/ErrorResponse::<init>:8
  6.00   100.0%            6   com/example/drive/domain/FolderPath$Companion::parse-m12R8Og:10
  ...
Average CRAP: 3.37
Above threshold: 4 of 75
```

Every one of the 4 violations was investigated (see Dogfood below) — surprises
get explained, not ignored.

## Formula

The original CRAP definition:

```text
CRAP(m) = complexity(m)^2 x (1 - coverage(m))^3 + complexity(m)
```

Inputs come from JaCoCo method counters:

- **Coverage** — covered share of instructions vs branches, whichever is worse.
  A method with no counters scores 0%, never crashes.
- **Complexity** — the JaCoCo cyclomatic-complexity counter (covered + missed).

## Behavior notes

- Synthetic methods (`lambda$…`, `write$Self`, `…$default`) are filtered out;
  coroutine state machines (`invokeSuspend`) are kept — they are real logic.
- Rows sort worst-first; `FAIL` marks methods over `max`.
- Empty input prints `no methods to report` instead of an empty table.
- Real JaCoCo reports carry a `DOCTYPE`; the parser accepts it while still
  refusing external entities (XXE-safe).

## Dogfood

`crapCheck` runs against `drive-iam-service` (Kotlin/Ktor). Hand-counted source
branches vs JaCoCo bytecode counters:

| Method | Bytecode c | Hand-counted c | Verdict |
|--------|-----------|----------------|---------|
| `foldersRoute` route lambda (`invokeSuspend`) | 12 | ~5 | Coroutine state machine inflates bytecode; still ~10.6 at source level — genuinely the riskiest function |
| `ApplicationKt::dataSourceFromEnv` | 6 | 6 | Exact; 0% covered env wiring |
| `ApplicationKt::main` | 3 | 3 | Exact; untestable entry point |
| `ApplicationKt::toJdbcUrl` | 3 | 3 | Exact; pure, trivially testable gap |
| `DescribeService::subtreeSize` | 7 | 6–7 | Within tolerance |
| `DescribeService::collectFamily` | 6 | 6 | Exact |
| `DescribeService::describe` | 4 | 4 | Exact |
| `AuthorizerKt::isVisible` | 5 | ~5 | Match |
| `Principal$Companion::parse` | 5 | 5 | Exact |

75 methods, average CRAP 3.37. The brief expected every method under 10; the 4
over 10 at `max = 10.0` are all accounted for above — untested config wiring,
an untestable `main`, one pure function missing a test, and the route handler
that deserves either error-path tests or extraction.

## Go sibling

[`gocrap`](https://github.com/mfenderov/gocrap) is the Go sibling: same CRAP formula, same violations-first table,
same threshold-gates-the-build contract — Go AST plus `go test -cover` on that
side, JaCoCo XML on this one.
