plugins {
  `java-conventions`
  `kotlin-conventions`
  `java-library`
  `library-publishing-conventions`
}

description = "Restate SDK AWS Lambda integration"

dependencies {
  api(project(":sdk-common"))
  implementation(project(":sdk-core"))

  api(libs.aws.lambda.core)
  api(libs.aws.lambda.events)

  implementation(libs.opentelemetry.api)

  implementation(libs.log4j.api)
}
