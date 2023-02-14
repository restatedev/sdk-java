package dev.restate.sdk.testing;

import static dev.restate.sdk.testing.ProtoUtils.*;
import static dev.restate.sdk.testing.TestDriver.TestCaseBuilder.TestInvocationBuilder.endToEndTestInvocation;

import dev.restate.sdk.testing.testservices.*;
import services.ServiceTwo;
import services.TestGreeterService;

import java.util.stream.Stream;

public class GreeterTest extends TestDriver {

    @Override
    Stream<TestDefinition> definitions() {
        return Stream.of(
                endToEndTestInvocation()
                        .withServices(new TestGreeterService())
                        .withInput(TestInput.of(TestGreeterGrpc.getGreetMethod(),
                                inputMessage(TestGreetingRequest.newBuilder().setName("Goofy"))))
                        .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
                        .expectingOutput(
                                outputMessage(TestGreetingResponse.newBuilder().setMessage("Hello Goofy")))
                        .named("End-to-end test greeter/greet"),
                endToEndTestInvocation()
                        .withServices(new TestGreeterService())
                        .withInput(TestInput.of(TestGreeterGrpc.getGreetCountMethod(),
                                inputMessage(TestGreetingRequest.newBuilder().setName("Goofy"))))
                        .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
                        .expectingOutput(
                                outputMessage(TestGreetingResponse.newBuilder().setMessage("The new count for Goofy is 1")))
                        .named("End-to-end test greeter/greetCount"),
                endToEndTestInvocation()
                        .withServices(new TestGreeterService())
                        .withInput(TestInput.of(TestGreeterGrpc.getGreetCountMethod(),
                                        inputMessage(TestGreetingRequest.newBuilder().setName("Goofy"))),
                                TestInput.of(TestGreeterGrpc.getGreetCountMethod(),
                                        inputMessage(TestGreetingRequest.newBuilder().setName("Goofy"))))
                        .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
                        .expectingOutput(
                                outputMessage(TestGreetingResponse.newBuilder().setMessage("The new count for Goofy is 1")),
                                outputMessage(TestGreetingResponse.newBuilder().setMessage("The new count for Goofy is 2")))
                        .named("End-to-end test two calls greeter/greetCount"),
                endToEndTestInvocation()
                        .withServices(new TestGreeterService())
                        .withInput(TestInput.of(TestGreeterGrpc.getGetSetClearStateMethod(),
                                inputMessage(TestGreetingRequest.newBuilder().setName("Goofy"))))
                        .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
                        .expectingOutput(
                                outputMessage(TestGreetingResponse.newBuilder().setMessage("State got cleared")))
                        .named("End-to-end test greeter/getSetClearState"),
                endToEndTestInvocation()
                        .withServices(new TestGreeterService())
                        .withInput(TestInput.of(TestGreeterGrpc.getGreetCountMethod(),
                                        inputMessage(TestGreetingRequest.newBuilder().setName("Goofy"))),
                                TestInput.of(TestGreeterGrpc.getGetSetClearStateMethod(),
                                        inputMessage(TestGreetingRequest.newBuilder().setName("Goofy"))),
                                TestInput.of(TestGreeterGrpc.getGreetCountMethod(),
                                        inputMessage(TestGreetingRequest.newBuilder().setName("Goofy"))))
                        .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
                        .expectingOutput(
                                outputMessage(TestGreetingResponse.newBuilder().setMessage("The new count for Goofy is 1")),
                                outputMessage(TestGreetingResponse.newBuilder().setMessage("State got cleared")),
                                outputMessage(TestGreetingResponse.newBuilder().setMessage("The new count for Goofy is 1")))
                        .named("End-to-end test increment state, clear state, increment state"),
                endToEndTestInvocation()
                        .withServices(new TestGreeterService(), new ServiceTwo())
                        .withInput(TestInput.of(TestGreeterGrpc.getCallOtherServiceMethod(),
                                        inputMessage(TestGreetingRequest.newBuilder().setName("Goofy"))))
                        .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
                        .expectingOutput(
                                outputMessage(TestGreetingResponse.newBuilder().setMessage("We have a new count: The new count for Goofy is 1")))
                        .named("End-to-end test inter-service call and background call."),
                endToEndTestInvocation()
                        .withServices(new TestGreeterService())
                        .withInput(TestInput.of(TestGreeterGrpc.getFailingGreetMethod(),
                                inputMessage(TestGreetingRequest.newBuilder().setName("Goofy"))))
                        .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
                        .expectingOutput(
                                outputMessage(new IllegalStateException("Whatever")))
                        .named("End-to-end test failing call."),
                endToEndTestInvocation()
                        .withServices(new TestGreeterService())
                        .withInput(TestInput.of(TestGreeterGrpc.getUseSideEffectMethod(),
                                inputMessage(TestGreetingRequest.newBuilder().setName("Goofy"))))
                        .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
                        .expectingOutput(
                                outputMessage(TestGreetingResponse.newBuilder().setMessage("Side effect executed").build()))
                        .named("End-to-end test side effect."),
                endToEndTestInvocation()
                        .withServices(new TestGreeterService(), new ServiceTwo())
                        .withInput(TestInput.of(TestGreeterGrpc.getAwakeableTestMethod(),
                                inputMessage(TestGreetingRequest.newBuilder().setName("Goofy"))))
                        .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
                        .expectingOutput(
                                outputMessage(TestGreetingResponse.newBuilder().setMessage("Wake up!").build()))
                        .named("End-to-end test awakeable."));
    }
}
