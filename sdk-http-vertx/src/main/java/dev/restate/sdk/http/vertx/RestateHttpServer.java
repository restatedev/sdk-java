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
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
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

  private static final int DEFAULT_PORT =
      Optional.ofNullable(System.getenv("PORT")).map(Integer::parseInt).orElse(9080);
  private static final HttpServerOptions DEFAULT_HTTP_SERVER_OPTIONS = new HttpServerOptions();
  private static final int DEFAULT_EVENT_LOOPS = VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE;

  /**
   * Start serving the provided {@code endpoint} on the port specified by the environment variable
   * {@code PORT}, or alternatively on the default {@code 9080} port.
   *
   * <p>NOTE: this method will block for opening the socket and reserving the port. If you need a
   * non-blocking variant, manually create the server with {@link #fromEndpoint(Endpoint)} and start
   * listening it.
   *
   * @return The listening port, use 0 for random port.
   */
  public static int listen(Endpoint endpoint) {
    return listen(endpoint, DEFAULT_PORT);
  }

  /** Like {@link #listen(Endpoint)} */
  public static int listen(Endpoint.Builder endpointBuilder) {
    return listen(endpointBuilder.build());
  }

  /**
   * Start serving the provided {@code endpoint} on the specified port.
   *
   * <p>NOTE: this method will block for opening the socket and reserving the port. If you need a
   * non-blocking variant, manually create the server with {@link #fromEndpoint(Endpoint)} and start
   * listening it.
   *
   * @return The listening port, use 0 for random port.
   */
  public static int listen(Endpoint endpoint, int port) {
    return listen(HttpEndpointRequestHandler.fromEndpoint(endpoint), port);
  }

  /** Like {@link #listen(Endpoint, int)} */
  public static int listen(Endpoint.Builder endpointBuilder, int port) {
    return listen(endpointBuilder.build(), port);
  }

  /** Like {@link #listen(Endpoint)}, with an already built request handler */
  public static int listen(HttpEndpointRequestHandler requestHandler) {
    return listen(requestHandler, DEFAULT_PORT);
  }

  /** Like {@link #listen(Endpoint, int)}, with an already built request handler */
  public static int listen(HttpEndpointRequestHandler requestHandler, int port) {
    return listenBlocking(requestHandler, port);
  }

  /** Create a Vert.x {@link HttpServer} from the provided endpoint. */
  public static HttpServer fromEndpoint(Endpoint endpoint) {
    return fromEndpoint(endpoint, DEFAULT_HTTP_SERVER_OPTIONS);
  }

  /** Like {@link #fromEndpoint(Endpoint)} */
  public static HttpServer fromEndpoint(Endpoint.Builder endpointBuilder) {
    return fromEndpoint(endpointBuilder.build());
  }

  /**
   * Create a Vert.x {@link HttpServer} from the provided endpoint, with the given {@link
   * HttpServerOptions}.
   */
  public static HttpServer fromEndpoint(Endpoint endpoint, HttpServerOptions options) {
    return fromEndpoint(Vertx.vertx(), endpoint, options);
  }

  /** Like {@link #fromEndpoint(Endpoint, HttpServerOptions)} */
  public static HttpServer fromEndpoint(
      Endpoint.Builder endpointBuilder, HttpServerOptions options) {
    return fromEndpoint(endpointBuilder.build(), options);
  }

  /** Create a Vert.x {@link HttpServer} from the provided endpoint. */
  public static HttpServer fromEndpoint(Vertx vertx, Endpoint endpoint) {
    return fromEndpoint(vertx, endpoint, DEFAULT_HTTP_SERVER_OPTIONS);
  }

  /** Like {@link #fromEndpoint(Vertx, Endpoint)} */
  public static HttpServer fromEndpoint(Vertx vertx, Endpoint.Builder endpointBuilder) {
    return fromEndpoint(vertx, endpointBuilder.build());
  }

  /**
   * Create a Vert.x {@link HttpServer} from the provided endpoint, with the given {@link
   * HttpServerOptions}.
   */
  public static HttpServer fromEndpoint(Vertx vertx, Endpoint endpoint, HttpServerOptions options) {
    return fromHandler(vertx, HttpEndpointRequestHandler.fromEndpoint(endpoint), options);
  }

  /** Like {@link #fromEndpoint(Vertx, Endpoint, HttpServerOptions)} */
  public static HttpServer fromEndpoint(
      Vertx vertx, Endpoint.Builder endpointBuilder, HttpServerOptions options) {
    return fromEndpoint(vertx, endpointBuilder.build(), options);
  }

  /** Create a Vert.x {@link HttpServer} from the provided {@link HttpEndpointRequestHandler}. */
  public static HttpServer fromHandler(HttpEndpointRequestHandler handler) {
    return fromHandler(handler, DEFAULT_HTTP_SERVER_OPTIONS);
  }

  /**
   * Create a Vert.x {@link HttpServer} from the provided {@link HttpEndpointRequestHandler}, with
   * the given {@link HttpServerOptions}.
   */
  public static HttpServer fromHandler(
      HttpEndpointRequestHandler handler, HttpServerOptions options) {
    return fromHandler(Vertx.vertx(), handler, options);
  }

  /** Create a Vert.x {@link HttpServer} from the provided {@link HttpEndpointRequestHandler}. */
  public static HttpServer fromHandler(Vertx vertx, HttpEndpointRequestHandler handler) {
    return fromHandler(vertx, handler, DEFAULT_HTTP_SERVER_OPTIONS);
  }

  /**
   * Create a Vert.x {@link HttpServer} from the provided {@link HttpEndpointRequestHandler}, with
   * the given {@link HttpServerOptions}.
   */
  public static HttpServer fromHandler(
      Vertx vertx, HttpEndpointRequestHandler handler, HttpServerOptions options) {
    HttpServer server = vertx.createHttpServer(options);
    server.requestHandler(handler);
    return server;
  }

  private static int listenBlocking(HttpEndpointRequestHandler handler, int port) {
    String eventLoopsOverride =
        System.getProperty(
            "dev.restate.sdk.http.eventLoops", System.getenv("RESTATE_SDK_HTTP_EVENT_LOOPS"));
    int instances =
        eventLoopsOverride != null ? Integer.parseInt(eventLoopsOverride) : DEFAULT_EVENT_LOOPS;
    Vertx vertx = Vertx.vertx(new VertxOptions().setEventLoopPoolSize(instances));
    // A negative port makes all the server instances share the same random port, whereas port 0
    // would make each instance bind a different random port.
    int listenPort = port == 0 ? -1 : port;
    AtomicInteger actualPort = new AtomicInteger();
    try {
      vertx
          .deployVerticle(
              () ->
                  new RestateServerVerticle(
                      handler, DEFAULT_HTTP_SERVER_OPTIONS, listenPort, actualPort),
              new DeploymentOptions().setInstances(instances))
          .toCompletionStage()
          .toCompletableFuture()
          .join();
      LOG.info(
          "Restate HTTP Endpoint server started on port {} with {} event loop(s)",
          actualPort.get(),
          instances);
      return actualPort.get();
    } catch (CompletionException e) {
      LOG.error("Restate HTTP Endpoint server start failed", e.getCause());
      vertx.close();
      sneakyThrow(e.getCause());
      // This is never reached
      return -1;
    }
  }

  private static final class RestateServerVerticle extends AbstractVerticle {

    private final HttpEndpointRequestHandler handler;
    private final HttpServerOptions options;
    private final int port;
    private final AtomicInteger actualPort;

    private RestateServerVerticle(
        HttpEndpointRequestHandler handler,
        HttpServerOptions options,
        int port,
        AtomicInteger actualPort) {
      this.handler = handler;
      this.options = options;
      this.port = port;
      this.actualPort = actualPort;
    }

    @Override
    public void start(Promise<Void> startPromise) {
      HttpServer server = vertx.createHttpServer(options);
      server.requestHandler(handler);
      server
          .listen(port)
          .onSuccess(
              s -> {
                actualPort.set(s.actualPort());
                startPromise.complete();
              })
          .onFailure(startPromise::fail);
    }
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
    throw (E) e;
  }
}
