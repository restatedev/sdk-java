// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.http.vertx.testservices;

import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.VirtualObject;
import dev.restate.sdk.common.JsonSerdes;
import dev.restate.sdk.common.StateKey;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@VirtualObject
public class BlockingGreeter {

  private static final Logger LOG = LogManager.getLogger(BlockingGreeter.class);
  public static final StateKey<Long> COUNTER = StateKey.of("counter", JsonSerdes.LONG);

  @Handler
  public String greet(ObjectContext context, String request) {
    LOG.info("Greet invoked!");

    var count = context.get(COUNTER).orElse(0L) + 1;
    context.set(COUNTER, count);

    context.sleep(Duration.ofSeconds(1));

    return "Hello " + request + ". Count: " + count;
  }
}
