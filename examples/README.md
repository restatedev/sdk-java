# Examples

This directory contains different examples of the SDK features.

For a sample project configuration and more elaborated examples, check out the [examples repo](https://github.com/restatedev/examples).

Available examples:

* [`Counter`](src/main/java/my/restate/sdk/examples/Counter.java): Shows a simple virtual object using state primitives.
* [`CounterKt`](src/main/kotlin/my/restate/sdk/examples/CounterKt.kt): Same as `Counter` but using Kotlin.
* [`LoanWorkflow`](src/main/java/my/restate/sdk/examples/LoanWorkflow.java): Shows a simple workflow example using the Workflow API.

## Package the examples for Lambda

Run:

```shell
./gradlew shadowJar
```

You'll find the shadowed jar in the `build` directory.

The class to configure in Lambda is `my.restate.sdk.examples.LambdaHandler`.

By default, the [`my.restate.sdk.examples.Counter`](src/main/java/my/restate/sdk/examples/Counter.java) virtual object is deployed. Set the env variable `LAMBDA_FACTORY_SERVICE_CLASS` to one of the available example classes to change the deployed class.

## Running the examples (HTTP)

You can run the Java Counter example via:

```shell
./gradlew :examples:run
```

You can modify the class to run setting `-PmainClass=<FQCN>`, for example, in order to run the Kotlin implementation:

```shell
./gradlew :examples:run -PmainClass=my.restate.sdk.examples.CounterKtKt
```

## Invoking the Counter

Make sure your handlers are registered with your restate server. When you run the example above it'll start a deployment listening on port `9080`. Use the `restate` CLI to let the server know to reach it:
```shell
restate deployment register localhost:9080
```

If you want to invoke the counter virtual object via curl:

```shell
# To add a new value to my-counter
curl http://localhost:8080/Counter/my-counter/add --json "1"
# To get my-counter value
curl http://localhost:8080/Counter/my-counter/get
```

The command assumes that the Restate runtime is reachable under `localhost:8080`.
