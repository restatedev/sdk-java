plugins {
    `maven-publish`
    signing
}

project.afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("maven") {
                afterEvaluate {
                    val shadowJar = tasks.findByName("shadowJar")
                    if (shadowJar == null) {
                        from(components["java"])
                    }
                    else {
                        apply(plugin = "com.gradleup.shadow")

                        from(components["shadow"])
                        artifact(tasks["sourcesJar"]!!)
                        artifact(tasks["javadocJar"]!!)

                        afterEvaluate {
                            // Fix for avoiding inclusion of runtime dependencies marked as 'shadow' in MANIFEST Class-Path.
                            // https://github.com/johnrengelman/shadow/issues/324
                            pom.withXml {
                                val rootNode = asElement()
                                val doc = rootNode.ownerDocument

                                val dependenciesNode =
                                    if (rootNode.getElementsByTagName("dependencies").length != 0) {
                                        rootNode.getElementsByTagName("dependencies").item(0)
                                    } else {
                                        rootNode.appendChild(
                                            doc.createElement("dependencies")
                                        )
                                    }

                                project.configurations["shade"].allDependencies.forEach { dep ->
                                    dependenciesNode.appendChild(
                                        doc.createElement("dependency").apply {
                                            appendChild(
                                                doc.createElement("groupId").apply {
                                                    textContent = dep.group
                                                }
                                            )
                                            appendChild(
                                                doc.createElement("artifactId").apply {
                                                    textContent = dep.name
                                                }
                                            )
                                            appendChild(
                                                doc.createElement("version").apply {
                                                    textContent = dep.version
                                                }
                                            )
                                            appendChild(
                                                doc.createElement("scope").apply {
                                                    textContent = "runtime"
                                                }
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                groupId = "dev.restate"
                artifactId = project.name

                pom {
                    name = "Restate SDK :: ${project.name}"
                    description = project.description!!
                    url = "https://github.com/restatedev/sdk-java"
                    inceptionYear = "2023"

                    licenses {
                        license {
                            name = "MIT License"
                            url = "https://opensource.org/license/mit/"
                        }
                    }

                    scm {
                        connection = "scm:git:git://github.com/restatedev/sdk-java.git"
                        developerConnection = "scm:git:ssh://github.com/restatedev/sdk-java.git"
                        url = "https://github.com/restatedev/sdk-java"
                    }

                    developers {
                        developer {
                            name = "Francesco Guardiani"
                            id = "slinkydeveloper"
                            email = "francescoguard@gmail.com"
                        }
                    }
                }
            }
        }
    }

    signing {
        setRequired { !project.hasProperty("skipSigning") }

        val key = System.getenv("MAVEN_CENTRAL_GPG_PRIVATE_KEY") ?: return@signing
        val password = System.getenv("MAVEN_CENTRAL_GPG_PASSPHRASE") ?: return@signing
        val publishing: PublishingExtension by project

        useInMemoryPgpKeys(key, password)
        sign(publishing.publications["maven"])
    }
}