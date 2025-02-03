// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.client.jdk;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import dev.restate.client.*;
import dev.restate.sdk.types.Output;
import dev.restate.sdk.serde.Serde;
import dev.restate.common.Slice;
import dev.restate.sdk.types.Target;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public class JdkClient implements Client {

  private static final JsonFactory JSON_FACTORY = new JsonFactory();

  private final HttpClient httpClient;
  private final URI baseUri;
  private final Map<String, String> headers;

  private JdkClient(HttpClient httpClient, String baseUri, Map<String, String> headers) {
    this.httpClient = httpClient;
    this.baseUri = URI.create(baseUri);
    this.headers = headers;
  }

  @Override
  public <Req, Res> CompletableFuture<Res> callAsync(
      Target target,
      Serde<Req> reqSerde,
      Serde<Res> resSerde,
      Req req,
      RequestOptions requestOptions) {
    HttpRequest request = prepareHttpRequest(target, false, reqSerde, req, null, requestOptions);
    return httpClient
        .sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
        .handle(
            (response, throwable) -> {
              if (throwable != null) {
                throw createIngressException("Error when executing the request", request, throwable);
              }

              if (response.statusCode() >= 300) {
                handleNonSuccessResponse(response);
              }

              try {
                return resSerde.deserialize(Slice.wrap(response.body()));
              } catch (Exception e) {
                throw createIngressException("Cannot deserialize the response", response, e);
              }
            });
  }

  @Override
  public <Req> CompletableFuture<SendResponse> sendAsync(
      Target target, Serde<Req> reqSerde, Req req, Duration delay, RequestOptions options) {
    HttpRequest request = prepareHttpRequest(target, true, reqSerde, req, delay, options);
    return httpClient
        .sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
        .handle(
            (response, throwable) -> {
              if (throwable != null) {
                throw createIngressException("Error when executing the request", request, throwable);
              }
              if (response.statusCode() >= 300) {
                handleNonSuccessResponse(response);
              }

              Map<String, String> fields;
              try {
                fields =
                    findStringFieldsInJsonObject(
                        new ByteArrayInputStream(response.body()), "invocationId", "status");
              } catch (Exception e) {
                throw createIngressException("Cannot deserialize the response", response, e);
              }

              String statusField = fields.get("status");
              SendResponse.SendStatus status;
              if ("Accepted".equals(statusField)) {
                status = SendResponse.SendStatus.ACCEPTED;
              } else if ("PreviouslyAccepted".equals(statusField)) {
                status = SendResponse.SendStatus.PREVIOUSLY_ACCEPTED;
              } else {
                throw createIngressException(
                    "Cannot deserialize the response status, got " + statusField, response);
              }

              return new SendResponse(status, fields.get("invocationId"));
            });
  }

  @Override
  public AwakeableHandle awakeableHandle(String id) {
    return new AwakeableHandle() {
      private Void handleVoidResponse(
          HttpRequest request, HttpResponse<byte[]> response, Throwable throwable) {
        if (throwable != null) {
          throw createIngressException("Error when executing the request", request, throwable);
        }

        if (response.statusCode() >= 300) {
          handleNonSuccessResponse(response);
        }

        return null;
      }

      @Override
      public <T> CompletableFuture<Void> resolveAsync(
          Serde<T> serde, @NonNull T payload, RequestOptions options) {
        // Prepare request
        var reqBuilder =
            prepareBuilder(options).uri(baseUri.resolve("/restate/awakeables/" + id + "/resolve"));

        // Add content-type
        if (serde.contentType() != null) {
          reqBuilder.header("content-type", serde.contentType());
        }

        // Build and Send request
        HttpRequest request =
            reqBuilder
                .POST(HttpRequest.BodyPublishers.ofByteArray(serde.serialize(payload).toByteArray()))
                .build();
        return httpClient
            .sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
            .handle((res, t) -> this.handleVoidResponse(request, res, t));
      }

      @Override
      public CompletableFuture<Void> rejectAsync(String reason, RequestOptions options) {
        // Prepare request
        var reqBuilder =
            HttpRequest.newBuilder()
                .uri(baseUri.resolve("/restate/awakeables/" + id + "/reject"))
                .header("content-type", "text-plain");

        // Add headers
        headers.forEach(reqBuilder::header);
        options.getAdditionalHeaders().forEach(reqBuilder::header);

        // Build and Send request
        HttpRequest request = reqBuilder.POST(HttpRequest.BodyPublishers.ofString(reason)).build();
        return httpClient
            .sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
            .handle((res, t) -> this.handleVoidResponse(request, res, t));
      }
    };
  }

  @Override
  public <Res> InvocationHandle<Res> invocationHandle(String invocationId, Serde<Res> resSerde) {
    return new InvocationHandle<>() {
      @Override
      public String invocationId() {
        return invocationId;
      }

      @Override
      public CompletableFuture<Res> attachAsync(RequestOptions options) {
        // Prepare request
        var reqBuilder =
            prepareBuilder(options)
                .uri(baseUri.resolve("/restate/invocation/" + invocationId + "/attach"));

        // Build and Send request
        HttpRequest request = reqBuilder.GET().build();
        return httpClient
            .sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
            .handle(handleAttachResponse(request, resSerde));
      }

      @Override
      public CompletableFuture<Output<Res>> getOutputAsync(RequestOptions options) {
        // Prepare request
        var reqBuilder =
            prepareBuilder(options)
                .uri(baseUri.resolve("/restate/invocation/" + invocationId + "/output"));

        // Build and Send request
        HttpRequest request = reqBuilder.GET().build();
        return httpClient
            .sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
            .handle(handleGetOutputResponse(request, resSerde));
      }
    };
  }

  @Override
  public <Res> IdempotentInvocationHandle<Res> idempotentInvocationHandle(
      Target target, String idempotencyKey, Serde<Res> resSerde) {
    return new IdempotentInvocationHandle<>() {
      @Override
      public CompletableFuture<Res> attachAsync(RequestOptions options) {
        // Prepare request
        var uri =
            baseUri.resolve(
                "/restate/invocation"
                    + targetToURI(target)
                    + "/"
                    + URLEncoder.encode(idempotencyKey, StandardCharsets.UTF_8)
                    + "/attach");
        var reqBuilder = prepareBuilder(options).uri(uri);

        // Build and Send request
        HttpRequest request = reqBuilder.GET().build();
        return httpClient
            .sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
            .handle(handleAttachResponse(request, resSerde));
      }

      @Override
      public CompletableFuture<Output<Res>> getOutputAsync(RequestOptions options) {
        // Prepare request
        var uri =
            baseUri.resolve(
                "/restate/invocation"
                    + targetToURI(target)
                    + "/"
                    + URLEncoder.encode(idempotencyKey, StandardCharsets.UTF_8)
                    + "/output");
        var reqBuilder = prepareBuilder(options).uri(uri);

        // Build and Send request
        HttpRequest request = reqBuilder.GET().build();
        return httpClient
            .sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
            .handle(handleGetOutputResponse(request, resSerde));
      }
    };
  }

  @Override
  public <Res> WorkflowHandle<Res> workflowHandle(
      String workflowName, String workflowId, Serde<Res> resSerde) {
    return new WorkflowHandle<>() {
      @Override
      public CompletableFuture<Res> attachAsync(RequestOptions options) {
        // Prepare request
        var reqBuilder =
            prepareBuilder(options)
                .uri(
                    baseUri.resolve(
                        "/restate/workflow/"
                            + workflowName
                            + "/"
                            + URLEncoder.encode(workflowId, StandardCharsets.UTF_8)
                            + "/attach"));

        // Build and Send request
        HttpRequest request = reqBuilder.GET().build();
        return httpClient
            .sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
            .handle(handleAttachResponse(request, resSerde));
      }

      @Override
      public CompletableFuture<Output<Res>> getOutputAsync(RequestOptions options) {
        // Prepare request
        var reqBuilder =
            prepareBuilder(options)
                .uri(
                    baseUri.resolve(
                        "/restate/workflow/"
                            + workflowName
                            + "/"
                            + URLEncoder.encode(workflowId, StandardCharsets.UTF_8)
                            + "/output"));

        // Build and Send request
        HttpRequest request = reqBuilder.GET().build();
        return httpClient
            .sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
            .handle(handleGetOutputResponse(request, resSerde));
      }
    };
  }

  private <Res> @NotNull
      BiFunction<HttpResponse<byte[]>, Throwable, Output<Res>> handleGetOutputResponse(
          HttpRequest request, Serde<Res> resSerde) {
    return (response, throwable) -> {
      if (throwable != null) {
        throw createIngressException("Error when executing the request", request, throwable);
      }

      if (response.statusCode() == 470) {
        return Output.notReady();
      }

      if (response.statusCode() >= 300) {
        handleNonSuccessResponse(response);
      }

      try {
        return Output.ready(resSerde.deserialize(Slice.wrap(response.body())));
      } catch (Exception e) {
        throw createIngressException("Cannot deserialize the response", response, e);
      }
    };
  }

  private <Res> @NotNull BiFunction<HttpResponse<byte[]>, Throwable, Res> handleAttachResponse(
      HttpRequest request, Serde<Res> resSerde) {
    return (response, throwable) -> {
      if (throwable != null) {
        throw createIngressException("Error when executing the request", request, throwable);
      }

      if (response.statusCode() >= 300) {
        handleNonSuccessResponse(response);
      }

      try {
        return resSerde.deserialize(Slice.wrap(response.body()));
      } catch (Exception e) {
        throw createIngressException("Cannot deserialize the response", response, e);
      }
    };
  }

  /** Contains prefix / but not postfix / */
  private String targetToURI(Target target) {
    StringBuilder builder = new StringBuilder();
    builder.append("/").append(target.getService());
    if (target.getKey() != null) {
      builder.append("/").append(URLEncoder.encode(target.getKey(), StandardCharsets.UTF_8));
    }
    builder.append("/").append(target.getHandler());
    return builder.toString();
  }

  private URI toRequestURI(Target target, boolean isSend, Duration delay) {
    StringBuilder builder = new StringBuilder(targetToURI(target));
    if (isSend) {
      builder.append("/send");
    }
    if (delay != null && !delay.isZero() && !delay.isNegative()) {
      builder.append("?delay=").append(delay);
    }

    return this.baseUri.resolve(builder.toString());
  }

  private HttpRequest.Builder prepareBuilder(RequestOptions options) {
    var reqBuilder = HttpRequest.newBuilder();

    // Add headers
    this.headers.forEach(reqBuilder::header);

    // Add idempotency key and period
    if (options instanceof CallRequestOptions) {
      if (((CallRequestOptions) options).getIdempotencyKey() != null) {
        reqBuilder.header("idempotency-key", ((CallRequestOptions) options).getIdempotencyKey());
      }
    }

    // Add additional headers
    options.getAdditionalHeaders().forEach(reqBuilder::header);

    return reqBuilder;
  }

  private <Req> HttpRequest prepareHttpRequest(
      Target target,
      boolean isSend,
      Serde<Req> reqSerde,
      Req req,
      Duration delay,
      RequestOptions options) {
    var reqBuilder = prepareBuilder(options).uri(toRequestURI(target, isSend, delay));

    // Add content-type
    if (reqSerde.contentType() != null) {
      reqBuilder.header("content-type", reqSerde.contentType());
    }

    return reqBuilder.POST(HttpRequest.BodyPublishers.ofByteArray(reqSerde.serialize(req).toByteArray())).build();
  }

  private void handleNonSuccessResponse(HttpResponse<byte[]> response) {
    if (response.headers().firstValue("content-type").orElse("").contains("application/json")) {
      String errorMessage;
      // Let's try to parse the message field
      try {
        errorMessage =
            findStringFieldInJsonObject(new ByteArrayInputStream(response.body()), "message");
      } catch (Exception e) {
        throw createIngressException("Can't decode error response from ingress", response, e);
      }
      throw createIngressException(errorMessage, response);
    }

    // Fallback error
    throw createIngressException("Received non success status code", response);
  }

  private static String findStringFieldInJsonObject(InputStream body, String fieldName)
      throws IOException {
    try (JsonParser parser = JSON_FACTORY.createParser(body)) {
      if (parser.nextToken() != JsonToken.START_OBJECT) {
        throw new IllegalStateException(
            "Expecting token " + JsonToken.START_OBJECT + ", got " + parser.getCurrentToken());
      }
      for (String actualFieldName = parser.nextFieldName();
          actualFieldName != null;
          actualFieldName = parser.nextFieldName()) {
        if (actualFieldName.equalsIgnoreCase(fieldName)) {
          return parser.nextTextValue();
        } else {
          parser.nextValue();
        }
      }
      throw new IllegalStateException(
          "Expecting field name \"" + fieldName + "\", got " + parser.getCurrentToken());
    }
  }

  private static Map<String, String> findStringFieldsInJsonObject(
      InputStream body, String... fields) throws IOException {
    Map<String, String> resultMap = new HashMap<>();
    Set<String> fieldSet = new HashSet<>(Set.of(fields));

    try (JsonParser parser = JSON_FACTORY.createParser(body)) {
      if (parser.nextToken() != JsonToken.START_OBJECT) {
        throw new IllegalStateException(
            "Expecting token " + JsonToken.START_OBJECT + ", got " + parser.getCurrentToken());
      }
      for (String actualFieldName = parser.nextFieldName();
          actualFieldName != null;
          actualFieldName = parser.nextFieldName()) {
        if (fieldSet.remove(actualFieldName)) {
          resultMap.put(actualFieldName, parser.nextTextValue());
        } else {
          parser.nextValue();
        }
      }
    }

    if (!fieldSet.isEmpty()) {
      throw new IllegalStateException(
          "Expecting fields \"" + Arrays.toString(fields) + "\", cannot find fields " + fieldSet);
    }

    return resultMap;
  }

  public static JdkClient of(
      HttpClient httpClient, String baseUri, Map<String, String> headers) {
    return new JdkClient(httpClient, baseUri, headers);
  }

  static IngressException createIngressException(String message, HttpRequest request, Throwable cause) {
    return new IngressException(message, request.method(), request.uri().toString(), -1, null, cause);
  }

  static IngressException createIngressException(String message, HttpRequest request) {
    return createIngressException(message, request, null);
  }

  static IngressException createIngressException(String message, HttpResponse<byte[]> response, Throwable cause) {
    return new IngressException(
            message,
            response.request().method(),
            response.request().uri().toString(),
            response.statusCode(),
            response.body(),
            cause);
  }

  static IngressException createIngressException(String message, HttpResponse<byte[]> response) {
    return createIngressException(message, response, null);
  }
}
