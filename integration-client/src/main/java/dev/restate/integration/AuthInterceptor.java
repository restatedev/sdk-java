// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.integration;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/** Adds an {@code Authorization: Bearer <token>} header to every call. */
final class AuthInterceptor implements ClientInterceptor {

  private static final Metadata.Key<String> AUTHORIZATION =
      Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

  private final String bearer;

  AuthInterceptor(String token) {
    this.bearer = "Bearer " + token;
  }

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
    return new ForwardingClientCall.SimpleForwardingClientCall<>(
        next.newCall(method, callOptions)) {
      @Override
      public void start(Listener<RespT> responseListener, Metadata headers) {
        headers.put(AUTHORIZATION, bearer);
        super.start(responseListener, headers);
      }
    };
  }
}
