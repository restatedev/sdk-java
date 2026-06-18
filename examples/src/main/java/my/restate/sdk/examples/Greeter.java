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
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.HttpServerOptions;
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
    var vertxOptions = new VertxOptions();
    var eventLoopPoolSize = vertxOptions.getEventLoopPoolSize();
    var vertx = Vertx.vertx(new VertxOptions());
    var httpServerOptions =
        new HttpServerOptions().setInitialSettings(new Http2Settings().setMaxConcurrentStreams(10));

    var endpoint = Endpoint.bind(new Greeter()).bind(new Counter()).build();

    for (int i = 0; i < eventLoopPoolSize; i++) {
      vertx.deployVerticle(
          new AbstractVerticle() {
            @Override
            public void start(Promise<Void> startPromise) {
              RestateHttpServer.fromEndpoint(vertx, endpoint, httpServerOptions)
                  .listen(9080)
                  .map(
                      server -> {
                        LOG.info("Server started on port {}", server.actualPort());
                        return (Void) null;
                      })
                  .andThen(startPromise);
            }
          });
    }
  }
}
