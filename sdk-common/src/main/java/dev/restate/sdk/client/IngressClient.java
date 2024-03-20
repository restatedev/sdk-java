// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.client;

import dev.restate.sdk.common.Serde;
import dev.restate.sdk.common.Target;
import java.net.http.HttpClient;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public interface IngressClient {

  <Req, Res> CompletableFuture<Res> callAsync(
      Target target, Serde<Req> reqSerde, Serde<Res> resSerde, Req req, RequestOptions options);

  default <Req, Res> CompletableFuture<Res> callAsync(
      Target target, Serde<Req> reqSerde, Serde<Res> resSerde, Req req) {
    return callAsync(target, reqSerde, resSerde, req, RequestOptions.DEFAULT);
  }

  default <Req, Res> Res call(
      Target target, Serde<Req> reqSerde, Serde<Res> resSerde, Req req, RequestOptions options)
      throws IngressException {
    try {
      return callAsync(target, reqSerde, resSerde, req, options).join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw new RuntimeException(e.getCause());
    }
  }

  default <Req, Res> Res call(Target target, Serde<Req> reqSerde, Serde<Res> resSerde, Req req)
      throws IngressException {
    return call(target, reqSerde, resSerde, req, RequestOptions.DEFAULT);
  }

  <Req> CompletableFuture<String> sendAsync(
      Target target, Serde<Req> reqSerde, Req req, RequestOptions options);

  default <Req> CompletableFuture<String> sendAsync(Target target, Serde<Req> reqSerde, Req req) {
    return sendAsync(target, reqSerde, req, RequestOptions.DEFAULT);
  }

  default <Req> String send(Target target, Serde<Req> reqSerde, Req req, RequestOptions options)
      throws IngressException {
    try {
      return sendAsync(target, reqSerde, req, options).join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw new RuntimeException(e.getCause());
    }
  }

  default <Req> String send(Target target, Serde<Req> reqSerde, Req req) throws IngressException {
    return send(target, reqSerde, req, RequestOptions.DEFAULT);
  }

  static IngressClient defaultClient(String baseUri) {
    return defaultClient(baseUri, Collections.emptyMap());
  }

  static IngressClient defaultClient(String baseUri, Map<String, String> headers) {
    return new DefaultIngressClient(HttpClient.newHttpClient(), baseUri, headers);
  }
}
