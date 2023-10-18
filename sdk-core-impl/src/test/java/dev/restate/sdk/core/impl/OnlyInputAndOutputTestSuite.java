package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.ProtoUtils.*;
import static dev.restate.sdk.core.impl.TestDefinitions.TestDefinition;
import static dev.restate.sdk.core.impl.TestDefinitions.testInvocation;

import dev.restate.sdk.core.impl.TestDefinitions.TestSuite;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.BindableService;
import java.util.stream.Stream;

public abstract class OnlyInputAndOutputTestSuite implements TestSuite {

  protected abstract BindableService noSyscallsGreeter();

  @Override
  public Stream<TestDefinition> definitions() {
    return Stream.of(
        testInvocation(this::noSyscallsGreeter, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1), inputMessage(GreetingRequest.newBuilder().setName("Francesco")))
            .expectingOutput(
                outputMessage(
                    GreetingResponse.newBuilder().setMessage("Hello Francesco").build())));
  }
}
