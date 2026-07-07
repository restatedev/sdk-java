[![Documentation](https://img.shields.io/badge/doc-reference-blue)](https://docs.restate.dev)
[![Java](https://img.shields.io/badge/Javadoc-blue?logo=readthedocs)](http://restatedev.github.io/sdk-java/javadocs/)
[![Kotlindocs](https://img.shields.io/badge/Kotlindocs-blue?logo=kotlin)](http://restatedev.github.io/sdk-java/ktdocs/)
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

### Prerequisites
- JDK >= 17 (JDK >= 23 recommended — required for the latest Restate features; see [Native access on JDK 23+](#native-access-on-jdk-23))

### tl;dr Use project templates

To get started, follow the [Java quickstart](https://docs.restate.dev/get_started/quickstart?sdk=java) or the [Kotlin quickstart](https://docs.restate.dev/get_started/quickstart?sdk=kotlin).

### Setup a project (Java)

Scaffold a project using the build tool of your choice. For example, with Gradle (Kotlin script):

```
gradle init --type java-application
```

Depending on whether you want to deploy using HTTP or Lambda, add the appropriate dependency:

```kotlin
// For HTTP services
implementation("dev.restate:sdk-java-http:2.8.0")
// For Lambda services
// implementation("dev.restate:sdk-java-lambda:2.8.0")
```

### Setup a project (Kotlin)

Scaffold a project using the build tool of your choice. For example, with Gradle (Kotlin script):

```
gradle init --type kotlin-application
```

Depending on whether you want to deploy using HTTP or Lambda, add the appropriate dependency:

```kotlin
// For HTTP services
implementation("dev.restate:sdk-kotlin-http:2.8.0")
// For Lambda services
// implementation("dev.restate:sdk-kotlin-lambda:2.8.0")
```

### Implement your first Restate component (Java)

Implement your first virtual object in a new class, for example:

```java
import dev.restate.sdk.Restate;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.VirtualObject;
import dev.restate.sdk.common.StateKey;

@VirtualObject
public class Greeter {

  private static final StateKey<Long> COUNT = StateKey.of("total", Long.class);

  @Handler
  public String greet(String name) {
    var state = Restate.state();
    long count = state.get(COUNT).orElse(0L);
    state.set(COUNT, count + 1);

    return String.format("Hello %s for the %d time!", name, count);
  }
}
```

By default, [Jackson Databind](https://github.com/FasterXML/jackson) will be used for serialization/deserialization. You can override this configuration by customizing the `SerdeFactory`, check out the javadocs for more details.

### Implement your first Restate component (Kotlin)

Implement your first virtual object in a new class, for example:

```kotlin
import dev.restate.sdk.annotation.*
import dev.restate.sdk.kotlin.*

@VirtualObject
class Greeter {
  companion object {
    private val COUNT = stateKey("total")
  }

  @Handler
  suspend fun greet(name: String): String {
    val state = state()
    val count = state.get(COUNT) ?: 0L
    state.set(COUNT, count + 1)
    return "Hello $name for the $count time!"
  }
}
```

By default [`kotlinx.serialization`](https://github.com/Kotlin/kotlinx.serialization?tab=readme-ov-file#setup) will be used for serialization/deserialization. You can override this configuration by customizing the `SerdeFactory`, check out the javadocs for more details.

### Deploy the service (HTTP Server)

To deploy the Restate service as HTTP server, add the following code to the `main`. For example in Java:

```java
public static void main(String[] args) {
  RestateHttpServer.listen(
    Endpoint.bind(new Greeter())
  );
}
```

In Kotlin:

```kotlin
fun main() {
  RestateHttpServer.listen(
    endpoint {
      bind(Greeter())
    }
  )
}
```

Execute the project. For example, using Gradle:

```
gradle run
```

### Deploy the service (AWS Lambda)

To deploy the Restate service as Lambda, configure the build tool to generate Fat-JARs, which are required by AWS Lambda to correctly load the JAR.
For example, using Gradle:

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
  public void register(Endpoint.Builder builder) {
    builder.bind(new Greeter());
  }
}
```

In Kotlin:

```kotlin
class MyLambdaHandler : BaseRestateLambdaHandler {
  override fun register(builder: Endpoint.Builder) {
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

#### Native access on JDK 23+

On JDK 23 and later the SDK runs its state machine through the native Restate shared-core library, via
the Java Foreign Function & Memory API. **This is required to support the latest Restate features.** On
older JDKs the SDK falls back to a pure-Java state machine, which is **deprecated and will be removed in
a future release** — so running on JDK 23+ is strongly recommended.

Using the native library requires _native access_ to be enabled for the application. If it isn't, the
SDK still works but the JVM prints a one-time warning at startup (e.g. _"A restricted method ... has
been called ... Use --enable-native-access=ALL-UNNAMED to avoid a warning"_), and a future JDK will turn
that warning into an error — so it's worth enabling.

The cleanest way to enable it **without a command-line flag** is to add this attribute to the manifest
of your application's runnable (fat) jar — for example with the Gradle Shadow plugin:

```kotlin
tasks.shadowJar {
  manifest { attributes("Enable-Native-Access" to "ALL-UNNAMED") }
}
```

(`ALL-UNNAMED` is the only accepted value.) For launchers that don't run the app via `java -jar`
(custom entrypoints, containers, `java -cp`), pass `--enable-native-access=ALL-UNNAMED` directly or via
the `JDK_JAVA_OPTIONS` environment variable.

If you'd rather grant native access **selectively** (the integrity-friendly approach recommended by the
JDK) instead of to the whole class path, put the SDK jars on the **module path** and enable access only
for the module that performs it — `sdk-core` publishes the stable automatic-module name
`dev.restate.sdk.core`:

```
java --module-path libs --enable-native-access=dev.restate.sdk.core ...
```

(`sdk-core` works unchanged on the class path too; the module name is simply ignored there.)

To force the pure-Java state machine instead (no native access needed), set
`-Ddev.restate.sdk.statemachine.disableNewCore=true`. On JDK < 23 the pure-Java state machine is
always used.

#### Logging

The SDK uses log4j2 as logging facade, to configure it add the file `resources/log4j2.properties`:

```properties
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

The dependencies `sdk-java-http`, `sdk-java-lambda`, `sdk-kotlin-http` and `sdk-kotlin-lambda` bring in `log4j-core` by default, but you can easily exclude/override that if you need to.

When assembling fat-jars, make sure to enable merging META-INF/services files. For more info, see https://github.com/apache/logging-log4j2/issues/2099.

## Versions

This library follows [Semantic Versioning](https://semver.org/).

The compatibility with Restate is described in the following table:

| Restate Server\sdk-java | < 2.0            | 2.0 - 2.1 | 2.2 - 2.3        | 2.4 - 2.8        |
|-------------------------|------------------|-----------|------------------|------------------|
| < 1.3                   | ✅                | ❌         | ❌                | ❌                |
| 1.3                     | ✅                | ✅         | ✅ <sup>(1)</sup> | ✅ <sup>(2)</sup> |
| 1.4                     | ✅                | ✅         | ✅                | ✅ <sup>(2)</sup> |
| 1.5 - 1.6               | ⚠ <sup>(3)</sup> | ✅         | ✅                | ✅                |

<sup>(1)</sup> **Note** The new service/handler configuration options `inactivityTimeout`, `abortTimeout`, `idempotencyRetention`, `journalRetention`, `ingressPrivate`, `enableLazyState` work only from Restate 1.4 onward.

<sup>(2)</sup> **Note** The new service/handler configuration option `invocationRetryPolicy` works only from Restate 1.5 onward.

<sup>(3)</sup> **Warning** SDK versions < 2.0 are deprecated, and cannot be registered anymore. Check the [Restate 1.5 release notes](https://github.com/restatedev/restate/releases/tag/v1.5.0) for more info.

## Contributing

We’re excited if you join the Restate community and start contributing!
Whether it is feature requests, bug reports, ideas & feedback or PRs, we appreciate any and all contributions.
We know that your time is precious and, therefore, deeply value any effort to contribute!

### Building the SDK locally

Prerequisites:

- JDK >= 25
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
