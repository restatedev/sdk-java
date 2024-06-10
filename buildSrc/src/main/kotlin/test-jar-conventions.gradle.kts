configurations { register("testArchive") }

tasks.register<Jar>("testJar") {
    archiveClassifier.set("tests")

    from(project.the<SourceSetContainer>()["test"].output)
}

artifacts { add("testArchive", tasks["testJar"]) }