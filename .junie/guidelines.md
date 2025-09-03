Project-specific development guidelines

Scope
- This document captures only information that is particular to this repository (sdk-java). It assumes familiarity with Gradle, JUnit 5, Docker, and multi-module builds.

Build and configuration
- JDK/Toolchains
  - Java toolchain: 17 for compile/run (auto-provisioned via org.gradle.toolchains.foojay-resolver-convention in settings.gradle.kts). CI also validates with Java 21.
  - You do NOT need a locally installed JDK 17 if your Gradle has toolchains enabled; Gradle will download it.
- Gradle wrapper
  - Always use the provided wrapper: ./gradlew ...
  - Wrapper version: see gradle/wrapper/gradle-wrapper.properties (8.x). CI uses the wrapper as well.
- Root build highlights (build.gradle.kts)
  - Versioning via Gradle Version Catalog: gradle/libs.versions.toml. The SDK version is libs.versions.restate.
  - Spotless is enforced across all projects (Google Java Format for Java, ktfmt for Kotlin, license headers from config/license-header). The check task depends on checkLicense from the license-report plugin.
  - Dokka is applied to most subprojects for Kotlin docs; aggregated Javadocs live in :sdk-aggregated-javadocs.
  - Publishing is wired via io.github.gradle-nexus.publish-plugin. Sonatype credentials must be provided as MAVEN_CENTRAL_USERNAME and MAVEN_CENTRAL_TOKEN environment variables when publishing.
- Subprojects layout
  - Core libraries: common, client, client-kotlin, sdk-common, sdk-core, sdk-serde-*, sdk-request-identity, sdk-api*, sdk-http-vertx, sdk-lambda, sdk-spring-boot*, starters, meta modules (sdk-*-http/lambda), examples, test-services.
  - Java/Kotlin conventions are centralized in buildSrc:
    - java-conventions.gradle.kts: toolchain 17, JUnit Platform, Spotless with googleJavaFormat.
    - kotlin-conventions.gradle.kts: toolchain 17, JUnit Platform, Spotless with ktfmt, license headers.

Testing
- Frameworks and dependencies
  - JUnit 5 Platform is enabled globally (tasks.withType<Test> { useJUnitPlatform() }).
  - AssertJ is available broadly via the version catalog and commonly applied in modules.
  - Some integration tests use Testcontainers and the Restate runtime. The sdk-testing module provides a JUnit 5 extension and utilities (RestateTest, RestateRunner) to spin up Restate and auto-register in-process services.
- Running tests
  - All modules: ./gradlew test
  - Single module: ./gradlew :common:test (replace :common with the desired module)
  - Single class or method: ./gradlew :common:test --tests 'dev.restate.common.SomethingTest' or --tests 'dev.restate.common.SomethingTest.methodName'
  - CI pulls the Restate Docker image explicitly and tests on Java 17 and 21 (see .github/workflows/tests.yml). Locally, if you run integration tests that leverage @RestateTest, ensure Docker is running; Testcontainers will pull the required image on demand.
- Restate integration testing (sdk-testing)
  - Annotate your JUnit 5 test class with @RestateTest to bootstrap a Restate runtime in a container and register your services.
    - Example sketch:
      @RestateTest(containerImage = "ghcr.io/restatedev/restate:main")
      class CounterTest { /* fields annotated with @BindService, inject @RestateClient, etc. */ }
  - The default image is docker.io/restatedev/restate:latest; CI uses ghcr.io/restatedev/restate:main. You can override via containerImage or add env via environment() in the annotation.
  - Under the hood, RestateRunner uses Testcontainers and opens ports 8080 (ingress) and 9070 (admin). Docker must be available.
- Adding tests
  - Java tests: place under src/test/java and name *Test.java (JUnit 5). Kotlin tests under src/test/kotlin.
  - Dependencies are already configured in most modules (e.g., common includes testImplementation(libs.junit.jupiter) and testImplementation(libs.assertj)). If adding tests to a module without these, add them in that module's build.gradle.kts.
  - For Restate-based tests, add a dependency on sdk-testing if not already present and use the annotations provided in dev.restate.sdk.testing.*.
- Example test run (verified locally)
  - A simple JUnit 5 test was created temporarily in :common and executed via:
    ./gradlew :common:test --no-daemon
  - The build succeeded. The temporary test file was then removed to avoid polluting the repo.

Development and debugging tips
- Formatting and license headers
  - Run formatting checks: ./gradlew spotlessCheck
  - Apply formatting and headers: ./gradlew spotlessApply
  - Any newly added source files must include the license header from config/license-header (Spotless can apply it).
- Dependency and license compliance
  - The check task depends on checkLicense, which uses allowed-licenses.json and normalizer config under config/. If you add new dependencies, ensure license reporting stays green.
- Version management
  - Add/upgrade dependencies in gradle/libs.versions.toml. Prefer using the version catalog aliases (libs.*) in module build files. The overall SDK version is controlled by versions.restate.
- Docs
  - Aggregated Javadoc: ./gradlew :sdk-aggregated-javadocs:javadoc
  - Kotlin docs: ./gradlew dokkaHtmlMultiModule
- Docker-based tests
  - If integration tests fail locally while CI is green, verify Docker daemon availability and that the Restate image is accessible. You may pre-pull CI's image: docker pull ghcr.io/restatedev/restate:main
- IDE setup
  - Use Gradle import. The toolchain resolver will fetch JDK 17 automatically. Ensure your IDE respects the Gradle JVM and uses language level 17 for compilation.

Troubleshooting
- Classpath conflicts when running Dokka: The root buildscript pins Jackson modules to 2.17.1 specifically to avoid Dokka bringing unshaded variants that break other plugins. Keep these overrides if upgrading Dokka.
- If tests that rely on Testcontainers hang on startup, check network/firewall settings and whether Testcontainers can communicate with Docker. You can enable more verbose logs with TESTCONTAINERS_LOG_LEVEL=DEBUG.

Housekeeping
- Do not commit temporary tests used for local verification; keep the tree clean.
- Before publishing or opening PRs, run: ./gradlew clean build
