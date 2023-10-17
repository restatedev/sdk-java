package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.CoreTestRunner.TestCaseBuilder.testInvocation;
import static dev.restate.sdk.core.impl.ProtoUtils.*;

import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.BindableService;
import java.util.stream.Stream;

public abstract class OnlyInputAndOutputTestSuite extends CoreTestRunner {

  protected abstract BindableService noSyscallsGreeter();

  @Override
  protected Stream<TestDefinition> definitions() {
    return Stream.of(
        testInvocation(this::noSyscallsGreeter, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1), inputMessage(GreetingRequest.newBuilder().setName("Francesco")))
            .usingAllThreadingModels()
            .expectingOutput(
                outputMessage(
                    GreetingResponse.newBuilder().setMessage("Hello Francesco").build())));
  }
}
