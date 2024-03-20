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
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

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
  public <Req, Res> Res call(
      Target target,
      Serde<Req> reqSerde,
      Serde<Res> resSerde,
      Req req,
      RequestOptions requestOptions) {
    HttpRequest request = prepareHttpRequest(target, false, reqSerde, req, requestOptions);
    HttpResponse<byte[]> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException("Error when executing the request", e);
    }

    if (response.statusCode() != 200) {
      // Try to parse as string
      String error = new String(response.body(), StandardCharsets.UTF_8);
      throw new RuntimeException(
          "Received non OK status code: " + response.statusCode() + ". Body: " + error);
    }

    return resSerde.deserialize(response.body());
  }

  @Override
  public <Req> String send(Target target, Serde<Req> reqSerde, Req req, RequestOptions options) {
    HttpRequest request = prepareHttpRequest(target, true, reqSerde, req, options);
    HttpResponse<InputStream> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException("Error when executing the request", e);
    }

    try (InputStream in = response.body()) {
      if (response.statusCode() >= 300) {
        // Try to parse as string
        String error = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        throw new RuntimeException(
            "Received non OK status code: " + response.statusCode() + ". Body: " + error);
      }
      return deserializeInvocationId(in);
    } catch (IOException e) {
      throw new RuntimeException(
          "Error when trying to read the response, when status code was " + response.statusCode(),
          e);
    }
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

  private static String deserializeInvocationId(InputStream body) throws IOException {
    try (JsonParser parser = JSON_FACTORY.createParser(body)) {
      if (parser.nextToken() != JsonToken.START_OBJECT) {
        throw new IllegalStateException(
            "Expecting token " + JsonToken.START_OBJECT + ", got " + parser.getCurrentToken());
      }
      String fieldName = parser.nextFieldName();
      if (fieldName == null || !fieldName.equalsIgnoreCase("invocationid")) {
        throw new IllegalStateException(
            "Expecting token \"invocationId\", got " + parser.getCurrentToken());
      }
      String invocationId = parser.nextTextValue();
      if (invocationId == null) {
        throw new IllegalStateException(
            "Expecting token " + JsonToken.VALUE_STRING + ", got " + parser.getCurrentToken());
      }
      return invocationId;
    }
  }
}
