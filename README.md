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

To get started, check out a template project in https://github.com/restatedev/examples/tree/main/jvm, or look at the examples in the [examples](examples) directory.

The SDK is composed in modules that you can pick depending on the service you want to build:

- [sdk-java-blocking](sdk-java-blocking) contains the plain Java blocking interface.
- [sdk-kotlin](sdk-kotlin) contains the Kotlin coroutines based interface.
- [sdk-http-vertx](sdk-http-vertx) contains the HTTP server endpoint implementation, based on [Eclipse Vert.x](https://vertx.io).
- [sdk-lambda](sdk-lambda) contains the AWS Lambda endpoint implementation, based on the [official AWS SDK](https://docs.aws.amazon.com/lambda/latest/dg/lambda-java.html).

You need to set up a gRPC code-generator to generate the required Protobuf/gRPC classes.

For example, given the following contract:

```protobuf
service Counter {
  option (dev.restate.ext.service_type) = KEYED;

  rpc Add (AddRequest) returns (google.protobuf.Empty);
  rpc Get (GetRequest) returns (GetResponse);
}

message GetRequest {
  string counter_name = 1 [(dev.restate.ext.field) = KEY];
}

message GetResponse {
  int64 value = 1;
}

message AddRequest {
  string counter_name = 1 [(dev.restate.ext.field) = KEY];
  int64 value = 2;
}
```

A Java implementation looks like the following:

```java
public class Counter extends CounterGrpc.CounterImplBase implements RestateBlockingService {

  private static final StateKey<Long> TOTAL = StateKey.of("total", Long.class);

  @Override
  public void add(AddRequest request, StreamObserver<Empty> responseObserver) {
    RestateContext ctx = restateContext();

    long currentValue = ctx.get(TOTAL).orElse(0L);
    long newValue = currentValue + request.getValue();
    ctx.set(TOTAL, newValue);

    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void get(GetRequest request, StreamObserver<GetResponse> responseObserver) {
    long currentValue = restateContext().get(TOTAL).orElse(0L);

    responseObserver.onNext(GetResponse.newBuilder().setValue(currentValue).build());
    responseObserver.onCompleted();
  }
}
```

A kotlin implementation looks like the following:

```kotlin
class Counter(coroutineContext: CoroutineContext) :
    CounterGrpcKt.CounterCoroutineImplBase(coroutineContext), RestateCoroutineService {

  private val TOTAL = StateKey.of("total", Long::class.java)

  override suspend fun add(request: AddRequest): Empty {
    val currentValue = restateContext().get(TOTAL) ?: 0L
    val newValue = currentValue + add

    restateContext().set(TOTAL, newValue)

    return currentValue to newValue
    return Empty.getDefaultInstance()
  }

  override suspend fun get(request: GetRequest): GetResponse {
    return getResponse { value = getCounter() }
  }
}
```

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
