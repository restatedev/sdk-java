# Lambda examples

This directory contains a Java and Kotlin implementation of the [counter service](src/main/proto/counter.proto), running as Lambda service endpoint.

## Package

Run:

```shell
./gradlew shadowJar
```

You'll find the shadowed jar in the `build` directory.

The class to configure in Lambda is `dev.restate.sdk.lambda.LambdaHandler`.

By default, the Java service implementation is used. If you configure with the env variable `USE_KOTLIN`, the Kotlin service implementation will be used instead.
