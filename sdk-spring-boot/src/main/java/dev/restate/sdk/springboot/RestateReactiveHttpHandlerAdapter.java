// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot;

import dev.restate.common.Slice;
import dev.restate.sdk.core.EndpointRequestHandler;
import dev.restate.sdk.core.ProtocolException;
import dev.restate.sdk.core.RequestProcessor;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.endpoint.HeadersAccessor;
import dev.restate.sdk.version.Version;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.FlowAdapters;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Spring Reactive {@link HttpHandler} adapter for {@link Endpoint}. */
public class RestateReactiveHttpHandlerAdapter implements HttpHandler {

  private static final Logger LOG = LogManager.getLogger(RestateReactiveHttpHandlerAdapter.class);

  private static final String X_RESTATE_SERVER_HEADER = "x-restate-server";
  private static final String X_RESTATE_SERVER_VALUE =
      "restate-sdk-java-spring/" + Version.VERSION + "_" + Version.GIT_HASH;

  private final EndpointRequestHandler endpoint;
  private final boolean enableBidirectionalStreaming;

  public RestateReactiveHttpHandlerAdapter(
      Endpoint endpoint, boolean enableBidirectionalStreaming) {
    this.endpoint = EndpointRequestHandler.create(endpoint);
    this.enableBidirectionalStreaming = enableBidirectionalStreaming;
  }

  @Override
  public @NonNull Mono<Void> handle(
      @NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response) {
    response.getHeaders().set(X_RESTATE_SERVER_HEADER, X_RESTATE_SERVER_VALUE);

    RequestProcessor requestProcessor;
    try {
      requestProcessor =
          this.endpoint.processorForRequest(
              request.getPath().value(),
              new HeadersAccessor() {
                @Override
                public Iterable<String> keys() {
                  return request.getHeaders().keySet();
                }

                @Override
                public @Nullable String get(String key) {
                  return request.getHeaders().getFirst(key);
                }
              },
              ThreadContext::put,
              Executors.newSingleThreadExecutor(),
              enableBidirectionalStreaming);
    } catch (Exception e) {
      LOG.warn("Error when handling the request", e);
      response.setStatusCode(
          HttpStatusCode.valueOf(
              (e instanceof ProtocolException protocolException)
                  ? protocolException.getCode()
                  : 500));
      response.getHeaders().setContentType(MediaType.TEXT_PLAIN);
      return response.writeWith(
          Mono.just(
              response.bufferFactory().wrap(e.getMessage().getBytes(StandardCharsets.UTF_8))));
    }

    // Set response headers
    response.setStatusCode(HttpStatusCode.valueOf(requestProcessor.statusCode()));
    response.getHeaders().setContentType(MediaType.valueOf(requestProcessor.responseContentType()));

    // Start async processing for streaming support
    var reactiveStreamProcessor = FlowAdapters.toProcessor(requestProcessor);
    request
        .getBody()
        .flatMapIterable(db -> (Iterable<ByteBuffer>) db::readableByteBuffers)
        .map(Slice::wrap)
        .subscribe(reactiveStreamProcessor);
    return response.writeAndFlushWith(
        Flux.from(reactiveStreamProcessor)
            .map(s -> Mono.just(response.bufferFactory().wrap(s.asReadOnlyByteBuffer()))));
  }
}
