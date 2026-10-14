// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.restate.common.Slice;
import dev.restate.sdk.core.legacy.ProtoUtils;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.endpoint.HeadersAccessor;
import dev.restate.sdk.endpoint.definition.HandlerDefinition;
import dev.restate.sdk.endpoint.definition.HandlerType;
import dev.restate.sdk.endpoint.definition.ServiceDefinition;
import dev.restate.sdk.endpoint.definition.ServiceType;
import dev.restate.serde.PayloadCodec;
import dev.restate.serde.Serde;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

// TODO remove once we remove LegacyStateMachine
class PayloadCodecLegacyStateMachineTest {

  private static final Executor DIRECT_EXECUTOR = Runnable::run;

  private static ServiceDefinition service() {
    return ServiceDefinition.of(
        "MyService",
        ServiceType.SERVICE,
        List.of(
            HandlerDefinition.of(
                "run",
                HandlerType.SHARED,
                Serde.VOID,
                Serde.VOID,
                (ctx, reqSerde, resSerde, onClosed) -> CompletableFuture.completedFuture(null))));
  }

  private static void processorForRequest(Endpoint endpoint) {
    new EndpointRequestHandler(null, endpoint, StateMachineFactory.Loader.LEGACY_FACTORY)
        .processorForRequest(
            "/invoke/MyService/run",
            HeadersAccessor.wrap(
                Map.of("content-type", ProtoUtils.serviceProtocolContentTypeHeader())),
            EndpointRequestHandler.LoggingContextSetter.THREAD_LOCAL_INSTANCE,
            DIRECT_EXECUTOR,
            true);
  }

  @Test
  void endpointCodecFails() {
    assertThatThrownBy(
            () ->
                processorForRequest(
                    Endpoint.builder()
                        .bind(service())
                        .withPayloadCodec(
                            new PayloadCodec() {
                              @Override
                              public CompletableFuture<Slice> encode(Slice payload) {
                                return CompletableFuture.completedFuture(payload);
                              }

                              @Override
                              public CompletableFuture<Slice> decode(Slice payload) {
                                return CompletableFuture.completedFuture(payload);
                              }
                            })
                        .build()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PayloadCodec");
  }

  @Test
  void noCodecWorks() {
    assertThatNoException()
        .isThrownBy(() -> processorForRequest(Endpoint.builder().bind(service()).build()));
  }
}
