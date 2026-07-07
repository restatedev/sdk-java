// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package my.restate.sdk.examples;

import dev.restate.sdk.Restate;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Service;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.http.vertx.RestateHttpServer;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Service
public class Greeter {

  private static final Logger LOG = LogManager.getLogger(Greeter.class);

  public record Greeting(String name) {}

  public record GreetingResponse(String message) {}

  @Handler
  public GreetingResponse greet(Greeting req) {
    Restate.sleep(Duration.ofSeconds(1));

    // Respond to caller
    return new GreetingResponse(
        "You said hi to "
            + req.name
            + " for the "
            + Restate.virtualObject(Counter.class, req.name).getAndAdd(1).newValue()
            + "th time!");
  }

  public static void main(String[] args) {
    RestateHttpServer.listen(Endpoint.bind(new Greeter()).bind(new Counter()));
  }
}
