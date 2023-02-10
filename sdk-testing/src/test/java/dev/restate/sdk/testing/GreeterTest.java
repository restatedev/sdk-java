package dev.restate.sdk.testing;

import dev.restate.sdk.testing.testservices.TestGreeterGrpc;
import dev.restate.sdk.testing.testservices.TestGreetingRequest;
import dev.restate.sdk.testing.testservices.TestGreetingResponse;

import static dev.restate.sdk.testing.ProtoUtils.*;
import static dev.restate.sdk.testing.TestDriver.TestCaseBuilder.testInvocation;

import java.util.stream.Stream;

public class GreeterTest extends TestDriver {

  @Override
  Stream<TestDefinition> definitions() {
    return Stream.of(
        testInvocation(new TestGreeterService(), TestGreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(TestGreetingRequest.newBuilder().setName("Till")))
            .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
            .expectingOutput(
                outputMessage(TestGreetingResponse.newBuilder().setMessage("Hello Till").build()))
            .named("End-to-end test")
    );
  }
}
