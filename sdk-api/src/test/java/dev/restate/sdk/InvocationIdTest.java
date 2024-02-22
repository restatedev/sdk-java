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

import dev.restate.sdk.common.InvocationId;
import dev.restate.sdk.core.InvocationIdTestSuite;
import dev.restate.sdk.core.testservices.GreeterGrpc;
import dev.restate.sdk.core.testservices.GreetingRequest;
import dev.restate.sdk.core.testservices.GreetingResponse;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;

public class InvocationIdTest extends InvocationIdTestSuite {

  private static class ReturnInvocationId extends GreeterGrpc.GreeterImplBase implements Component {

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      responseObserver.onNext(greetingResponse(InvocationId.current().toString()));
      responseObserver.onCompleted();
    }
  }

  @Override
  protected BindableService returnInvocationId() {
    return new ReturnInvocationId();
  }
}
