// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package my.restate.sdk.examples;

import dev.restate.client.Client;
import dev.restate.sdk.*;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Shared;
import dev.restate.sdk.annotation.VirtualObject;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.http.vertx.RestateHttpServer;
import dev.restate.sdk.types.StateKey;
import java.time.Duration;
import java.util.concurrent.Executors;

import dev.restate.sdk.types.TerminalException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Counter virtual object */
@VirtualObject(name = "Counter")
public class Counter {

  private static final Logger LOG = LogManager.getLogger(Counter.class);

  private static final StateKey<Long> TOTAL = StateKey.of("total", JsonSerdes.LONG);

  /** Reset the counter. */
  @Handler
  public void reset(ObjectContext ctx) {
    ctx.clearAll();
  }

  /** Add the given value to the count. */
  @Handler
  public void add(ObjectContext ctx, long request) {

    // --- From Context

//    // Normal call
//    CallAwaitable<Long> res = ctx.call(
//      CounterRequests.add(request)
//    );
//    // The Awaitable is overriden to let people get invocation id
//    String invocationId = res.invocationId().await();
//    ctx.call(
//            CounterRequests.add(request)
//                    .idempotencyId("my-ass")
//    )
//    // Send
//    Awaitable<String> invocationId = ctx.send(
//      CounterRequests.add(request)
//    )
//    // Code generated client (pretty much for getting started, generation can be disabled)
//    CounterClient.ContextClient client = CounterClient.fromContext(ctx);
//    CallAwaitable<Long> res = client.add(request);
//
//    // --- From ingress
//
//    Client client = Client.connect("http://myass:9080");
//    // Simple
//    client.call(
//            CounterRequests.add(request)
//    )
//    // Complex, get back response headers
//    CompletableFuture<CallResponse<Long>> res = client.callWithInfo(
//            CounterRequests.add(request)
//                    .idempotencyId("myass")
//    )
//    // Get result
//    res.join().result();
//    // Get headers
//    res.join().headers();
//    client.send(
//            CounterRequests.add(request)
//    )
//    // Code generated client (pretty much for getting started, generation can be disabled)
//    CounterClient.IngressClient client = CounterClient.connect("http://myass:9080");
//    Long res = client.add(request);
//
//    ctx.send(
//      CounterRequests.add(request)
//    )

    long currentValue = ctx.get(TOTAL).orElse(0L);
    long newValue = currentValue + request;
    ctx.sleep(Duration.ofSeconds(120));
    ctx.set(TOTAL, newValue);
  }

  /** Get the current counter value. */
  @Shared
  @Handler
  public long get(SharedObjectContext ctx) {
    return ctx.get(TOTAL).orElse(0L);
  }

  /** Add a value, and get both the previous value and the new value. */
  @Handler
  public CounterUpdateResult getAndAdd(ObjectContext ctx, long request) {
    LOG.info("Invoked get and add with {}", request);

    long currentValue = ctx.get(TOTAL).orElse(0L);
    long newValue = currentValue + request;
    ctx.set(TOTAL, newValue);

    return new CounterUpdateResult(newValue, currentValue);
  }

  public static void main(String[] args) {

    // Using defaults
    Endpoint endpoint = Endpoint.builder()
            .bind(new Counter())
            .build();

    // Customize executor
    Endpoint endpoint = Endpoint.builder()
            .bind(new Counter(), HandlerRunner.Options.withExecutor(myAwesomeExecutor))
            .build();



    RestateHttpServer.listen(endpoint);
  }

  public record CounterUpdateResult(long newValue, long oldValue) {}
}
