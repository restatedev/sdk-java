// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.client.base;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import dev.restate.client.*;
import dev.restate.common.*;
import dev.restate.serde.Serde;
import dev.restate.serde.SerdeFactory;
import dev.restate.serde.SerdeInfo;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Base client. This can be used to build {@link Client} implementations on top with the HTTP client
 * of your choice.
 */
public abstract class BaseClient implements Client {

  private static final JsonFactory JSON_FACTORY = new JsonFactory();

  private final URI baseUri;
  private final SerdeFactory serdeFactory;
  private final ClientRequestOptions baseOptions;

  protected BaseClient(URI baseUri, SerdeFactory serdeFactory, ClientRequestOptions baseOptions) {
    this.baseUri = baseUri;
    this.serdeFactory = serdeFactory;
    this.baseOptions = baseOptions;
  }

  @Override
  public <Req, Res> CompletableFuture<ClientResponse<Res>> callAsync(
      CallRequest<Req, Res> request) {
    Serde<Req> reqSerde = this.serdeFactory.create(request.requestSerdeInfo());
    Serde<Res> resSerde = this.serdeFactory.create(request.responseSerdeInfo());

    URI requestUri = toRequestURI(request.target(), false, null);
    Stream<Map.Entry<String, String>> headersStream =
        Stream.concat(
            baseOptions.headers().entrySet().stream(), request.headers().entrySet().stream());
    if (reqSerde.contentType() != null) {
      headersStream =
          Stream.concat(
              headersStream, Stream.of(Map.entry("content-type", reqSerde.contentType())));
    }
    if (request.idempotencyKey() != null) {
      headersStream =
          Stream.concat(
              headersStream, Stream.of(Map.entry("idempotency-key", request.idempotencyKey())));
    }
    Slice requestBody = reqSerde.serialize(request.request());

    return doPostRequest(
        requestUri, headersStream, requestBody, callResponseMapper("POST", requestUri, resSerde));
  }

  @Override
  public <Req> CompletableFuture<ClientResponse<SendResponse>> sendAsync(SendRequest<Req> request) {
    Serde<Req> reqSerde = this.serdeFactory.create(request.requestSerdeInfo());

    URI requestUri = toRequestURI(request.target(), true, request.delay());
    Stream<Map.Entry<String, String>> headersStream =
        Stream.concat(
            baseOptions.headers().entrySet().stream(), request.headers().entrySet().stream());
    if (reqSerde.contentType() != null) {
      headersStream =
          Stream.concat(
              headersStream, Stream.of(Map.entry("content-type", reqSerde.contentType())));
    }
    if (request.idempotencyKey() != null) {
      headersStream =
          Stream.concat(
              headersStream, Stream.of(Map.entry("idempotency-key", request.idempotencyKey())));
    }
    Slice requestBody = reqSerde.serialize(request.request());

    return doPostRequest(
        requestUri,
        headersStream,
        requestBody,
        (statusCode, responseHeaders, responseBody) -> {
          if (statusCode >= 300) {
            handleNonSuccessResponse(
                "POST", requestUri.toString(), statusCode, responseHeaders, responseBody);
          }

          if (responseBody == null) {
            throw new IngressException(
                "Expecting a response body, but got none",
                "POST",
                requestUri.toString(),
                statusCode,
                null,
                null);
          }

          Map<String, String> fields;
          try {
            fields =
                findStringFieldsInJsonObject(
                    new ByteArrayInputStream(responseBody.toByteArray()), "invocationId", "status");
          } catch (Exception e) {
            throw new IngressException(
                "Cannot deserialize the response",
                "POST",
                requestUri.toString(),
                statusCode,
                responseBody.toByteArray(),
                e);
          }

          String statusField = fields.get("status");
          SendResponse.SendStatus status;
          if ("Accepted".equalsIgnoreCase(statusField)) {
            status = SendResponse.SendStatus.ACCEPTED;
          } else if ("PreviouslyAccepted".equalsIgnoreCase(statusField)) {
            status = SendResponse.SendStatus.PREVIOUSLY_ACCEPTED;
          } else {
            throw new IngressException(
                "Cannot deserialize the response status, got " + statusField,
                "POST",
                requestUri.toString(),
                statusCode,
                responseBody.toByteArray(),
                null);
          }

          return new ClientResponse<>(
              statusCode, responseHeaders, new SendResponse(status, fields.get("invocationId")));
        });
  }

  @Override
  public AwakeableHandle awakeableHandle(String id) {
    return new AwakeableHandle() {
      @Override
      public <T> CompletableFuture<ClientResponse<Void>> resolveAsync(
          SerdeInfo<T> serde, @NonNull T payload, ClientRequestOptions options) {
        Serde<T> reqSerde = serdeFactory.create(serde);
        Slice requestBody = reqSerde.serialize(payload);

        URI requestUri = baseUri.resolve("/restate/awakeables/" + id + "/resolve");
        Stream<Map.Entry<String, String>> headersStream =
            Stream.concat(
                baseOptions.headers().entrySet().stream(), options.headers().entrySet().stream());
        if (reqSerde.contentType() != null) {
          headersStream =
              Stream.concat(
                  headersStream, Stream.of(Map.entry("content-type", reqSerde.contentType())));
        }

        return doPostRequest(
            requestUri,
            headersStream,
            requestBody,
            handleVoidResponse("POST", requestUri.toString()));
      }

      @Override
      public CompletableFuture<ClientResponse<Void>> rejectAsync(
          String reason, ClientRequestOptions options) {
        URI requestUri = baseUri.resolve("/restate/awakeables/" + id + "/reject");
        Stream<Map.Entry<String, String>> headersStream =
            Stream.concat(
                baseOptions.headers().entrySet().stream(),
                Stream.concat(
                    options.headers().entrySet().stream(),
                    Stream.of(Map.entry("content-type", "text/plain"))));

        return doPostRequest(
            requestUri,
            headersStream,
            Slice.wrap(reason),
            handleVoidResponse("POST", requestUri.toString()));
      }
    };
  }

  @Override
  public <Res> InvocationHandle<Res> invocationHandle(
      String invocationId, SerdeInfo<Res> resSerdeInfo) {
    return new InvocationHandle<>() {
      @Override
      public String invocationId() {
        return invocationId;
      }

      @Override
      public CompletableFuture<ClientResponse<Res>> attachAsync(ClientRequestOptions options) {
        Serde<Res> resSerde = serdeFactory.create(resSerdeInfo);

        URI requestUri = baseUri.resolve("/restate/invocation/" + invocationId + "/attach");
        Stream<Map.Entry<String, String>> headersStream =
            Stream.concat(
                baseOptions.headers().entrySet().stream(), options.headers().entrySet().stream());

        return doGetRequest(
            requestUri, headersStream, callResponseMapper("GET", requestUri, resSerde));
      }

      @Override
      public CompletableFuture<ClientResponse<Output<Res>>> getOutputAsync(
          ClientRequestOptions options) {
        Serde<Res> resSerde = serdeFactory.create(resSerdeInfo);

        URI requestUri = baseUri.resolve("/restate/invocation/" + invocationId + "/output");
        Stream<Map.Entry<String, String>> headersStream =
            Stream.concat(
                baseOptions.headers().entrySet().stream(), options.headers().entrySet().stream());

        return doGetRequest(
            requestUri, headersStream, getOutputResponseMapper("GET", requestUri, resSerde));
      }
    };
  }

  @Override
  public <Res> IdempotentInvocationHandle<Res> idempotentInvocationHandle(
      Target target, String idempotencyKey, SerdeInfo<Res> resSerdeInfo) {
    return new IdempotentInvocationHandle<>() {
      @Override
      public CompletableFuture<ClientResponse<Res>> attachAsync(ClientRequestOptions options) {
        Serde<Res> resSerde = serdeFactory.create(resSerdeInfo);

        URI requestUri =
            baseUri.resolve(
                "/restate/invocation"
                    + targetToURI(target)
                    + "/"
                    + URLEncoder.encode(idempotencyKey, StandardCharsets.UTF_8)
                    + "/attach");
        Stream<Map.Entry<String, String>> headersStream =
            Stream.concat(
                baseOptions.headers().entrySet().stream(), options.headers().entrySet().stream());

        return doGetRequest(
            requestUri, headersStream, callResponseMapper("GET", requestUri, resSerde));
      }

      @Override
      public CompletableFuture<ClientResponse<Output<Res>>> getOutputAsync(
          ClientRequestOptions options) {
        Serde<Res> resSerde = serdeFactory.create(resSerdeInfo);

        URI requestUri =
            baseUri.resolve(
                "/restate/invocation"
                    + targetToURI(target)
                    + "/"
                    + URLEncoder.encode(idempotencyKey, StandardCharsets.UTF_8)
                    + "/output");
        Stream<Map.Entry<String, String>> headersStream =
            Stream.concat(
                baseOptions.headers().entrySet().stream(), options.headers().entrySet().stream());

        return doGetRequest(
            requestUri, headersStream, getOutputResponseMapper("GET", requestUri, resSerde));
      }
    };
  }

  @Override
  public <Res> WorkflowHandle<Res> workflowHandle(
      String workflowName, String workflowId, SerdeInfo<Res> resSerdeInfo) {
    return new WorkflowHandle<>() {
      @Override
      public CompletableFuture<ClientResponse<Res>> attachAsync(ClientRequestOptions options) {
        Serde<Res> resSerde = serdeFactory.create(resSerdeInfo);

        URI requestUri =
            baseUri.resolve(
                "/restate/workflow/"
                    + workflowName
                    + "/"
                    + URLEncoder.encode(workflowId, StandardCharsets.UTF_8)
                    + "/attach");
        Stream<Map.Entry<String, String>> headersStream =
            Stream.concat(
                baseOptions.headers().entrySet().stream(), options.headers().entrySet().stream());

        return doGetRequest(
            requestUri, headersStream, callResponseMapper("GET", requestUri, resSerde));
      }

      @Override
      public CompletableFuture<ClientResponse<Output<Res>>> getOutputAsync(
          ClientRequestOptions options) {
        Serde<Res> resSerde = serdeFactory.create(resSerdeInfo);

        URI requestUri =
            baseUri.resolve(
                "/restate/workflow/"
                    + workflowName
                    + "/"
                    + URLEncoder.encode(workflowId, StandardCharsets.UTF_8)
                    + "/output");
        Stream<Map.Entry<String, String>> headersStream =
            Stream.concat(
                baseOptions.headers().entrySet().stream(), options.headers().entrySet().stream());

        return doGetRequest(
            requestUri, headersStream, getOutputResponseMapper("GET", requestUri, resSerde));
      }
    };
  }

  @FunctionalInterface
  protected interface ResponseMapper<R> {
    ClientResponse<R> mapResponse(
        int statusCode, ClientResponse.Headers responseHeaders, @Nullable Slice responseBody);
  }

  protected abstract <R> CompletableFuture<ClientResponse<R>> doPostRequest(
      URI target,
      Stream<Map.Entry<String, String>> headers,
      Slice payload,
      ResponseMapper<R> responseMapper);

  protected abstract <R> CompletableFuture<ClientResponse<R>> doGetRequest(
      URI target, Stream<Map.Entry<String, String>> headers, ResponseMapper<R> responseMapper);

  private <Res> @NotNull ResponseMapper<Res> callResponseMapper(
      String requestMethod, URI requestUri, Serde<Res> resSerde) {
    return (statusCode, responseHeaders, responseBody) -> {
      if (statusCode >= 300) {
        handleNonSuccessResponse(
            requestMethod, requestUri.toString(), statusCode, responseHeaders, responseBody);
      }

      if (responseBody == null) {
        throw new IngressException(
            "Expecting a response body, but got none",
            requestMethod,
            requestUri.toString(),
            statusCode,
            null,
            null);
      }
      try {
        return new ClientResponse<>(
            statusCode, responseHeaders, resSerde.deserialize(responseBody));
      } catch (Exception e) {
        throw new IngressException(
            "Cannot deserialize the response",
            requestMethod,
            requestUri.toString(),
            statusCode,
            responseBody.toByteArray(),
            e);
      }
    };
  }

  private <Res> @NotNull ResponseMapper<Output<Res>> getOutputResponseMapper(
      String requestMethod, URI requestUri, Serde<Res> resSerde) {
    return (statusCode, responseHeaders, responseBody) -> {
      if (statusCode == 470) {
        return new ClientResponse<>(statusCode, responseHeaders, Output.notReady());
      }

      if (statusCode >= 300) {
        handleNonSuccessResponse(
            "GET", requestUri.toString(), statusCode, responseHeaders, responseBody);
      }

      if (responseBody == null) {
        throw new IngressException(
            "Expecting a response body, but got none",
            requestMethod,
            requestUri.toString(),
            statusCode,
            null,
            null);
      }
      try {
        return new ClientResponse<>(
            statusCode, responseHeaders, Output.ready(resSerde.deserialize(responseBody)));
      } catch (Exception e) {
        throw new IngressException(
            "Cannot deserialize the response",
            requestMethod,
            requestUri.toString(),
            statusCode,
            responseBody.toByteArray(),
            e);
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

  private ResponseMapper<Void> handleVoidResponse(String requestMethod, String requestURI) {
    return (statusCode, responseHeaders, responseBody) -> {
      if (statusCode >= 300) {
        handleNonSuccessResponse(
            requestMethod, requestURI, statusCode, responseHeaders, responseBody);
      }

      return new ClientResponse<>(statusCode, responseHeaders, null);
    };
  }

  private void handleNonSuccessResponse(
      String requestMethod,
      String requestURI,
      int statusCode,
      ClientResponse.Headers headers,
      @Nullable Slice responseBody) {
    String ct = headers.get("content-type");
    if (ct != null && ct.contains("application/json") && responseBody != null) {
      String errorMessage;
      // Let's try to parse the message field
      try {
        errorMessage =
            findStringFieldInJsonObject(
                new ByteArrayInputStream(responseBody.toByteArray()), "message");
      } catch (Exception e) {
        throw new IngressException(
            "Can't decode error response from ingress",
            requestMethod,
            requestURI,
            statusCode,
            responseBody.toByteArray(),
            e);
      }
      throw new IngressException(
          errorMessage, requestMethod, requestURI, statusCode, responseBody.toByteArray(), null);
    }

    // Fallback error
    throw new IngressException(
        "Received non success status code",
        requestMethod,
        requestURI,
        statusCode,
        (responseBody != null) ? responseBody.toByteArray() : null,
        null);
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
}
