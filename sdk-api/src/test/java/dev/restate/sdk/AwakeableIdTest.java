// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import static dev.restate.sdk.core.ProtoUtils.greetingResponse;

import dev.restate.sdk.common.CoreSerdes;
import dev.restate.sdk.core.AwakeableIdTestSuite;
import dev.restate.sdk.core.testservices.GreeterGrpc;
import dev.restate.sdk.core.testservices.GreetingRequest;
import dev.restate.sdk.core.testservices.GreetingResponse;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;

public class AwakeableIdTest extends AwakeableIdTestSuite {

  private static class ReturnAwakeableId extends GreeterGrpc.GreeterImplBase
      implements RestateService {

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      String id = restateContext().awakeable(CoreSerdes.JSON_STRING).id();
      responseObserver.onNext(greetingResponse(id));
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService returnAwakeableId() {
    return new ReturnAwakeableId();
  }
}
