// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.client.jdk;

import dev.restate.client.*;
import dev.restate.client.base.BaseClient;
import dev.restate.common.*;
import dev.restate.serde.SerdeFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public class JdkClient extends BaseClient {

  private final HttpClient httpClient;

  private JdkClient(
      URI baseUri,
      SerdeFactory serdeFactory,
      ClientRequestOptions baseOptions,
      HttpClient httpClient) {
    super(baseUri, serdeFactory, baseOptions);
    this.httpClient = httpClient;
  }

  @Override
  protected <R> CompletableFuture<ClientResponse<R>> doPostRequest(
      URI target,
      Stream<Map.Entry<String, String>> headers,
      Slice payload,
      ResponseMapper<R> responseMapper) {
    var reqBuilder = HttpRequest.newBuilder().uri(target);
    headers.forEach(h -> reqBuilder.header(h.getKey(), h.getValue()));
    reqBuilder.POST(HttpRequest.BodyPublishers.ofByteArray(payload.toByteArray()));

    return this.httpClient
        .sendAsync(reqBuilder.build(), HttpResponse.BodyHandlers.ofByteArray())
        .handle(
            (res, t) -> {
              if (t != null) {
                throw new IngressException(
                    "Error when executing the request: " + t.getMessage(),
                    "POST",
                    target.toString(),
                    -1,
                    null,
                    t);
              }

              return responseMapper.mapResponse(
                  res.statusCode(), toHeaders(res.headers()), Slice.wrap(res.body()));
            });
  }

  @Override
  protected <R> CompletableFuture<ClientResponse<R>> doGetRequest(
      URI target, Stream<Map.Entry<String, String>> headers, ResponseMapper<R> responseMapper) {
    var reqBuilder = HttpRequest.newBuilder().uri(target);
    headers.forEach(h -> reqBuilder.header(h.getKey(), h.getValue()));
    reqBuilder.GET();

    return this.httpClient
        .sendAsync(reqBuilder.build(), HttpResponse.BodyHandlers.ofByteArray())
        .handle(
            (res, t) -> {
              if (t != null) {
                throw new IngressException(
                    "Error when executing the request: " + t.getMessage(),
                    "POST",
                    target.toString(),
                    -1,
                    null,
                    t);
              }

              return responseMapper.mapResponse(
                  res.statusCode(), toHeaders(res.headers()), Slice.wrap(res.body()));
            });
  }

  private ClientResponse.Headers toHeaders(HttpHeaders httpHeaders) {
    return new ClientResponse.Headers() {
      @Override
      public @Nullable String get(String key) {
        return httpHeaders.firstValue(key).orElse(null);
      }

      @Override
      public Set<String> keys() {
        return httpHeaders.map().keySet();
      }

      @Override
      public Map<String, String> toLowercaseMap() {
        return httpHeaders.map().entrySet().stream()
            .map(e -> Map.entry(e.getKey().toLowerCase(), e.getValue().get(0)))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
      }
    };
  }

  /** Create a new JDK Client */
  public static JdkClient of(
      HttpClient httpClient,
      String baseUri,
      SerdeFactory serdeFactory,
      ClientRequestOptions options) {
    return new JdkClient(URI.create(baseUri), serdeFactory, options, httpClient);
  }

  /** Create a new JDK Client */
  public static JdkClient of(
      String baseUri, SerdeFactory serdeFactory, ClientRequestOptions options) {
    return new JdkClient(URI.create(baseUri), serdeFactory, options, HttpClient.newHttpClient());
  }
}
