// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.client;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import dev.restate.sdk.common.Serde;
import dev.restate.sdk.common.Target;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DefaultIngressClient implements IngressClient {

  private static final JsonFactory JSON_FACTORY = new JsonFactory();

  private final HttpClient httpClient;
  private final URI baseUri;
  private final Map<String, String> headers;

  public DefaultIngressClient(HttpClient httpClient, String baseUri, Map<String, String> headers) {
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
    HttpRequest request = prepareHttpRequest(target, false, reqSerde, req, requestOptions);
    return httpClient
        .sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
        .handle(
            (response, throwable) -> {
              if (throwable != null) {
                throw new IngressException("Error when executing the request", throwable);
              }

              if (response.statusCode() >= 300) {
                handleNonSuccessResponse(response);
              }

              try {
                return resSerde.deserialize(response.body());
              } catch (Exception e) {
                throw new IngressException(
                    "Cannot deserialize the response", response.statusCode(), response.body(), e);
              }
            });
  }

  @Override
  public <Req> CompletableFuture<String> sendAsync(
      Target target, Serde<Req> reqSerde, Req req, RequestOptions options) {
    HttpRequest request = prepareHttpRequest(target, true, reqSerde, req, options);
    return httpClient
        .sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
        .handle(
            (response, throwable) -> {
              if (throwable != null) {
                throw new IngressException("Error when executing the request", throwable);
              }

              if (response.statusCode() >= 300) {
                handleNonSuccessResponse(response);
              }

              try {
                return findStringFieldInJsonObject(
                    new ByteArrayInputStream(response.body()), "invocationId");
              } catch (Exception e) {
                throw new IngressException(
                    "Cannot deserialize the response", response.statusCode(), response.body(), e);
              }
            });
  }

  private URI toRequestURI(Target target, boolean isSend) {
    StringBuilder builder = new StringBuilder();
    builder.append("/").append(target.getComponent());
    if (target.getKey() != null) {
      builder.append("/").append(target.getKey());
    }
    builder.append("/").append(target.getHandler());
    if (isSend) {
      builder.append("/send");
    }

    return this.baseUri.resolve(builder.toString());
  }

  private <Req> HttpRequest prepareHttpRequest(
      Target target, boolean isSend, Serde<Req> reqSerde, Req req, RequestOptions options) {
    var reqBuilder = HttpRequest.newBuilder().uri(toRequestURI(target, isSend));

    // Add content-type
    if (reqSerde.contentType() != null) {
      reqBuilder.header("content-type", reqSerde.contentType());
    }

    // Add headers
    this.headers.forEach(reqBuilder::header);

    // Add idempotency key and period
    if (options.getIdempotencyKey() != null) {
      reqBuilder.header("idempotency-key", options.getIdempotencyKey());
    }
    if (options.getIdempotencyRetainPeriod() != null) {
      reqBuilder.header(
          "idempotency-retention-period",
          String.valueOf(options.getIdempotencyRetainPeriod().toSeconds()));
    }

    // Add additional headers
    options.getAdditionalHeaders().forEach(reqBuilder::header);

    return reqBuilder.POST(HttpRequest.BodyPublishers.ofByteArray(reqSerde.serialize(req))).build();
  }

  private void handleNonSuccessResponse(HttpResponse<byte[]> response) {
    if (response.headers().firstValue("content-type").orElse("").contains("application/json")) {
      String errorMessage;
      // Let's try to parse the message field
      try {
        errorMessage =
            findStringFieldInJsonObject(new ByteArrayInputStream(response.body()), "message");
      } catch (Exception e) {
        throw new IngressException(
            "Can't decode error response from ingress", response.statusCode(), response.body(), e);
      }
      throw new IngressException(errorMessage, response.statusCode(), response.body());
    }

    // Fallback error
    throw new IngressException(
        "Received non success status code", response.statusCode(), response.body());
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
}
