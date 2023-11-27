// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.sdk.core.SleepTestSuite;
import dev.restate.sdk.core.testservices.GreeterGrpc;
import dev.restate.sdk.core.testservices.GreetingRequest;
import dev.restate.sdk.core.testservices.GreetingResponse;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class SleepTest extends SleepTestSuite {

  private static class SleepGreeter extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();

      ctx.sleep(Duration.ofSeconds(1));

      responseObserver.onNext(GreetingResponse.newBuilder().setMessage("Hello").build());
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService sleepGreeter() {
    return new SleepGreeter();
  }

  private static class ManySleeps extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      RestateContext ctx = restateContext();
      List<Awaitable<?>> collectedAwaitables = new ArrayList<>();

      for (int i = 0; i < 10; i++) {
        collectedAwaitables.add(ctx.timer(Duration.ofSeconds(1)));
      }

      Awaitable.all(
              collectedAwaitables.get(0),
              collectedAwaitables.get(1),
              collectedAwaitables.subList(2, collectedAwaitables.size()).toArray(Awaitable[]::new))
          .await();

      responseObserver.onNext(GreetingResponse.newBuilder().build());
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService manySleeps() {
    return new ManySleeps();
  }
}
