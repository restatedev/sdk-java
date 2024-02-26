# Examples

This directory contains different examples of the SDK features.

For a sample project configuration and more elaborated examples, check out the [examples repo](https://github.com/restatedev/examples).

Available examples:

* [`Counter`](src/main/java/dev/restate/sdk/examples/Counter.java): Shows a simple virtual object using state primitives.
* [`VanillaGrpcCounter`](src/main/java/dev/restate/sdk/examples/VanillaGrpcCounter.java): Same as `Counter` but using the vanilla gRPC code generator output.
* [`CounterKt`](src/main/kotlin/dev/restate/sdk/examples/CounterKt.kt): Same as `Counter` but using Kotlin.

## Package the examples for Lambda

Run:

```shell
./gradlew shadowJar
```

You'll find the shadowed jar in the `build` directory.

The class to configure in Lambda is `my.restate.sdk.examples.LambdaHandler`.

By default, the [`dev.restate.sdk.examples.Counter`](src/main/java/dev/restate/sdk/examples/Counter.java) bindableComponent is deployed. Set the env variable `LAMBDA_FACTORY_SERVICE_CLASS` to one of the available example classes to change the deployed class.

## Running the examples (HTTP)

You can run the Java counter bindableComponent via:

```shell
./gradlew :examples:run
```

You can modify the class to run setting `-PmainClass=<FQCN>`, for example, in order to run the Kotlin implementation:

```shell
./gradlew :examples:run -PmainClass=dev.restate.sdk.examples.CounterKt
```

## Invoking the counter bindableComponent

If you want to invoke the counter bindableComponent via [grpcurl](https://github.com/fullstorydev/grpcurl):

```shell
grpcurl -plaintext -d '{"counter_name": "my_counter"}' localhost:9090 counter.Counter/Get
```

The command assumes that the Restate runtime is reachable under `localhost:9090`.
