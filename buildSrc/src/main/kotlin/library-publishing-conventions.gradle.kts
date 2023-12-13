plugins {
    `maven-publish`
    signing
}

project.afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("maven") {
                groupId = "dev.restate"
                artifactId = project.name

                from(components["java"])

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

        val key = System.getenv("MAVEN_CENTRAL_GPG_KEY") ?: return@signing
        val password = System.getenv("MAVEN_CENTRAL_GPG_PASSPHRASE") ?: return@signing
        val publishing: PublishingExtension by project

        useInMemoryPgpKeys(key, password)
        sign(publishing.publications["maven"])
    }
}