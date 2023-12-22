// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.core.RandomTestSuite;
import dev.restate.sdk.core.testservices.GreeterRestate;
import dev.restate.sdk.core.testservices.GreetingRequest;
import dev.restate.sdk.core.testservices.GreetingResponse;
import io.grpc.BindableService;
import java.util.Random;

public class RandomTest extends RandomTestSuite {

  private static class RandomShouldBeDeterministic extends GreeterRestate.GreeterRestateImplBase {
    @Override
    public GreetingResponse greet(RestateContext context, GreetingRequest request)
        throws TerminalException {
      return GreetingResponse.newBuilder()
          .setMessage(Integer.toString(context.random().nextInt()))
          .build();
    }
  }

  @Override
  protected BindableService randomShouldBeDeterministic() {
    return new RandomShouldBeDeterministic();
  }

  private static class RandomInsideSideEffect extends GreeterRestate.GreeterRestateImplBase {
    @Override
    public GreetingResponse greet(RestateContext context, GreetingRequest request)
        throws TerminalException {
      context.sideEffect(() -> context.random().nextInt());
      throw new IllegalStateException("This should not unreachable");
    }
  }

  @Override
  protected BindableService randomInsideSideEffect() {
    return new RandomInsideSideEffect();
  }

  @Override
  protected int getExpectedInt(long seed) {
    return new Random(seed).nextInt();
  }
}
