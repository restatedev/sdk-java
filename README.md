# Restate JVM SDK

[Restate](https://restate.dev/) is a system for easily building resilient applications using _distributed durable async/await_. This repository contains the Restate SDK for writing services using JVM languages.

This SDK features:

- Implement Restate services using either:
  - Java
  - Kotlin coroutines
- Reuse the existing gRPC/Protobuf ecosystem to define service interfaces
- Deploy Restate services as:
  - Non-blocking HTTP servers
  - On AWS Lambda

Check [Restate GitHub](https://github.com/restatedev/) or the [docs](https://docs.restate.dev/) for further details.

## Using the SDK

### tl;dr Use project templates

To get started, check out a template project in https://github.com/restatedev/examples/tree/main/jvm.

### Setup a project (Java)

Scaffold a project using the build tool of your choice. For example, with Gradle (Kotlin script):

```
gradle init --type java-application
```

Add the dependency [sdk-java-blocking](sdk-java-blocking):

```
implementation("dev.restate.sdk:sdk-java-blocking:1.0-SNAPSHOT")
```

Now you need to configure the protobuf plugin to build your Protobuf contracts. For example, with Gradle (Kotlin script):

```kts
import com.google.protobuf.gradle.id

plugins {
  // ...
  id("com.google.protobuf") version "0.9.1"
  // ...
}

dependencies {
  // ...
  // You need the following dependencies to compile the generated code
  implementation("com.google.protobuf:protobuf-java:3.24.3")
  implementation("io.grpc:grpc-stub:1.58.0")
  implementation("io.grpc:grpc-protobuf:1.58.0")
  compileOnly("org.apache.tomcat:annotations-api:6.0.53")
}

// Configure protoc plugin
protobuf {
  protoc { artifact = "com.google.protobuf:protoc:3.24.3" }

  plugins {
    // The Restate plugin depends on the gRPC generated code
    id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:1.58.0" }
    id("restate") { artifact = "dev.restate.sdk:protoc-gen-restate-java-blocking:1.0-SNAPSHOT:all@jar" }
  }

  generateProtoTasks {
    all().forEach {
      it.plugins {
        id("grpc")
        id("restate")
      }
    }
  }
}
```

For Maven you can use the [xolstice Protobuf plugin](https://www.xolstice.org/protobuf-maven-plugin/index.html).

### Setup a project (Kotlin)

Scaffold a project using the build tool of your choice. For example, with Gradle (Kotlin script):

```
gradle init --type kotlin-application
```

Add the dependency [`sdk-kotlin`](sdk-kotlin):

```
implementation("dev.restate.sdk:sdk-kotlin:1.0-SNAPSHOT")
```

Now you need to configure the protobuf plugin to build your Protobuf contracts. For example, with Gradle (Kotlin script):

```kts
import com.google.protobuf.gradle.id

plugins {
  // ...
  id("com.google.protobuf") version "0.9.1"
  // ...
}

dependencies {
  // ...
  // You need the following dependencies to compile the generated code
  implementation("com.google.protobuf:protobuf-java:3.24.3")
  implementation("com.google.protobuf:protobuf-kotlin:3.24.3")
  implementation("io.grpc:grpc-stub:1.58.0")
  implementation("io.grpc:grpc-protobuf:1.58.0")
  implementation("io.grpc:grpc-kotlin-stub:1.4.0") { exclude("javax.annotation", "javax.annotation-api") }
  compileOnly("org.apache.tomcat:annotations-api:6.0.53")
}

// Configure protoc plugin
protobuf {
  protoc { artifact = "com.google.protobuf:protoc:3.24.3" }

  plugins {
    // The gRPC Kotlin plugin depends on the gRPC generated code
    id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:1.58.0" }
    id("grpckt") { artifact = "io.grpc:protoc-gen-grpc-kotlin:1.4.0:jdk8@jar" }
  }

  generateProtoTasks {
    all().forEach {
      it.plugins {
        id("grpc")
        id("grpckt")
      }
      it.builtins {
        // The Kotlin codegen depends on the Java generated code
        java {}
        id("kotlin")
      }
    }
  }
}
```

### Add the Protobuf contract

Now you can add the Protobuf contract under `src/main/proto`. For example:

```protobuf
syntax = "proto3";
package greeter;

option java_package = "my.greeter";
option java_outer_classname = "GreeterProto";

import "dev/restate/ext.proto";

service Greeter {
  option (dev.restate.ext.service_type) = KEYED;

  rpc Greet (GreetRequest) returns (GreetResponse);
}

message GreetRequest {
  string name = 1 [(dev.restate.ext.field) = KEY];
}

message GreetResponse {
  string message = 1;
}
```

By using the Gradle or Maven plugin, the code is automatically re-generated on every build.

### Implement the service (Java)

Implement the service in a new class, for example:

```java
public class Greeter extends GreeterRestate.GreeterRestateImplBase {

  private static final StateKey<Long> COUNT = StateKey.of("total", CoreSerdes.LONG);

  @Override
  public GreetResponse greet(RestateContext ctx, GreetRequest request) {
    long count = ctx.get(COUNT).orElse(0L);
    ctx.set(COUNT, count + 1);

    return GreetResponse.newBuilder()
      .setMessage(String.format("Hello %s for the %d time!", request.getName(), count))
      .build();
  }
}
```

If you want to use POJOs for state, check [how to use Jackson](#state-serde-using-jackson).

### Implement the service (Kotlin)

Implement the service in a new class, for example:

```kotlin
class Greeter :
        // Use Dispatchers.Unconfined as the Executor/thread pool is managed by the SDK itself.
        GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined),
        RestateCoroutineService {
  companion object {
    private val COUNT = StateKey.of("total", CoreSerdes.LONG)
  }

  override suspend fun greet(request: GreetRequest): GreetResponse {
    val ctx = restateContext()
    val count = ctx.get(COUNT) ?: 0L
    ctx.set(COUNT, count + 1)
    return greetResponse { message = "Hello ${request.name} for the $count time!" }
  }
}
```

If you want to use POJOs for state, check [how to use Jackson](#state-serde-using-jackson).

### Deploy the service (HTTP Server)

To deploy the Restate service as HTTP server, add [`sdk-http-vertx`](sdk-http-vertx) to the dependencies. For example, in Gradle:

```
implementation("dev.restate.sdk:sdk-http-vertx:1.0-SNAPSHOT")
```

To deploy the service, add the following code to the `main`. For example in Java:

```java
public static void main(String[] args) {
  RestateHttpEndpointBuilder.builder()
        .withService(new Greeter())
        .buildAndListen();
}
```

In Kotlin:

```kotlin
fun main() {
  RestateHttpEndpointBuilder.builder()
          .withService(Greeter())
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
implementation("dev.restate.sdk:sdk-lambda:1.0-SNAPSHOT")
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

To deploy the service, create the following class. For example, in Java:

```java
public class LambdaFactory implements LambdaRestateServerFactory {
  @Override
  public LambdaRestateServer create() {
    return LambdaRestateServer.builder().withService(new Greeter()).build();
  }
}
```

In Kotlin:

```kotlin
class LambdaFactory : LambdaRestateServerFactory {
  override fun create(): LambdaRestateServer {
    return LambdaRestateServer.builder().withService(Greeter()).build()
  }
}
```

Add the file `dev.restate.sdk.lambda.LambdaRestateServerFactory` in the project resources in `resources/META-INF/services` containing the following content:

```
LambdaFactory
```

Now build the Fat-JAR. For example, using Gradle:

```
gradle shadowJar
```

You can now upload the generated Jar in AWS Lambda, and configure `dev.restate.sdk.lambda.LambdaHandler` as the Lambda class in the AWS UI.

### Additional setup

#### State ser/de using Jackson

State ser/de is defined by the interface `Serde`. If you want to use [Jackson Databind](https://github.com/FasterXML/jackson) to ser/de POJOs to JSON, add the dependency [`sdk-serde-jackson`](sdk-serde-jackson).

For example, in Gradle:

```
implementation("dev.restate.sdk:sdk-serde-jackson:1.0-SNAPSHOT")
```

And then use `JacksonSerdes`:

```java
private static final StateKey<Person> PERSON = StateKey.of("person", JacksonSerdes.of(Person.class));
```

#### Logging

The SDK uses log4j2 as logging facade. To enable logging, add the `log4j2` implementation to the dependencies:

```
implementation("org.apache.logging.log4j:log4j-core:2.20.0")
```

And configure the logging adding the file `resources/log4j2.properties`:

```
# Set to debug or trace if log4j initialization is failing
status = warn

# Console appender configuration
appender.console.type = Console
appender.console.name = consoleLogger
appender.console.layout.type = PatternLayout
appender.console.layout.pattern = %d{yyyy-MM-dd HH:mm:ss} %-5p %notEmpty{[%X{restateServiceMethod}]}%notEmpty{[%X{restateInvocationId}]} %c - %m%n

# Restate logs to debug level
logger.app.name = dev.restate
logger.app.level = debug
logger.app.additivity = false
logger.app.appenderRef.console.ref = consoleLogger

# Root logger
rootLogger.level = info
rootLogger.appenderRef.stdout.ref = consoleLogger
```

The SDK injects the following additional metadata to the logging context that can be used for filtering as well:

* `restateServiceMethod`: service and method, e.g. `counter.Counter/Add`.
* `restateInvocationId`: Invocation identifier, to be used in Restate observability tools. See https://docs.restate.dev/services/invocation#invocation-identifier.
* `restateInvocationStatus`: Invocation status, can be `WAITING_START`, `REPLAYING`, `PROCESSING`, `CLOSED`.

#### Tracing with OpenTelemetry

The SDK can generate additional tracing information on top of what Restate already publishes. See https://docs.restate.dev/restate/tracing to configure Restate tracing.

You can the additional SDK tracing information by configuring the `OpenTelemetry` in the `RestateHttpEndpointBuilder`/`LambdaRestateServer`.

For example, to set up tracing using environment variables, add the following modules to your dependencies:

```
implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure:1.31.0")
implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.31.0")
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

## Contributing to the SDK

Prerequisites:

- JDK >= 11

To build the SDK:

```shell
./gradlew build
```

To run the tests:

```shell
./gradlew check
```
