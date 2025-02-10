// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.http.vertx;

import dev.restate.sdk.endpoint.Endpoint;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Endpoint builder for a Restate HTTP Endpoint using Vert.x, to serve Restate services.
 *
 * <p>This endpoint supports the Restate HTTP/2 Streaming component Protocol.
 *
 * <p>Example usage:
 *
 * <pre>
 * public static void main(String[] args) {
 *   Endpoint endpoint = Endpoint.builder()
 *     .bind(new Counter())
 *     .build();
 *
 *   RestateHttpServer.listen(endpoint);
 * }
 * </pre>
 */
public class RestateHttpServer {

  private static final Logger LOG = LogManager.getLogger(RestateHttpServer.class);

  private static int DEFAULT_PORT =
      Optional.ofNullable(System.getenv("PORT")).map(Integer::parseInt).orElse(9080);
  private static HttpServerOptions DEFAULT_OPTIONS =
      new HttpServerOptions()
          .setInitialSettings(new Http2Settings().setMaxConcurrentStreams(Integer.MAX_VALUE));

  /**
   * Start serving the provided {@code endpoint} on the port specified by the environment variable
   * {@code PORT}, or alternatively on the default {@code 9080} port.
   *
   * <p>NOTE: this method will block for opening the socket and reserving the port. If you need a
   * non-blocking variant, manually create the server with {@link #fromEndpoint(Endpoint)} and start
   * listening it.
   *
   * @return The listening port
   */
  public static int listen(Endpoint endpoint) {
    return handleStart(fromEndpoint(endpoint).listen(DEFAULT_PORT));
  }

  /**
   * Start serving the provided {@code endpoint} on the specified port.
   *
   * <p>NOTE: this method will block for opening the socket and reserving the port. If you need a
   * non-blocking variant, manually create the server with {@link #fromEndpoint(Endpoint)} and start
   * listening it.
   *
   * @return The listening port
   */
  public static int listen(Endpoint endpoint, int port) {
    return handleStart(fromEndpoint(endpoint).listen(port));
  }

  /** Create a Vert.x {@link HttpServer} from the provided endpoint. */
  public static HttpServer fromEndpoint(Endpoint endpoint) {
    return fromEndpoint(endpoint, DEFAULT_OPTIONS);
  }

  /**
   * Create a Vert.x {@link HttpServer} from the provided endpoint, with the given {@link
   * HttpServerOptions}.
   */
  public static HttpServer fromEndpoint(Endpoint endpoint, HttpServerOptions options) {
    return fromEndpoint(Vertx.vertx(), endpoint, options);
  }

  /** Create a Vert.x {@link HttpServer} from the provided endpoint. */
  public static HttpServer fromEndpoint(Vertx vertx, Endpoint endpoint) {
    return fromEndpoint(vertx, endpoint, DEFAULT_OPTIONS);
  }

  /**
   * Create a Vert.x {@link HttpServer} from the provided endpoint, with the given {@link
   * HttpServerOptions}.
   */
  public static HttpServer fromEndpoint(Vertx vertx, Endpoint endpoint, HttpServerOptions options) {
    HttpServer server = vertx.createHttpServer(options);
    server.requestHandler(HttpEndpointRequestHandler.fromEndpoint(endpoint));
    return server;
  }

  private static int handleStart(Future<HttpServer> fut) {
    try {
      HttpServer server = fut.toCompletionStage().toCompletableFuture().join();
      LOG.info("Restate HTTP Endpoint server started on port {}", server.actualPort());
      return server.actualPort();
    } catch (CompletionException e) {
      LOG.error("Restate HTTP Endpoint server start failed", e.getCause());
      sneakyThrow(e.getCause());
      // This is never reached
      return -1;
    }
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
    throw (E) e;
  }
}
