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
    id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:1.58.0" }
  }

  generateProtoTasks {
    all().forEach {
      it.plugins {
        id("grpc")
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
    id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:1.58.0" }
    id("grpckt") { artifact = "io.grpc:protoc-gen-grpc-kotlin:1.4.0:jdk8@jar" }
  }

  generateProtoTasks {
    all().forEach {
      // We need both java and kotlin codegen(s) because the kotlin protobuf/grpc codegen depends on the java ones
      it.plugins {
        id("grpc")
        id("grpckt")
      }
      it.builtins {
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
public class Greeter extends GreeterGrpc.GreeterImplBase implements RestateBlockingService {

  private static final StateKey<Long> COUNT = StateKey.of("total", Long.class);

  @Override
  public void greet(GreetRequest request, StreamObserver<GreetResponse> responseObserver) {
    RestateContext ctx = restateContext();

    long count = ctx.get(COUNT).orElse(0L);
    ctx.set(COUNT, count + 1);

    responseObserver.onNext(
        GreetResponse.newBuilder()
            .setMessage(String.format("Hello %s for the %d time!", request.getName(), count))
            .build());
    responseObserver.onCompleted();
  }
}
```

### Implement the service (Kotlin)

Implement the service in a new class, for example:

```kotlin
class Greeter :
        // Use Dispatchers.Unconfined as the Executor/thread pool is managed by the SDK itself. 
        GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined),
        RestateCoroutineService {
  companion object {
    private val COUNT = StateKey.of("total", Long::class.java)
  }

  override suspend fun greet(request: GreetRequest): GreetResponse {
    val ctx = restateContext()
    val count = ctx.get(COUNT) ?: 0L
    ctx.set(COUNT, count + 1)
    return greetResponse { message = "Hello ${request.name} for the $count time!" }
  }
}
```

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
