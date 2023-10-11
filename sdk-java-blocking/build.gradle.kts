// Without these suppressions version catalog usage here and in other build
// files is marked red by IntelliJ:
// https://youtrack.jetbrains.com/issue/KTIJ-19369.
@Suppress(
    "DSL_SCOPE_VIOLATION",
    "MISSING_DEPENDENCY_CLASS",
    "UNRESOLVED_REFERENCE_WRONG_RECEIVER",
    "FUNCTION_CALL_EXPECTED")
plugins {
  `java-library`
  idea
  `maven-publish`
}

dependencies { api(project(":sdk-core")) }

publishing {
  publications {
    register<MavenPublication>("maven") {
      groupId = "dev.restate.sdk"
      artifactId = "sdk-java-blocking"

      from(components["java"])
    }
  }
}
