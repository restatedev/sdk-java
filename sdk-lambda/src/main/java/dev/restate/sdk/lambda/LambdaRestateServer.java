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
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class LambdaRestateServer {

  private static final Logger LOG = LogManager.getLogger(LambdaRestateServer.class);

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

  LambdaRestateServer(RestateGrpcServer restateGrpcServer, OpenTelemetry openTelemetry) {
    this.restateGrpcServer = restateGrpcServer;
    this.openTelemetry = openTelemetry;
  }

  /** Create a new builder. */
  public static LambdaRestateServerBuilder builder() {
    return new LambdaRestateServerBuilder();
  }

  APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
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
      handler = this.restateGrpcServer.resolve(service, method, otelContext, null, null);
    } catch (ProtocolException e) {
      LOG.warn("Error when resolving the grpc handler", e);
      return new APIGatewayProxyResponseEvent()
          .withStatusCode(e.getGrpcCode() == Status.Code.NOT_FOUND ? 404 : 500);
    }

    BufferedPublisher publisher = new BufferedPublisher(requestBody);
    FutureSubscriber subscriber = new FutureSubscriber();

    // Wire handler
    publisher.subscribe(handler.input());
    handler.output().subscribe(subscriber);

    // Start and wait for response
    handler.start();

    byte[] responseBody;
    try {
      responseBody = subscriber.getFuture().get();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      // Propagate cause as it's a Restate ProtocolException
      throw new RuntimeException(e.getCause());
    }

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

  // --- LambdaRestateService SPI discovery and Singleton

  static LambdaRestateServer getInstance() {
    return LambdaRestateServerHolder.INSTANCE;
  }

  private static class LambdaRestateServerHolder {
    private static final LambdaRestateServer INSTANCE = loadFromSPI();

    private LambdaRestateServerHolder() {}

    private static LambdaRestateServer loadFromSPI() {
      List<LambdaRestateServer> restateServerList =
          ServiceLoader.load(LambdaRestateServerFactory.class).stream()
              .map(factoryProvider -> factoryProvider.get().create())
              .collect(Collectors.toList());

      if (restateServerList.size() != 1) {
        throw new IllegalStateException(
            "There MUST be exactly one LambdaRestateServer available in classpath. Found: "
                + restateServerList);
      }

      return restateServerList.get(0);
    }
  }
}
