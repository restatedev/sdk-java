package dev.restate.sdk.testing;

import static dev.restate.sdk.testing.ProtoUtils.*;
import static dev.restate.sdk.testing.TestDriver.TestCaseBuilder.TestInvocationBuilder.endToEndTestInvocation;

import static dev.restate.sdk.testing.TestDriver.TestInput.Builder.testInput;

import dev.restate.sdk.testing.testservices.GreeterOneGrpc;
import dev.restate.sdk.testing.testservices.GreeterOneRequest;
import dev.restate.sdk.testing.testservices.GreeterOneResponse;
import services.GreeterOne;
import services.GreeterTwo;

import java.util.stream.Stream;

public class GreeterTest extends TestDriver {

    @Override
    Stream<TestDefinition> definitions() {
        return Stream.of(
                endToEndTestInvocation()
                        .withServices(new GreeterOne())
                        .withInput(
                                testInput().withMethod(GreeterOneGrpc.getGreetMethod())
                                        .withMessage(GreeterOneRequest.newBuilder().setName("Goofy")))
                        .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
                        .expectingOutput(
                                outputMessage(GreeterOneResponse.newBuilder().setMessage("Hello Goofy")))
                        .named("GreeterOne/greet: send response"),
                endToEndTestInvocation()
                        .withServices(new GreeterOne())
                        .withInput(
                                testInput().withMethod(GreeterOneGrpc.getStoreAndGreetMethod())
                                        .withMessage(GreeterOneRequest.newBuilder().setName("Goofy")))
                        .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
                        .expectingOutput(
                                outputMessage(GreeterOneResponse.newBuilder().setMessage("Hello Goofy")))
                        .named("GreeterOne/storeAndGreet: get and set state"),
                endToEndTestInvocation()
                        .withServices(new GreeterOne())
                        .withInput(
                                testInput().withMethod(GreeterOneGrpc.getCountGreetingsMethod())
                                        .withMessage(GreeterOneRequest.newBuilder().setName("Goofy")),
                                testInput().withMethod(GreeterOneGrpc.getCountGreetingsMethod())
                                        .withMessage(GreeterOneRequest.newBuilder().setName("Pluto")))
                        .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
                        .expectingOutput(
                                outputMessage(GreeterOneResponse.newBuilder().setMessage("Hello Goofy #1")),
                                outputMessage(GreeterOneResponse.newBuilder().setMessage("Hello Pluto #1")))
                        .named("GreeterOne/countGreetings: get and set state for multiple keys"),
                endToEndTestInvocation()
                        .withServices(new GreeterOne())
                        .withInput(
                                testInput().withMethod(GreeterOneGrpc.getCountGreetingsMethod())
                                        .withMessage(GreeterOneRequest.newBuilder().setName("Goofy")),
                                testInput().withMethod(GreeterOneGrpc.getCountGreetingsMethod())
                                        .withMessage(GreeterOneRequest.newBuilder().setName("Pluto")),
                                testInput().withMethod(GreeterOneGrpc.getCountGreetingsMethod())
                                        .withMessage(GreeterOneRequest.newBuilder().setName("Goofy")),
                                testInput().withMethod(GreeterOneGrpc.getCountGreetingsMethod())
                                        .withMessage(GreeterOneRequest.newBuilder().setName("Goofy")),
                                testInput().withMethod(GreeterOneGrpc.getCountGreetingsMethod())
                                        .withMessage(GreeterOneRequest.newBuilder().setName("Pluto")),
                                testInput().withMethod(GreeterOneGrpc.getCountGreetingsMethod())
                                        .withMessage(GreeterOneRequest.newBuilder().setName("Pluto")))
                        .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
                        .expectingOutput(
                                outputMessage(GreeterOneResponse.newBuilder().setMessage("Hello Goofy #1")),
                                outputMessage(GreeterOneResponse.newBuilder().setMessage("Hello Pluto #1")),
                                outputMessage(GreeterOneResponse.newBuilder().setMessage("Hello Goofy #2")),
                                outputMessage(GreeterOneResponse.newBuilder().setMessage("Hello Goofy #3")),
                                outputMessage(GreeterOneResponse.newBuilder().setMessage("Hello Pluto #2")),
                                outputMessage(GreeterOneResponse.newBuilder().setMessage("Hello Pluto #3")))
                        .named("GreeterOne/countGreetings: get and set state for multiple keys multiple times"),
                endToEndTestInvocation()
                        .withServices(new GreeterOne())
                        .withInput(
                                testInput().withMethod(GreeterOneGrpc.getCountGreetingsMethod())
                                        .withMessage(GreeterOneRequest.newBuilder().setName("Goofy")),
                                testInput().withMethod(GreeterOneGrpc.getCountGreetingsMethod())
                                        .withMessage(GreeterOneRequest.newBuilder().setName("Goofy")),
                                testInput().withMethod(GreeterOneGrpc.getResetGreetingCounterMethod())
                                        .withMessage(GreeterOneRequest.newBuilder().setName("Goofy")))
                        .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
                        .expectingOutput(
                                outputMessage(GreeterOneResponse.newBuilder().setMessage("Hello Goofy #1")),
                                outputMessage(GreeterOneResponse.newBuilder().setMessage("Hello Goofy #2")),
                                outputMessage(GreeterOneResponse.newBuilder().setMessage("State got cleared")))
                        .named("GreeterOne/resetGreetingCounter: set and clear state"),
                endToEndTestInvocation()
                        .withServices(new GreeterOne())
                        .withInput(
                                testInput().withMethod(GreeterOneGrpc.getCountGreetingsMethod())
                                        .withMessage(GreeterOneRequest.newBuilder().setName("Goofy")),
                                testInput().withMethod(GreeterOneGrpc.getResetGreetingCounterMethod())
                                        .withMessage(GreeterOneRequest.newBuilder().setName("Goofy")),
                                testInput().withMethod(GreeterOneGrpc.getCountGreetingsMethod())
                                        .withMessage(GreeterOneRequest.newBuilder().setName("Goofy")))
                        .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
                        .expectingOutput(
                                outputMessage(GreeterOneResponse.newBuilder().setMessage("Hello Goofy #1")),
                                outputMessage(GreeterOneResponse.newBuilder().setMessage("State got cleared")),
                                outputMessage(GreeterOneResponse.newBuilder().setMessage("Hello Goofy #1")))
                        .named("GreeterOne/resetGreetingCounter: set state, clear state, set state"),
                endToEndTestInvocation()
                        .withServices(new GreeterOne(), new GreeterTwo())
                        .withInput(
                                testInput().withMethod(GreeterOneGrpc.getForwardGreetingMethod())
                                        .withMessage(GreeterOneRequest.newBuilder().setName("Goofy")))
                        .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
                        .expectingOutput(
                                outputMessage(GreeterOneResponse.newBuilder()
                                        .setMessage("Greeting has been forwarded to GreeterTwo. Response was: Hello Goofy #1")))
                        .named("GreeterOne/forwardGreeting: synchronous inter-service call"),
                endToEndTestInvocation()
                        .withServices(new GreeterOne(), new GreeterTwo())
                        .withInput(
                                testInput().withMethod(GreeterOneGrpc.getForwardBackgroundGreetingMethod())
                                        .withMessage(GreeterOneRequest.newBuilder().setName("Goofy")),
                                testInput().withMethod(GreeterOneGrpc.getForwardGreetingMethod())
                                        .withMessage(GreeterOneRequest.newBuilder().setName("Goofy")))
                        .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
                        .expectingOutput(
                                outputMessage(GreeterOneResponse.newBuilder()
                                        .setMessage("Greeting has been forwarded to GreeterTwo! Not waiting for a response.")),
                                outputMessage(GreeterOneResponse.newBuilder()
                                        .setMessage("Greeting has been forwarded to GreeterTwo. Response was: Hello Goofy #2")))
                        .named("GreeterOne/forwardBackgroundGreeting: async and sync inter-service calls"),
                endToEndTestInvocation()
                        .withServices(new GreeterOne(), new GreeterTwo())
                        .withInput(
                                testInput().withMethod(GreeterOneGrpc.getGetMultipleGreetingsMethod())
                                        .withMessage(GreeterOneRequest.newBuilder().setName("Goofy")))
                        .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
                        .expectingOutput(
                                outputMessage(GreeterOneResponse.newBuilder()
                                        .setMessage("Two greetings have been forwarded to GreeterTwo! Response: Hello Goofy #1, Hello Goofy #2")))
                        .named("GreeterOne/getMultipleGreetings: await multiple synchronous inter-service calls"),
                endToEndTestInvocation()
                        .withServices(new GreeterOne(), new GreeterTwo())
                        .withInput(
                                testInput().withMethod(GreeterOneGrpc.getGetOneOfMultipleGreetingsMethod())
                                        .withMessage(GreeterOneRequest.newBuilder().setName("Goofy")))
                        .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
                        .expectingOutput(
                                outputMessage(GreeterOneResponse.newBuilder()
                                        .setMessage("Two greetings have been forwarded to GreeterTwo! Response: Hello Goofy #1")))
                        .named("GreeterOne/getOneOfMultipleGreetings: await multiple synchronous inter-service calls"),
                endToEndTestInvocation()
                        .withServices(new GreeterOne())
                        .withInput(
                                testInput().withMethod(GreeterOneGrpc.getFailingGreetMethod())
                                        .withMessage(GreeterOneRequest.newBuilder().setName("Goofy")))
                        .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
                        .expectingOutput(
                                outputMessage(new IllegalStateException("Whatever")))
                        .named("GreeterOne/failingGreet: failing call"),
                endToEndTestInvocation()
                        .withServices(new GreeterOne())
                        .withInput(
                                testInput().withMethod(GreeterOneGrpc.getGreetWithSideEffectMethod())
                                        .withMessage(GreeterOneRequest.newBuilder().setName("Goofy")))
                        .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
                        .expectingOutput(
                                outputMessage(GreeterOneResponse.newBuilder().setMessage("Hello").build()))
                        .named("GreeterOne/greetWithSideEffect: side effect."),
                endToEndTestInvocation()
                        .withServices(new GreeterOne(), new GreeterTwo())
                        .withInput(
                                testInput().withMethod(GreeterOneGrpc.getSleepAndGetWokenUpMethod())
                                        .withMessage(GreeterOneRequest.newBuilder().setName("Goofy")))
                        .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
                        .expectingOutput(
                                outputMessage(GreeterOneResponse.newBuilder().setMessage("Wake up!").build()))
                        .named("GreeterOne/sleepAndGetWokenUp: awakeable"));
    }
}
