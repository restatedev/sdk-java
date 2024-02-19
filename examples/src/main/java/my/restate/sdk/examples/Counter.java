// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package my.restate.sdk.examples;

import dev.restate.sdk.KeyedContext;
import dev.restate.sdk.annotation.Exclusive;
import dev.restate.sdk.annotation.Service;
import dev.restate.sdk.annotation.ServiceType;
import dev.restate.sdk.common.CoreSerdes;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.http.vertx.RestateHttpEndpointBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Service(ServiceType.OBJECT)
public class Counter {

  private static final Logger LOG = LogManager.getLogger(Counter.class);

  private static final StateKey<Long> TOTAL = StateKey.of("total", CoreSerdes.JSON_LONG);

  @Exclusive
  public void reset(KeyedContext ctx) {
    ctx.clearAll();
  }

  @Exclusive
  public void add(KeyedContext ctx, Long request) {
    long currentValue = ctx.get(TOTAL).orElse(0L);
    long newValue = currentValue + request;
    ctx.set(TOTAL, newValue);
  }

  @Exclusive
  public Long get(KeyedContext ctx) {
    return ctx.get(TOTAL).orElse(0L);
  }

  @Exclusive
  public CounterUpdateResult getAndAdd(KeyedContext ctx, Long request) {
    LOG.info("Invoked get and add with " + request);

    long currentValue = ctx.get(TOTAL).orElse(0L);
    long newValue = currentValue + request;
    ctx.set(TOTAL, newValue);

    return new CounterUpdateResult(newValue, currentValue);
  }

  public static void main(String[] args) {
    RestateHttpEndpointBuilder.builder().with(new Counter()).buildAndListen();
  }

  public static class CounterUpdateResult {
    private final Long newValue;
    private final Long oldValue;

    public CounterUpdateResult(Long newValue, Long oldValue) {
      this.newValue = newValue;
      this.oldValue = oldValue;
    }

    public Long getNewValue() {
      return newValue;
    }

    public Long getOldValue() {
      return oldValue;
    }
  }
}
