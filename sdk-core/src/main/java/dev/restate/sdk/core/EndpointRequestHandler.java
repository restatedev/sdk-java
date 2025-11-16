// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.common.Slice;
import dev.restate.sdk.core.generated.discovery.Discovery;
import dev.restate.sdk.core.generated.manifest.EndpointManifestSchema;
import dev.restate.sdk.core.generated.manifest.Service;
import dev.restate.sdk.core.statemachine.StateMachine;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.endpoint.HeadersAccessor;
import dev.restate.sdk.endpoint.definition.HandlerDefinition;
import dev.restate.sdk.endpoint.definition.ServiceDefinition;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
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
  private static final String HEALTH_PATH = "/health";
  private static final Pattern SLASH = Pattern.compile(Pattern.quote("/"));
  private static final String ACCEPT = "accept";
  private static final TextMapGetter<HeadersAccessor> OTEL_HEADERS_GETTER =
      new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(HeadersAccessor carrier) {
          return carrier.keys();
        }

        @Nullable
        @Override
        public String get(@Nullable HeadersAccessor carrier, @NonNull String key) {
          if (carrier == null) {
            return null;
          }
          return carrier.get(key);
        }
      };

  private final Endpoint endpoint;
  private final EndpointManifest deploymentManifest;
  private final boolean deprecatedSupportsBidirectionalStreaming;

  private EndpointRequestHandler(
      EndpointManifestSchema.@Nullable ProtocolMode protocolMode, Endpoint endpoint) {
    this.endpoint = endpoint;
    this.deploymentManifest =
        new EndpointManifest(
            this.endpoint.getServiceDefinitions(), this.endpoint.isExperimentalContextEnabled());
    this.deprecatedSupportsBidirectionalStreaming =
        protocolMode != EndpointManifestSchema.ProtocolMode.REQUEST_RESPONSE;
  }

  public static EndpointRequestHandler create(Endpoint endpoint) {
    return new EndpointRequestHandler(null, endpoint);
  }

  /**
   * @deprecated The protocol mode is now established on request basis, use {@link
   *     #create(Endpoint)} instead.
   */
  @Deprecated(since = "2.3", forRemoval = true)
  public static EndpointRequestHandler forBidiStream(Endpoint endpoint) {
    return new EndpointRequestHandler(EndpointManifestSchema.ProtocolMode.BIDI_STREAM, endpoint);
  }

  /**
   * @deprecated The protocol mode is now established on request basis, use {@link
   *     #create(Endpoint)} instead.
   */
  @Deprecated(since = "2.3", forRemoval = true)
  public static EndpointRequestHandler forRequestResponse(Endpoint endpoint) {
    return new EndpointRequestHandler(
        EndpointManifestSchema.ProtocolMode.REQUEST_RESPONSE, endpoint);
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

  /**
   * @deprecated Use {@link #processorForRequest(String, HeadersAccessor, LoggingContextSetter,
   *     Executor, boolean)} instead.
   */
  @Deprecated(since = "2.3", forRemoval = true)
  public RequestProcessor processorForRequest(
      String path,
      HeadersAccessor headersAccessor,
      LoggingContextSetter loggingContextSetter,
      Executor coreExecutor)
      throws ProtocolException {
    return processorForRequest(
        path,
        headersAccessor,
        loggingContextSetter,
        coreExecutor,
        this.deprecatedSupportsBidirectionalStreaming);
  }

  /**
   * @param coreExecutor This executor MUST serialize the execution of all scheduled tasks. For
   *     example {@link Executors#newSingleThreadExecutor()} can be used.
   * @param supportsBidirectionalStreaming true if the server supports bidirectional streaming.
   * @return The request processor
   */
  public RequestProcessor processorForRequest(
      String path,
      HeadersAccessor headersAccessor,
      LoggingContextSetter loggingContextSetter,
      Executor coreExecutor,
      boolean supportsBidirectionalStreaming)
      throws ProtocolException {
    if (path.endsWith(HEALTH_PATH)) {
      return new StaticResponseRequestProcessor(200, "text/plain", Slice.wrap("OK"));
    }

    // Verify request
    if (endpoint.getRequestIdentityVerifier() != null) {
      try {
        endpoint.getRequestIdentityVerifier().verifyRequest(headersAccessor);
      } catch (Exception e) {
        throw ProtocolException.unauthorized(e);
      }
    }

    // Discovery request
    if (path.endsWith(DISCOVER_PATH)) {
      return this.handleDiscoveryRequest(supportsBidirectionalStreaming, headersAccessor);
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

    // If we got it, set it
    String invocationIdHeader = headersAccessor.get("x-restate-invocation-id");
    if (invocationIdHeader != null) {
      loggingContextSetter.set(LoggingContextSetter.INVOCATION_ID_KEY, invocationIdHeader);
    }

    // Instantiate state machine
    StateMachine stateMachine = StateMachine.init(headersAccessor, loggingContextSetter);

    // Resolve the service method definition
    ServiceDefinition svc = this.endpoint.resolveService(serviceName);
    if (svc == null) {
      throw ProtocolException.methodNotFound(serviceName, handlerName);
    }
    HandlerDefinition<?, ?> handler = svc.getHandler(handlerName);
    if (handler == null) {
      throw ProtocolException.methodNotFound(serviceName, handlerName);
    }

    // Parse OTEL context and generate span
    final io.opentelemetry.context.Context otelContext =
        this.endpoint
            .getOpenTelemetry()
            .getPropagators()
            .getTextMapPropagator()
            .extract(
                io.opentelemetry.context.Context.current(), headersAccessor, OTEL_HEADERS_GETTER);

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
        coreExecutor);
  }

  StaticResponseRequestProcessor handleDiscoveryRequest(
      boolean supportsBidirectionalStreaming, HeadersAccessor headersAccessor)
      throws ProtocolException {
    String acceptContentType = headersAccessor.get(ACCEPT);

    Discovery.ServiceDiscoveryProtocolVersion version =
        DiscoveryProtocol.selectSupportedServiceDiscoveryProtocolVersion(acceptContentType);
    if (!DiscoveryProtocol.isSupported(version)) {
      throw new ProtocolException(
          String.format(
              "Unsupported Discovery version in the Accept header '%s'", acceptContentType),
          ProtocolException.UNSUPPORTED_MEDIA_TYPE_CODE);
    }

    EndpointManifestSchema response =
        this.deploymentManifest.manifest(
            version,
            supportsBidirectionalStreaming
                ? EndpointManifestSchema.ProtocolMode.BIDI_STREAM
                : EndpointManifestSchema.ProtocolMode.REQUEST_RESPONSE);
    LOG.info(
        "Replying to discovery request with services [{}]",
        response.getServices().stream().map(Service::getName).collect(Collectors.joining(",")));

    return new StaticResponseRequestProcessor(
        200,
        DiscoveryProtocol.serviceDiscoveryProtocolVersionToHeaderValue(version),
        Slice.wrap(DiscoveryProtocol.serializeManifest(version, response)));
  }
}
