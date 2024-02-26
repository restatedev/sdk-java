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

public interface IngressClient {
  <Req, Res> Res call(Target target, Serde<Req> reqSerde, Serde<Res> resSerde, Req req);

  <Req> String send(Target target, Serde<Req> reqSerde, Req req);

  static IngressClient defaultClient(String baseUri) {
    return new DefaultIngressClient(HttpClient.newHttpClient(), baseUri);
  }
}
