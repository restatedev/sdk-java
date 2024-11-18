[![Documentation](https://img.shields.io/badge/doc-reference-blue)](https://docs.restate.dev)
[![javadoc](https://javadoc.io/badge2/dev.restate/sdk-api/javadoc.svg)](https://javadoc.io/doc/dev.restate)
[![Examples](https://img.shields.io/badge/view-examples-blue)](https://github.com/restatedev/examples)
[![Discord](https://img.shields.io/discord/1128210118216007792?logo=discord)](https://discord.gg/skW3AZ6uGd)
[![Twitter](https://img.shields.io/twitter/follow/restatedev.svg?style=social&label=Follow)](https://twitter.com/intent/follow?screen_name=restatedev)

# Restate JVM SDK

[Restate](https://restate.dev/) is a system for easily building resilient applications using _distributed durable async/await_. This repository contains the Restate SDK for writing services using JVM languages.

This SDK features:

- Implement Restate services using either:
  - Java
  - Kotlin coroutines
- Deploy Restate services as:
  - Non-blocking HTTP servers
  - On AWS Lambda

## Community

* 🤗️ [Join our online community](https://discord.gg/skW3AZ6uGd) for help, sharing feedback and talking to the community.
* 📖 [Check out our documentation](https://docs.restate.dev) to get quickly started!
* 📣 [Follow us on Twitter](https://twitter.com/restatedev) for staying up to date.
* 🙋 [Create a GitHub issue](https://github.com/restatedev/sdk-java/issues) for requesting a new feature or reporting a problem.
* 🏠 [Visit our GitHub org](https://github.com/restatedev) for exploring other repositories.

## Using the SDK

### tl;dr Use project templates

To get started, follow the [Java quickstart](https://docs.restate.dev/get_started/quickstart).

### Setup a project (Java)

Scaffold a project using the build tool of your choice. For example, with Gradle (Kotlin script):

```
gradle init --type java-application
```

Add the runtime dependency [sdk-api](sdk-api) and the annotation processor dependency [sdk-api-gen](sdk-api-gen):

```
annotationProcessor("dev.restate:sdk-api-gen:1.2.0")
implementation("dev.restate:sdk-api:1.2.0")
```

### Setup a project (Kotlin)

Scaffold a project using the build tool of your choice. For example, with Gradle (Kotlin script):

```
gradle init --type kotlin-application
```

Add the [Kotlin symbol processing](https://kotlinlang.org/docs/ksp-quickstart.html#use-your-own-processor-in-a-project) plugin:

```
plugins {
    id("com.google.devtools.ksp") version "2.0.0-1.0.21"
}
```

Add the runtime dependency [sdk-api-kotlin](sdk-api-kotlin) and the ksp dependency [sdk-api-gen](sdk-api-kotlin-gen):

```
ksp("dev.restate:sdk-api-kotlin-gen:1.2.0")
implementation("dev.restate:sdk-api-kotlin:1.2.0")
```

### Implement your first Restate component (Java)

Implement your first virtual object in a new class, for example:

```java
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.VirtualObject;
import dev.restate.sdk.JsonSerdes;
import dev.restate.sdk.common.StateKey;

@VirtualObject
public class Greeter {

  private static final StateKey<Long> COUNT = StateKey.of("total", JsonSerdes.LONG);

  @Handler
  public String greet(ObjectContext ctx, String name) {
    long count = ctx.get(COUNT).orElse(0L);
    ctx.set(COUNT, count + 1);

    return String.format("Hello %s for the %d time!", name, count);
  }
}
```

When using composite types/POJOs for input/output, [Jackson Databind](https://github.com/FasterXML/jackson) will be used. The Jackson dependency is not automatically included, you must add it with [`sdk-serde-jackson`](sdk-serde-jackson):

```
implementation("dev.restate:sdk-serde-jackson:1.2.0")
```

If you want to store types/POJOs in state, use `JacksonSerdes`:

```java
private static final StateKey<Person> PERSON = StateKey.of("person", JacksonSerdes.of(Person.class));
```

### Implement your first Restate component (Kotlin)

Implement your first virtual object in a new class, for example:

```kotlin
import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.VirtualObject
import dev.restate.sdk.kotlin.*

@VirtualObject
class Greeter {
  companion object {
    private val COUNT = KtStateKey.json<Long>("total")
  }

  @Handler
  suspend fun greet(context: ObjectContext, name: String): String {
    val count = context.get(COUNT) ?: 0L
    context.set(COUNT, count + 1)
    return "Hello $name for the $count time!"
  }
}
```

When using composite data types for input/output, [`kotlinx.serialization`](https://github.com/Kotlin/kotlinx.serialization?tab=readme-ov-file#setup) will be used.

### Deploy the service (HTTP Server)

To deploy the Restate service as HTTP server, add [`sdk-http-vertx`](sdk-http-vertx) to the dependencies. For example, in Gradle:

```
implementation("dev.restate:sdk-http-vertx:1.2.0")
```

To deploy the service, add the following code to the `main`. For example in Java:

```java
public static void main(String[] args) {
  RestateHttpEndpointBuilder.builder()
        .bind(new Greeter())
        .buildAndListen();
}
```

In Kotlin:

```kotlin
fun main() {
  RestateHttpEndpointBuilder.builder()
          .bind(Greeter())
          .buildAndListen()
}
```

Execute the project. For example, using Gradle:

```
gradle run
```

### Deploy the service (AWS Lambda)

To deploy the Restate service as Lambda, add [`sdk-lambda`](sdk-lambda) to the dependencies. For example, in Gradle:

```
implementation("dev.restate:sdk-lambda:1.2.0")
```

Configure the build tool to generate Fat-JARs, which are required by AWS Lambda to correctly load the JAR. For example, using Gradle:

```
plugins {
  // ...
  // The shadow plugin generates a shadow JAR ready for AWS Lambda
  id("com.github.johnrengelman.shadow").version("7.1.2")
  // ...
}
```

Now create the Lambda handler invoking the service. For example, in Java:

```java
public class MyLambdaHandler extends BaseRestateLambdaHandler {
  @Override
  public void register(RestateLambdaEndpointBuilder builder) {
    builder.bind(new Greeter());
  }
}
```

In Kotlin:

```kotlin
class MyLambdaHandler : BaseRestateLambdaHandler {
  override fun register(builder: RestateLambdaEndpointBuilder) {
    builder.bind(Greeter())
  }
}
```

Now build the Fat-JAR. For example, using Gradle:

```
gradle shadowJar
```

You can now upload the generated Jar in AWS Lambda, and configure `MyLambdaHandler` as the Lambda class in the AWS UI.

### Additional setup

#### Logging

The SDK uses log4j2 as logging facade. To enable logging, add the `log4j2` implementation to the dependencies:

```
implementation("org.apache.logging.log4j:log4j-core:2.23.0")
```

And configure the logging adding the file `resources/log4j2.properties`:

```
# Set to debug or trace if log4j initialization is failing
status = warn

# Console appender configuration
appender.console.type = Console
appender.console.name = consoleLogger
appender.console.layout.type = PatternLayout
appender.console.layout.pattern = %d{yyyy-MM-dd HH:mm:ss} %-5p %notEmpty{[%X{restateInvocationTarget}]}%notEmpty{[%X{restateInvocationId}]} %c - %m%n

# Filter out logging during replay
appender.console.filter.replay.type = ContextMapFilter
appender.console.filter.replay.onMatch = DENY
appender.console.filter.replay.onMismatch = NEUTRAL
appender.console.filter.replay.0.type = KeyValuePair
appender.console.filter.replay.0.key = restateInvocationStatus
appender.console.filter.replay.0.value = REPLAYING

# Restate logs to debug level
logger.app.name = dev.restate
logger.app.level = info
logger.app.additivity = false
logger.app.appenderRef.console.ref = consoleLogger

# Root logger
rootLogger.level = info
rootLogger.appenderRef.stdout.ref = consoleLogger
```

The SDK injects the following additional metadata to the logging context that can be used for filtering as well:

* `restateInvocationTarget`: invocation target, e.g. `counter.Counter/Add`.
* `restateInvocationId`: Invocation identifier, to be used in Restate observability tools. See https://docs.restate.dev/operate/invocation#invocation-identifier.
* `restateInvocationStatus`: Invocation status, can be `WAITING_START`, `REPLAYING`, `PROCESSING`, `CLOSED`.

When assembling fat-jars, make sure to enable merging META-INF/services files. For more info, see https://github.com/apache/logging-log4j2/issues/2099.

#### Tracing with OpenTelemetry

The SDK can generate additional tracing information on top of what Restate already publishes. See https://docs.restate.dev/operate/monitoring/tracing to configure Restate tracing.

You can the additional SDK tracing information by configuring the `OpenTelemetry` in the `RestateHttpEndpointBuilder`/`LambdaRestateServer`.

For example, to set up tracing using environment variables, add the following modules to your dependencies:

```
implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure:1.38.0")
implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.38.0")
```

And then configure it in the Restate builder:

```java
.withOpenTelemetry(AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk())
```

By exporting the following environment variables the OpenTelemetry SDK will be automatically configured to push traces:

```shell
export OTEL_SERVICE_NAME=my-service
export OTEL_TRACES_SAMPLER=always_on
export OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://localhost:14250
```

Please refer to the [Opentelemetry manual instrumentation documentation](https://opentelemetry.io/docs/instrumentation/java/manual/#manual-instrumentation-setup) and the [autoconfigure documentation](https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk-extensions/autoconfigure/README.md) for more info.

## Versions

This library follows [Semantic Versioning](https://semver.org/).

The compatibility with Restate is described in the following table:

| Restate Server\sdk-java | 1.0 | 1.1 | 1.2 |
|-------------------------|-----|-----|-----|
| 1.0                     | ✅   | ✅   | ❌   |
| 1.1                     | ✅   | ✅   | ✅   |

## Contributing

We’re excited if you join the Restate community and start contributing!
Whether it is feature requests, bug reports, ideas & feedback or PRs, we appreciate any and all contributions.
We know that your time is precious and, therefore, deeply value any effort to contribute!

### Building the SDK locally

Prerequisites:

- JDK >= 11
- Docker or Podman

To build the SDK:

```shell
./gradlew build
```

To run the tests:

```shell
./gradlew check
```

To publish local snapshots of the project:

```shell
./gradlew -DskipSigning publishToMavenLocal
```

To update the [`service-protocol`](https://github.com/restatedev/service-protocol/) git subtree:

```shell
git subtree pull --prefix sdk-core/src/main/service-protocol/ git@github.com:restatedev/service-protocol.git main --squash
```
