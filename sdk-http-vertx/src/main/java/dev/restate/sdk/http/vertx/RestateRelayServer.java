// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.http.vertx;

import dev.restate.sdk.core.RelayTunnel;
import dev.restate.sdk.endpoint.Endpoint;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Serve a Restate {@link Endpoint} through an embedded <b>relay tunnel</b> instead of a public
 * port.
 *
 * <p>Boots a normal Vert.x HTTP/2 (h2c) server bound to {@code 127.0.0.1} on a random port — the
 * same server {@link RestateHttpServer} builds, so request dispatch is untouched — then starts a
 * {@link RelayTunnel} pointed at that port. The tunnel registers as a relay receiver and bridges
 * each forwarded request over the loopback socket into this server. The service author swaps {@code
 * RestateHttpServer.listen(endpoint)} for {@code RestateRelayServer.listen(endpoint, config)}; the
 * service code and {@code HttpEndpointRequestHandler} are unchanged (the forwarded {@code :path} is
 * the SDK-relative tail, so paths and request-identity are intact).
 *
 * <p>Requires JDK 23+ (the native FFM tunnel; see {@link RelayTunnel}).
 *
 * <pre>
 * RelayTunnel tunnel = RestateRelayServer.listen(
 *     endpoint,
 *     new RelayTunnel.Config("relay.example:8080", "myenv", "mytunnel", "api-key"));
 * </pre>
 */
public final class RestateRelayServer {

  private static final Logger LOG = LogManager.getLogger(RestateRelayServer.class);

  private RestateRelayServer() {}

  /**
   * Bind the local Vert.x server, start the tunnel against it, and register a JVM shutdown hook
   * that stops the tunnel and closes the server. Blocks until the local server is bound. Returns
   * the live {@link RelayTunnel} (also {@link AutoCloseable}) for explicit lifecycle control.
   */
  public static RelayTunnel listen(Endpoint endpoint, RelayTunnel.Config config) {
    Vertx vertx = Vertx.vertx();
    HttpServer server = RestateHttpServer.fromEndpoint(vertx, endpoint);

    int port;
    try {
      port =
          server
              .listen(0, "127.0.0.1")
              .toCompletionStage()
              .toCompletableFuture()
              .join()
              .actualPort();
    } catch (RuntimeException e) {
      vertx.close();
      throw e;
    }
    LOG.info("Relay-tunnel local HTTP/2 server listening on 127.0.0.1:{}", port);

    RelayTunnel tunnel;
    try {
      tunnel = RelayTunnel.start(config.withLocalPort(port));
    } catch (RuntimeException e) {
      server.close();
      vertx.close();
      throw e;
    }

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    tunnel.stop();
                  } catch (Throwable t) {
                    LOG.warn("Error stopping relay tunnel on shutdown", t);
                  }
                  server.close();
                  vertx.close();
                },
                "restate-relay-shutdown"));

    LOG.info(
        "Relay tunnel started for env='{}' tunnel='{}' against relay '{}'",
        config.env(),
        config.tunnel(),
        config.relayAddress());
    return tunnel;
  }

  /** Like {@link #listen(Endpoint, RelayTunnel.Config)}. */
  public static RelayTunnel listen(Endpoint.Builder endpointBuilder, RelayTunnel.Config config) {
    return listen(endpointBuilder.build(), config);
  }
}
