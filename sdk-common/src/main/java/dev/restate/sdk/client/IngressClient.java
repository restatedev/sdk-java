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

public interface IngressClient {
  <Req, Res> Res call(
      Target target, Serde<Req> reqSerde, Serde<Res> resSerde, Req req, RequestOptions options);

  default <Req, Res> Res call(Target target, Serde<Req> reqSerde, Serde<Res> resSerde, Req req) {
    return call(target, reqSerde, resSerde, req, RequestOptions.DEFAULT);
  }

  <Req> String send(Target target, Serde<Req> reqSerde, Req req, RequestOptions options);

  default <Req> String send(Target target, Serde<Req> reqSerde, Req req) {
    return send(target, reqSerde, req, RequestOptions.DEFAULT);
  }

  static IngressClient defaultClient(String baseUri) {
    return defaultClient(baseUri, Collections.emptyMap());
  }

  static IngressClient defaultClient(String baseUri, Map<String, String> headers) {
    return new DefaultIngressClient(HttpClient.newHttpClient(), baseUri, headers);
  }
}
