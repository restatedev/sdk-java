// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package my.restate.sdk.examples;

import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.Restate;
import dev.restate.sdk.SharedObjectContext;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Name;
import dev.restate.sdk.annotation.Shared;
import dev.restate.sdk.annotation.VirtualObject;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.http.vertx.RestateHttpServer;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Counter virtual object */
@VirtualObject
@Name("BroCounter")
public class Counter {

  private static final Logger LOG = LogManager.getLogger(Counter.class);

  private static final StateKey<Long> TOTAL = StateKey.of("total", Long.class);

  /** Reset the counter. */
  @Handler
  public void reset(ObjectContext ctx) {
    ctx.clearAll();
  }

  /** Add the given value to the count. */
  @Handler
  public void add(long request) {
    var ctx = (ObjectContext) Restate.context();

    long currentValue = ctx.get(TOTAL).orElse(0L);
    long newValue = currentValue + request;
    ctx.sleep(Duration.ofSeconds(120));
    ctx.set(TOTAL, newValue);
  }

  /** Get the current counter value. */
  @Shared
  @Handler
  public long get() {
    var ctx = (SharedObjectContext) Restate.context();
    return ctx.get(TOTAL).orElse(0L);
  }

  /** Add a value, and get both the previous value and the new value. */
  @Handler
  public CounterUpdateResult getAndAdd(long request) {
    var ctx = (ObjectContext) Restate.context();

    LOG.info("Invoked get and add with {}", request);

    long currentValue = ctx.get(TOTAL).orElse(0L);
    long newValue = currentValue + request;
    ctx.set(TOTAL, newValue);

    return new CounterUpdateResult(newValue, currentValue);
  }

  public static void main(String[] args) {
    Endpoint endpoint = Endpoint.builder().bind(new Counter()).build();

    RestateHttpServer.listen(endpoint);
  }

  public record CounterUpdateResult(long newValue, long oldValue) {}
}
