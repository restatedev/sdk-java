plugins { id("io.freefair.aggregate-javadoc") version "8.6" }

rootProject.subprojects
    .filter { it.name != "examples" }
    .forEach {
      it.plugins.withId("java") {
        // Add dependency
        dependencies.javadoc(it)

        tasks.javadoc { classpath += it.configurations["compileClasspath"] }
      }
    }

tasks.javadoc {
  title = "Restate SDK-Java documentation"
  options.windowTitle = "Restate SDK-Java documentation"
}
