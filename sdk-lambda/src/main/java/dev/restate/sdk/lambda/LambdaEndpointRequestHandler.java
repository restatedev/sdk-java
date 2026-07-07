// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.lambda;

import static dev.restate.sdk.lambda.LambdaFlowAdapters.*;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import dev.restate.common.Slice;
import dev.restate.sdk.core.EndpointRequestHandler;
import dev.restate.sdk.core.ProtocolException;
import dev.restate.sdk.core.RequestProcessor;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.endpoint.HeadersAccessor;
import dev.restate.sdk.version.Version;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

/** Restate Lambda Endpoint. */
public final class LambdaEndpointRequestHandler {

  private static final Logger LOG = LogManager.getLogger(LambdaEndpointRequestHandler.class);

  private final EndpointRequestHandler endpoint;

  LambdaEndpointRequestHandler(Endpoint endpoint) {
    this.endpoint = EndpointRequestHandler.create(endpoint);
  }

  /** Handle a Lambda request as Restate Lambda endpoint. */
  public APIGatewayProxyResponseEvent handleRequest(
      APIGatewayProxyRequestEvent input, Context context) {
    // Remove trailing path separator
    String path =
        input.getPath().endsWith("/")
            ? input.getPath().substring(0, input.getPath().length() - 1)
            : input.getPath();

    // Parse request body
    final Slice requestBody = parseInputBody(input);
    final ExecutorService coreExecutor = Executors.newSingleThreadExecutor();
    try {
      final BufferedPublisher publisher = new BufferedPublisher(requestBody);
      final ResultSubscriber subscriber = new ResultSubscriber();

      // Create the request processor and wire both streams on the coreExecutor thread, subscribing
      // the OUTPUT before the INPUT. Both run in order on the single-threaded coreExecutor, so the
      // output subscriber is attached before the input starts feeding the state machine; otherwise
      // the input subscription would drive the handler to emit its first output chunk before the
      // output subscriber is attached, and RequestProcessorImpl silently drops output emitted while
      // it has no subscriber. Creating the processor here also keeps the native state machine on
      // the
      // thread that drives it.
      RequestProcessor requestProcessor;
      try {
        requestProcessor =
            coreExecutor
                .submit(
                    () -> {
                      RequestProcessor handler =
                          this.endpoint.processorForRequest(
                              path,
                              HeadersAccessor.wrap(input.getHeaders()),
                              EndpointRequestHandler.LoggingContextSetter.THREAD_LOCAL_INSTANCE,
                              coreExecutor,
                              false);
                      handler.subscribe(subscriber);
                      publisher.subscribe(handler);
                      return handler;
                    })
                .get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      } catch (ExecutionException e) {
        if (e.getCause() instanceof ProtocolException pe) {
          // We can handle protocol exceptions by returning back the correct response
          LOG.warn("Error when handling the request", pe);
          return new APIGatewayProxyResponseEvent()
              .withStatusCode(pe.getCode())
              .withHeaders(
                  Map.of(
                      "content-type", "text/plain", "x-restate-server", Version.X_RESTATE_SERVER))
              .withBody(pe.getMessage());
        }
        throw new RuntimeException(e.getCause() != null ? e.getCause() : e);
      }

      // Await the result
      byte[] responseBody;
      try {
        responseBody = subscriber.getResult();
      } catch (Error | RuntimeException e) {
        throw e;
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }

      // Clear logging
      ThreadContext.clearAll();

      final APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
      response.setHeaders(
          Map.of(
              "content-type",
              requestProcessor.responseContentType(),
              "x-restate-server",
              Version.X_RESTATE_SERVER));
      response.setIsBase64Encoded(true);
      response.setStatusCode(requestProcessor.statusCode());
      response.setBody(Base64.getEncoder().encodeToString(responseBody));
      return response;
    } finally {
      // Shut down the per-request state-machine executor so its (non-daemon) thread does not leak
      // across warm Lambda invocations; the invocation has completed by the time we get here.
      coreExecutor.shutdown();
    }
  }

  // --- Utils

  private static Slice parseInputBody(APIGatewayProxyRequestEvent input) {
    if (input.getBody() == null) {
      return Slice.EMPTY;
    }
    if (!input.getIsBase64Encoded()) {
      throw new IllegalArgumentException(
          "Input is not Base64 encoded. This is most likely an SDK bug, please contact the developers.");
    }
    return Slice.wrap(Base64.getDecoder().decode(input.getBody()));
  }
}
