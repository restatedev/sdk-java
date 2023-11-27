// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.examples;

import com.google.protobuf.Empty;
import dev.restate.sdk.RestateContext;
import dev.restate.sdk.RestateService;
import dev.restate.sdk.common.CoreSerdes;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.examples.generated.*;
import dev.restate.sdk.http.vertx.RestateHttpEndpointBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class VanillaGrpcCounter extends CounterGrpc.CounterImplBase implements RestateService {

  private static final Logger LOG = LogManager.getLogger(VanillaGrpcCounter.class);

  private static final StateKey<Long> TOTAL = StateKey.of("total", CoreSerdes.LONG);

  @Override
  public void reset(CounterRequest request, StreamObserver<Empty> responseObserver) {
    restateContext().clear(TOTAL);

    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void add(CounterAddRequest request, StreamObserver<Empty> responseObserver) {
    RestateContext ctx = restateContext();

    long currentValue = ctx.get(TOTAL).orElse(0L);
    long newValue = currentValue + request.getValue();
    ctx.set(TOTAL, newValue);

    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void get(CounterRequest request, StreamObserver<GetResponse> responseObserver) {
    long currentValue = restateContext().get(TOTAL).orElse(0L);

    responseObserver.onNext(GetResponse.newBuilder().setValue(currentValue).build());
    responseObserver.onCompleted();
  }

  @Override
  public void getAndAdd(
      CounterAddRequest request, StreamObserver<CounterUpdateResult> responseObserver) {
    LOG.info("Invoked get and add with " + request.getValue());

    RestateContext ctx = restateContext();

    long currentValue = ctx.get(TOTAL).orElse(0L);
    long newValue = currentValue + request.getValue();
    ctx.set(TOTAL, newValue);

    responseObserver.onNext(
        CounterUpdateResult.newBuilder().setOldValue(currentValue).setNewValue(newValue).build());
    responseObserver.onCompleted();
  }

  public static void main(String[] args) {
    RestateHttpEndpointBuilder.builder().withService(new VanillaGrpcCounter()).buildAndListen();
  }
}
