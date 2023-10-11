# HTTP Examples

This directory contains a Java and Kotlin implementation of the [counter service](src/main/proto/counter.proto), running as HTTP service endpoint.

## Running the examples

You can run the Java counter service via:

```shell
./gradlew :examples:run
```

In order to run the Kotlin implementation, you have to specify the main class via `-PmainClass=dev.restate.sdk.examples.CounterKt`:

```shell
./gradlew :examples:run -PmainClass=dev.restate.sdk.examples.CounterKt
```

## Invoking the counter service

If you want to invoke the counter service via [grpcurl](https://github.com/fullstorydev/grpcurl):

```shell
grpcurl -plaintext -d '{"counter_name": "my_counter"}' localhost:9090 counter.Counter/Get
```

The command assumes that the Restate runtime is reachable under `localhost:9090`.
