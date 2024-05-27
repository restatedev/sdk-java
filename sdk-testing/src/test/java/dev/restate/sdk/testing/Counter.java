// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testing;

import dev.restate.sdk.JsonSerdes;
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.VirtualObject;
import dev.restate.sdk.common.StateKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@VirtualObject(name = "Counter")
public class Counter {

  private static final Logger LOG = LogManager.getLogger(Counter.class);

  private static final StateKey<Long> TOTAL = StateKey.of("total", JsonSerdes.LONG);

  @Handler
  public void reset(ObjectContext ctx) {
    ctx.clearAll();
  }

  @Handler
  public void add(ObjectContext ctx, Long request) {
    long currentValue = ctx.get(TOTAL).orElse(0L);
    long newValue = currentValue + request;
    ctx.set(TOTAL, newValue);
  }

  @Handler
  public long get(ObjectContext ctx) {
    return ctx.get(TOTAL).orElse(0L);
  }

  @Handler
  public CounterUpdateResult getAndAdd(ObjectContext ctx, Long request) {
    LOG.info("Invoked get and add with " + request);

    long currentValue = ctx.get(TOTAL).orElse(0L);
    long newValue = currentValue + request;
    ctx.set(TOTAL, newValue);

    return new CounterUpdateResult(newValue, currentValue);
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
