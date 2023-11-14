package dev.restate.sdk.examples;

import dev.restate.sdk.blocking.RestateContext;
import dev.restate.sdk.core.CoreSerdes;
import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.examples.generated.*;
import dev.restate.sdk.http.vertx.RestateHttpEndpointBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Counter extends CounterRestate.CounterRestateImplBase {

  private static final Logger LOG = LogManager.getLogger(Counter.class);

  private static final StateKey<Long> TOTAL = StateKey.of("total", CoreSerdes.LONG);

  @Override
  public void reset(RestateContext ctx, CounterRequest request) {
    restateContext().clear(TOTAL);
  }

  @Override
  public void add(RestateContext ctx, CounterAddRequest request) {
    long currentValue = ctx.get(TOTAL).orElse(0L);
    long newValue = currentValue + request.getValue();
    ctx.set(TOTAL, newValue);
  }

  @Override
  public GetResponse get(RestateContext context, CounterRequest request) {
    long currentValue = restateContext().get(TOTAL).orElse(0L);

    return GetResponse.newBuilder().setValue(currentValue).build();
  }

  @Override
  public CounterUpdateResult getAndAdd(RestateContext context, CounterAddRequest request) {
    LOG.info("Invoked get and add with " + request.getValue());

    RestateContext ctx = restateContext();

    long currentValue = ctx.get(TOTAL).orElse(0L);
    long newValue = currentValue + request.getValue();
    ctx.set(TOTAL, newValue);

    return CounterUpdateResult.newBuilder().setOldValue(currentValue).setNewValue(newValue).build();
  }

  public static void main(String[] args) {
    RestateHttpEndpointBuilder.builder().withService(new Counter()).buildAndListen();
  }
}
