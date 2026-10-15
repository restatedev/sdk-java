# Restate Integration Client

You can use the Integration API to turn any source of events into Restate invocations.

Use an integration client to send invocations to Restate using the Integration API.

## Installation

Add [`dev.restate:integration-client`](https://central.sonatype.com/artifact/dev.restate/integration-client) to your dependencies.

## Create a client

Create one client for your Restate ingress URL and close it when your integration shuts down:

```java
try (IntegrationClient client = IntegrationClient.builder("http://localhost:8080").build()) {
  // Use client
}
```

## Simple producer

Use `Producer` when your source does not provide stable offsets that you can replay.
The client assigns an offset to each invocation.
Call `flush()` before closing the producer to wait until Restate has durably committed everything you sent:

```java
try (Producer producer = client.newProducer()) {
  for (SourceRecord record : records) {
    producer.send(
        Invocation.create()
            .setServiceName("OrderService")
            .setHandlerName("ingest")
            .putHeader("content-type", "application/json")
            .setBody(record.payload()));
  }
  producer.flush();
}
```

## Exactly once producer

Use `ExactlyOnceProducer` when your source provides stable, strictly increasing offsets,
and choose a producer ID that remains stable across restarts and is unique to that offset sequence.
After `flush()` returns, you can checkpoint the returned offset in your source:

```java
try (ExactlyOnceProducer producer = client.newExactlyOnceProducer("orders/partition-0")) {
  for (SourceRecord record : sourceRecords) {
    producer.send(
        record.offset(),
        Invocation.create()
            .setServiceName("OrderService")
            .setHandlerName("ingest")
            .putHeader("content-type", "application/json")
            .setBody(record.payload()));
  }
  long committedOffset = producer.flush();
  // Checkpoint committedOffset in your source
}
```

## Producer default fields

Producers accept `InvocationMetadata` when every invocation shares fields such as the target service, handler, or headers.
Set defaults to avoid sending over and over the same fields in every invocation.

Fields set on an individual invocation override these defaults.

```java
InvocationMetadata defaults =
    InvocationMetadata.create()
        .setServiceName("OrderService")
        .setHandlerName("ingest")
        .putHeader("content-type", "application/json");

try (Producer producer = client.newProducer(defaults)) {
  for (SourceRecord record : records) {
    producer.send(Invocation.create().setBody(record.payload()));
  }
  producer.flush();
}
```

## More

For configuration options and advanced usage, see the [Integration client Javadocs](https://restatedev.github.io/sdk-java/javadocs/dev/restate/integration/package-summary.html).
