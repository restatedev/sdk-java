// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package my.restate.sdk.examples;

import dev.restate.sdk.Context;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Service;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.http.vertx.RestateHttpServer;
import java.time.Duration;

@Service
public class Greeter {

  public record Greeting(String name) {}

  public record GreetingResponse(String message) {}

  @Handler
  public GreetingResponse greet(Context ctx, Greeting req) {
    //      throw new IllegalArgumentException("Greeting not implemented");
    ctx.sleep(Duration.ofMinutes(1));
    //      throw new TerminalException("bla");
    // Respond to caller
    return new GreetingResponse("You said hi to " + req.name + "!");
  }

  public static void main(String[] args) {
    RestateHttpServer.listen(
        Endpoint.bind(new Greeter(), conf -> conf.inactivityTimeout(Duration.ofMinutes(10))));
  }
}
