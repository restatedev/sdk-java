package dev.restate.sdk.http.vertx;

import dev.restate.sdk.endpoint.Endpoint;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;

public class RestateHttpServer {

    public static void listen(Endpoint endpoint) {
        endpoint
    }

    public static void listen(Endpoint endpoint, int port);

    public static HttpServer fromEndpoint(Endpoint endpoint);

    public static HttpServer fromEndpoint(Endpoint endpoint, HttpServerOptions options);

}
