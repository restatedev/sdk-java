// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.blocking;

import static dev.restate.sdk.core.impl.ProtoUtils.greetingResponse;

import dev.restate.sdk.core.CoreSerdes;
import dev.restate.sdk.core.impl.AwakeableIdTestSuite;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;

public class AwakeableIdTest extends AwakeableIdTestSuite {

  private static class ReturnAwakeableId extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      String id = restateContext().awakeable(CoreSerdes.STRING_UTF8).id();
      responseObserver.onNext(greetingResponse(id));
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService returnAwakeableId() {
    return new ReturnAwakeableId();
  }
}
