package dev.restate.sdk.testing;

import static dev.restate.sdk.testing.ProtoUtils.*;
import static dev.restate.sdk.testing.TestDriver.TestCaseBuilder.testInvocation;

import dev.restate.sdk.testing.testservices.TestGreeterGrpc;
import dev.restate.sdk.testing.testservices.TestGreetingRequest;
import dev.restate.sdk.testing.testservices.TestGreetingResponse;
import services.TestGreeterService;

import java.util.stream.Stream;

public class GreeterTest extends TestDriver {

  @Override
  Stream<TestDefinition> definitions() {
    return Stream.of(
        testInvocation(new TestGreeterService(), TestGreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(
                    1), // 1 means unary mode: runtime directly sends the start message and the
                // inputs
                inputMessage(TestGreetingRequest.newBuilder().setName("Till")))
            .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
            .expectingOutput(
                outputMessage(TestGreetingResponse.newBuilder().setMessage("Hello Till").build()))
            .named("End-to-end test greeter/greet"),
        testInvocation(new TestGreeterService(), TestGreeterGrpc.getGreetCountMethod())
            .withInput(
                startMessage(
                    1), // 1 means unary mode: runtime directly sends the start message and the
                // inputs
                inputMessage(TestGreetingRequest.newBuilder().setName("name1")))
            .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
            .expectingOutput(
                outputMessage(
                    TestGreetingResponse.newBuilder()
                        .setMessage("The new count for name1 is 1")
                        .build()))
            .named("End-to-end test greeter/greetCount"),
            testInvocation(new TestGreeterService(), TestGreeterGrpc.getGetSetClearStateMethod())
                    .withInput(
                            startMessage(
                                    1), // 1 means unary mode: runtime directly sends the start message and the
                            // inputs
                            inputMessage(TestGreetingRequest.newBuilder().setName("name1")))
                    .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
                    .expectingOutput(
                            outputMessage(
                                    TestGreetingResponse.newBuilder()
                                            .setMessage("State got cleared")
                                            .build()))
                    .named("End-to-end test greeter/greetCount")/*,
            testInvocation(new TestGreeterService(), TestGreeterGrpc.getCallOtherServiceMethod())
                    .withInput(
                            startMessage(
                                    1), // 1 means unary mode: runtime directly sends the start message and the
                            // inputs
                            inputMessage(TestGreetingRequest.newBuilder().setName("name1")))
                    .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
                    .expectingOutput(
                            outputMessage(
                                    TestGreetingResponse.newBuilder()
                                            .setMessage("Hello Goofy")
                                            .build()))
                    .named("End-to-end test greeter/greetCount")*/);
  }
}
