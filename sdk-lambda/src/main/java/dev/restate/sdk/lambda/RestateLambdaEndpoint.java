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
import com.google.protobuf.InvalidProtocolBufferException;
import dev.restate.generated.service.discovery.Discovery;
import dev.restate.sdk.core.impl.InvocationHandler;
import dev.restate.sdk.core.impl.ProtocolException;
import dev.restate.sdk.core.impl.RestateGrpcServer;
import io.grpc.Status;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

/** Restate Lambda Endpoint. */
public final class RestateLambdaEndpoint {

  private static final Logger LOG = LogManager.getLogger(RestateLambdaEndpoint.class);

  private static final Pattern SLASH = Pattern.compile(Pattern.quote("/"));
  private static final String INVOKE_PATH_SEGMENT = "invoke";
  private static final String DISCOVER_PATH = "/discover";
  private static final Map<String, String> INVOKE_RESPONSE_HEADERS =
      Map.of("content-type", "application/restate");
  private static final Map<String, String> DISCOVER_RESPONSE_HEADERS =
      Map.of("content-type", "application/proto");

  private static TextMapGetter<Map<String, String>> OTEL_HEADERS_GETTER =
      new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
          return carrier.keySet();
        }

        @Nullable
        @Override
        public String get(@Nullable Map<String, String> carrier, String key) {
          if (carrier == null) {
            return null;
          }
          return carrier.get(key);
        }
      };

  private final RestateGrpcServer restateGrpcServer;
  private final OpenTelemetry openTelemetry;

  RestateLambdaEndpoint(RestateGrpcServer restateGrpcServer, OpenTelemetry openTelemetry) {
    this.restateGrpcServer = restateGrpcServer;
    this.openTelemetry = openTelemetry;
  }

  /** Create a new builder. */
  public static RestateLambdaEndpointBuilder builder() {
    return new RestateLambdaEndpointBuilder();
  }

  /** Handle a Lambda request as Restate Lambda endpoint. */
  public APIGatewayProxyResponseEvent handleRequest(
      APIGatewayProxyRequestEvent input, Context context) {
    // Remove trailing path separator
    String path =
        input.getPath().endsWith("/")
            ? input.getPath().substring(0, input.getPath().length() - 1)
            : input.getPath();

    if (path.endsWith(DISCOVER_PATH)) {
      return this.handleDiscovery(input);
    }

    return this.handleInvoke(input);
  }

  // --- Invoke request

  private APIGatewayProxyResponseEvent handleInvoke(APIGatewayProxyRequestEvent input) {
    // Parse request
    String[] pathSegments = SLASH.split(input.getPath());
    if (pathSegments.length < 3
        || !INVOKE_PATH_SEGMENT.equalsIgnoreCase(pathSegments[pathSegments.length - 3])) {
      LOG.warn("Path doesn't match the pattern /invoke/SvcName/MethodName: '{}'", input.getPath());
      return new APIGatewayProxyResponseEvent().withStatusCode(404);
    }
    String service = pathSegments[pathSegments.length - 2];
    String method = pathSegments[pathSegments.length - 1];

    // Parse OTEL context and generate span
    final io.opentelemetry.context.Context otelContext =
        openTelemetry
            .getPropagators()
            .getTextMapPropagator()
            .extract(
                io.opentelemetry.context.Context.current(),
                input.getHeaders(),
                OTEL_HEADERS_GETTER);

    // Parse request body
    final ByteBuffer requestBody = parseInputBody(input);

    // Resolve handler
    InvocationHandler handler;
    try {
      handler =
          this.restateGrpcServer.resolve(
              service,
              method,
              otelContext,
              RestateGrpcServer.LoggingContextSetter.THREAD_LOCAL_INSTANCE,
              null,
              null);
    } catch (ProtocolException e) {
      LOG.warn("Error when resolving the grpc handler", e);
      return new APIGatewayProxyResponseEvent()
          .withStatusCode(e.getFailureCode() == Status.Code.NOT_FOUND.value() ? 404 : 500);
    }

    BufferedPublisher publisher = new BufferedPublisher(requestBody);
    ResultSubscriber subscriber = new ResultSubscriber();

    // Wire handler
    publisher.subscribe(handler.input());
    handler.output().subscribe(subscriber);

    // Start
    handler.start();

    // Because everything runs in the same thread, handler.start() should execute the whole
    // computation. Hence, we should have a result available at this point.
    byte[] responseBody = subscriber.getResult();

    // Clear logging
    ThreadContext.clearAll();

    final APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
    response.setHeaders(INVOKE_RESPONSE_HEADERS);
    response.setIsBase64Encoded(true);
    response.setStatusCode(200);
    response.setBody(Base64.getEncoder().encodeToString(responseBody));
    return response;
  }

  // --- Service discovery

  private APIGatewayProxyResponseEvent handleDiscovery(APIGatewayProxyRequestEvent input) {
    final ByteBuffer requestBody = parseInputBody(input);

    Discovery.ServiceDiscoveryRequest discoveryRequest;
    try {
      discoveryRequest = Discovery.ServiceDiscoveryRequest.parseFrom(requestBody);
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException("Cannot decode discovery request", e);
    }

    final Discovery.ServiceDiscoveryResponse discoveryResponse =
        this.restateGrpcServer.handleDiscoveryRequest(discoveryRequest);

    final APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
    response.setHeaders(DISCOVER_RESPONSE_HEADERS);
    response.setIsBase64Encoded(true);
    response.setStatusCode(200);
    response.setBody(Base64.getEncoder().encodeToString(discoveryResponse.toByteArray()));
    return response;
  }

  // --- Utils

  private static ByteBuffer parseInputBody(APIGatewayProxyRequestEvent input) {
    if (input.getBody() == null) {
      return ByteBuffer.wrap(new byte[] {});
    }
    if (!input.getIsBase64Encoded()) {
      throw new IllegalArgumentException(
          "Input is not Base64 encoded. This is most likely an SDK bug, please contact the developers.");
    }
    return ByteBuffer.wrap(Base64.getDecoder().decode(input.getBody()));
  }
}
