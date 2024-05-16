// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.lambda;

import static dev.restate.sdk.core.ServiceProtocol.selectSupportedServiceDiscoveryProtocolVersion;
import static dev.restate.sdk.core.ServiceProtocol.serviceDiscoveryProtocolVersionToHeaderValue;
import static dev.restate.sdk.lambda.LambdaFlowAdapters.*;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import dev.restate.generated.service.discovery.Discovery;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.ProtocolException;
import dev.restate.sdk.core.ResolvedEndpointHandler;
import dev.restate.sdk.core.RestateEndpoint;
import dev.restate.sdk.core.ServiceProtocol;
import dev.restate.sdk.core.manifest.DeploymentManifestSchema;
import dev.restate.sdk.version.Version;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

/** Restate Lambda Endpoint. */
public final class RestateLambdaEndpoint {

  private static final Logger LOG = LogManager.getLogger(RestateLambdaEndpoint.class);

  private static final Pattern SLASH = Pattern.compile(Pattern.quote("/"));
  private static final String INVOKE_PATH_SEGMENT = "invoke";
  private static final String DISCOVER_PATH = "/discover";

  private static TextMapGetter<Map<String, String>> OTEL_HEADERS_GETTER =
      new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
          return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
          if (carrier == null) {
            return null;
          }
          return carrier.get(key);
        }
      };

  private final RestateEndpoint restateEndpoint;
  private final OpenTelemetry openTelemetry;

  RestateLambdaEndpoint(RestateEndpoint restateEndpoint, OpenTelemetry openTelemetry) {
    this.restateEndpoint = restateEndpoint;
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
      return this.handleDiscovery(input.getHeaders().get("accept"));
    }

    return this.handleInvoke(input);
  }

  // --- Invoke request

  private APIGatewayProxyResponseEvent handleInvoke(APIGatewayProxyRequestEvent input) {
    // check protocol version
    final String protocolVersionString = input.getHeaders().get("content-type");

    final Protocol.ServiceProtocolVersion serviceProtocolVersion =
        ServiceProtocol.parseServiceProtocolVersion(protocolVersionString);

    if (!ServiceProtocol.is_supported(serviceProtocolVersion)) {
      final String errorMessage =
          String.format(
              "Service endpoint does not support the service protocol version '%s'.",
              protocolVersionString);
      LOG.warn(errorMessage);

      final APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
      response.setStatusCode(415);
      response.setHeaders(
          Map.of("content-type", "text/plain", "x-restate-server", Version.X_RESTATE_SERVER));
      response.setBody(errorMessage);
      return response;
    }

    // Parse request
    String[] pathSegments = SLASH.split(input.getPath());
    if (pathSegments.length < 3
        || !INVOKE_PATH_SEGMENT.equalsIgnoreCase(pathSegments[pathSegments.length - 3])) {
      LOG.warn("Path doesn't match the pattern /invoke/SvcName/MethodName: '{}'", input.getPath());
      return new APIGatewayProxyResponseEvent().withStatusCode(404);
    }
    String serviceName = pathSegments[pathSegments.length - 2];
    String handlerName = pathSegments[pathSegments.length - 1];

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
    ResolvedEndpointHandler handler;
    try {
      handler =
          this.restateEndpoint.resolve(
              serviceName,
              handlerName,
              input.getHeaders()::get,
              otelContext,
              RestateEndpoint.LoggingContextSetter.THREAD_LOCAL_INSTANCE,
              null);
    } catch (ProtocolException e) {
      LOG.warn("Error when resolving the grpc handler", e);
      return new APIGatewayProxyResponseEvent().withStatusCode(e.getCode());
    }

    BufferedPublisher publisher = new BufferedPublisher(requestBody);
    ResultSubscriber subscriber = new ResultSubscriber();

    // Wire handler
    publisher.subscribe(handler.input());
    handler.output().subscribe(subscriber);

    // Start
    handler.start();

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
            ServiceProtocol.serviceProtocolVersionToHeaderValue(serviceProtocolVersion),
            "x-restate-server",
            Version.X_RESTATE_SERVER));
    response.setIsBase64Encoded(true);
    response.setStatusCode(200);
    response.setBody(Base64.getEncoder().encodeToString(responseBody));
    return response;
  }

  // --- Service discovery

  private APIGatewayProxyResponseEvent handleDiscovery(String acceptVersionsString) {
    final Discovery.ServiceDiscoveryProtocolVersion serviceDiscoveryProtocolVersion =
        selectSupportedServiceDiscoveryProtocolVersion(acceptVersionsString);

    if (serviceDiscoveryProtocolVersion
        == Discovery.ServiceDiscoveryProtocolVersion
            .SERVICE_DISCOVERY_PROTOCOL_VERSION_UNSPECIFIED) {
      final String errorMessage =
          String.format(
              "Unsupported service discovery protocol version: '%s'", acceptVersionsString);
      LOG.warn(errorMessage);
      APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
      response.setStatusCode(415);
      response.setHeaders(
          Map.of("content-type", "text/plain", "x-restate-server", Version.X_RESTATE_SERVER));
      response.setBody(errorMessage);
      return response;
    } else {
      // Compute response and write it back
      DeploymentManifestSchema responseManifest = this.restateEndpoint.handleDiscoveryRequest();
      byte[] serializedManifest;
      try {
        serializedManifest =
            new ServiceProtocol.DiscoveryResponseSerializer(serviceDiscoveryProtocolVersion)
                .serialize(responseManifest);
      } catch (Exception e) {
        LOG.warn("Error when writing out the manifest POJO", e);
        final APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(500);
        response.setBody(e.getMessage());
        return response;
      }

      final APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
      response.setHeaders(
          Map.of(
              "content-type",
              serviceDiscoveryProtocolVersionToHeaderValue(serviceDiscoveryProtocolVersion),
              "x-restate-server",
              Version.X_RESTATE_SERVER));
      response.setIsBase64Encoded(true);
      response.setStatusCode(200);
      response.setBody(Base64.getEncoder().encodeToString(serializedManifest));
      return response;
    }
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
