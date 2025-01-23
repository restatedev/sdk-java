// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.sdk.core.generated.discovery.Discovery;
import dev.restate.sdk.core.generated.manifest.EndpointManifestSchema;
import dev.restate.sdk.core.generated.manifest.Service;
import dev.restate.sdk.core.statemachine.StateMachine;
import dev.restate.sdk.definition.HandlerDefinition;
import dev.restate.sdk.types.Slice;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class EndpointRequestHandler {

  private static final Logger LOG = LogManager.getLogger(EndpointRequestHandler.class);
  private static final String DISCOVER_PATH = "/discover";
  private static final Pattern SLASH = Pattern.compile(Pattern.quote("/"));
  private static final String ACCEPT = "accept";
  private static final TextMapGetter<Headers> OTEL_HEADERS_GETTER =
      new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Headers carrier) {
          return carrier.keys();
        }

        @Nullable
        @Override
        public String get(@Nullable Headers carrier, @NonNull String key) {
          if (carrier == null) {
            return null;
          }
          return carrier.get(key);
        }
      };

  private final Endpoint endpoint;
  private final EndpointManifest deploymentManifest;

  private EndpointRequestHandler(
      EndpointManifestSchema.ProtocolMode protocolMode, Endpoint endpoint) {
    this.endpoint = endpoint;
    this.deploymentManifest =
        new EndpointManifest(
            protocolMode,
            this.endpoint.getServices().values().stream().map(Endpoint.ServiceAndOptions::service),
            this.endpoint.isExperimentalContextEnabled());
  }

  public static EndpointRequestHandler forBidiStream(Endpoint endpoint) {
    return new EndpointRequestHandler(EndpointManifestSchema.ProtocolMode.BIDI_STREAM, endpoint);
  }

  public static EndpointRequestHandler forRequestResponse(Endpoint endpoint) {
    return new EndpointRequestHandler(
        EndpointManifestSchema.ProtocolMode.REQUEST_RESPONSE, endpoint);
  }

  /** Abstraction for headers map. */
  public interface Headers {
    Iterable<String> keys();

    @Nullable String get(String key);
  }

  /**
   * Interface to abstract setting the logging context variables.
   *
   * <p>In classic multithreaded environments, you can just use {@link
   * LoggingContextSetter#THREAD_LOCAL_INSTANCE}, though the caller of {@link
   * EndpointRequestHandler} must take care of the cleanup of the thread local map.
   */
  @FunctionalInterface
  public interface LoggingContextSetter {

    String INVOCATION_ID_KEY = "restateInvocationId";
    String INVOCATION_TARGET_KEY = "restateInvocationTarget";
    String INVOCATION_STATUS_KEY = "restateInvocationStatus";

    LoggingContextSetter THREAD_LOCAL_INSTANCE = ThreadContext::put;

    void set(String key, String value);
  }

  public RequestProcessor processorForRequest(
      String path,
      Headers headers,
      LoggingContextSetter loggingContextSetter,
      @Nullable Executor coreExecutor)
      throws ProtocolException {
    // Discovery request
    if (DISCOVER_PATH.equalsIgnoreCase(path)) {
      return this.handleDiscoveryRequest(headers);
    }

    // Parse request
    String[] pathSegments = SLASH.split(path);
    if (pathSegments.length < 3) {
      LOG.warn(
          "Path doesn't match the pattern /invoke/ServiceName/HandlerName nor /discover nor /health: '{}'",
          path);
      throw new ProtocolException(
          "Path doesn't match the pattern /invoke/ServiceName/HandlerName nor /discover nor /health",
          404);
    }
    String serviceName = pathSegments[pathSegments.length - 2];
    String handlerName = pathSegments[pathSegments.length - 1];

    String fullyQualifiedServiceMethod = serviceName + "/" + handlerName;

    // Instantiate state machine
    StateMachine stateMachine = StateMachine.init(headers, loggingContextSetter);

    // Resolve the service method definition
    @SuppressWarnings("unchecked")
    Endpoint.ServiceAndOptions<Object> svc =
        (Endpoint.ServiceAndOptions<Object>) this.endpoint.getServices().get(serviceName);
    if (svc == null) {
      throw ProtocolException.methodNotFound(serviceName, handlerName);
    }
    HandlerDefinition<?, ?, Object> handler = svc.service().getHandler(handlerName);
    if (handler == null) {
      throw ProtocolException.methodNotFound(serviceName, handlerName);
    }

    // Verify request
    if (endpoint.getRequestIdentityVerifier() != null) {
      try {
        endpoint.getRequestIdentityVerifier().verifyRequest(headers);
      } catch (Exception e) {
        throw ProtocolException.unauthorized(e);
      }
    }

    // Parse OTEL context and generate span
    final io.opentelemetry.context.Context otelContext =
        this.endpoint
            .getOpenTelemetry()
            .getPropagators()
            .getTextMapPropagator()
            .extract(io.opentelemetry.context.Context.current(), headers, OTEL_HEADERS_GETTER);

    // Generate the span
    //    Span span =
    //        tracer
    //            .spanBuilder("Invoke handler")
    //            .setSpanKind(SpanKind.SERVER)
    //            .setParent(otelContext)
    //            .startSpan();

    // Setup logging context
    loggingContextSetter.set(
        LoggingContextSetter.INVOCATION_TARGET_KEY, fullyQualifiedServiceMethod);

    return new RequestProcessorImpl(
        fullyQualifiedServiceMethod,
        stateMachine,
        handler,
        otelContext,
        loggingContextSetter,
        svc.options(),
        coreExecutor);
  }

  StaticResponseRequestProcessor handleDiscoveryRequest(Headers headers) throws ProtocolException {
    String acceptContentType = headers.get(ACCEPT);

    Discovery.ServiceDiscoveryProtocolVersion version =
        DiscoveryProtocol.selectSupportedServiceDiscoveryProtocolVersion(acceptContentType);
    if (!DiscoveryProtocol.isSupported(version)) {
      throw new ProtocolException(
          String.format(
              "Unsupported Discovery version in the Accept header '%s'", acceptContentType),
          ProtocolException.UNSUPPORTED_MEDIA_TYPE_CODE);
    }

    EndpointManifestSchema response = this.deploymentManifest.manifest();
    LOG.info(
        "Replying to discovery request with services [{}]",
        response.getServices().stream().map(Service::getName).collect(Collectors.joining(",")));

    return new StaticResponseRequestProcessor(
        200,
        DiscoveryProtocol.serviceDiscoveryProtocolVersionToHeaderValue(version),
        Slice.wrap(DiscoveryProtocol.serializeManifest(version, response)));
  }
}
