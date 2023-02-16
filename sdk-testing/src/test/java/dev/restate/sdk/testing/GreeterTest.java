package dev.restate.sdk.testing;

import static dev.restate.sdk.testing.ProtoUtils.*;
import static dev.restate.sdk.testing.RestateTestDriver.TestCaseBuilder.TestInvocationBuilder.endToEndTestInvocation;
import static dev.restate.sdk.testing.RestateTestDriver.TestInput.Builder.testInput;

import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.testing.testservices.*;
import java.util.stream.Stream;
import services.AwakeService;
import services.GreeterOne;
import services.GreeterThree;
import services.GreeterTwo;

public class GreeterTest extends RestateTestDriver {

  @Override
  Stream<TestDefinition> definitions() {
    return Stream.of(
        endToEndTestInvocation()
            .withServices(new GreeterOne())
            .withInput(
                testInput()
                    .withMethod(GreeterOneGrpc.getGreetMethod())
                    .withMessage(greeterOneRequest("Goofy")))
            .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
            .expectingOutput(greeterOneResponse("Hello Goofy"))
            .named("GreeterOne/greet: send response"),
        endToEndTestInvocation()
            .withServices(new GreeterOne())
            .withInput(
                testInput()
                    .withMethod(GreeterOneGrpc.getStoreAndGreetMethod())
                    .withMessage(greeterOneRequest("Goofy")))
            .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
            .expectingOutput(greeterOneResponse("Hello Goofy"))
            .named("GreeterOne/storeAndGreet: get and set state"),
        endToEndTestInvocation()
            .withServices(new GreeterOne())
            .withInput(
                testInput()
                    .withMethod(GreeterOneGrpc.getCountGreetingsMethod())
                    .withMessage(greeterOneRequest("Goofy")),
                testInput()
                    .withMethod(GreeterOneGrpc.getCountGreetingsMethod())
                    .withMessage(greeterOneRequest("Pluto")))
            .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
            .expectingOutput(
                greeterOneResponse("Hello Goofy #1"), greeterOneResponse("Hello Pluto #1"))
            .named("GreeterOne/countGreetings: get and set state for multiple keys"),
        endToEndTestInvocation()
            .withServices(new GreeterOne())
            .withInput(
                testInput()
                    .withMethod(GreeterOneGrpc.getCountGreetingsMethod())
                    .withMessage(greeterOneRequest("Goofy")),
                testInput()
                    .withMethod(GreeterOneGrpc.getCountGreetingsMethod())
                    .withMessage(greeterOneRequest("Pluto")),
                testInput()
                    .withMethod(GreeterOneGrpc.getCountGreetingsMethod())
                    .withMessage(greeterOneRequest("Goofy")),
                testInput()
                    .withMethod(GreeterOneGrpc.getCountGreetingsMethod())
                    .withMessage(greeterOneRequest("Goofy")),
                testInput()
                    .withMethod(GreeterOneGrpc.getCountGreetingsMethod())
                    .withMessage(greeterOneRequest("Pluto")),
                testInput()
                    .withMethod(GreeterOneGrpc.getCountGreetingsMethod())
                    .withMessage(greeterOneRequest("Pluto")))
            .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
            .expectingOutput(
                greeterOneResponse("Hello Goofy #1"),
                greeterOneResponse("Hello Pluto #1"),
                greeterOneResponse("Hello Goofy #2"),
                greeterOneResponse("Hello Goofy #3"),
                greeterOneResponse("Hello Pluto #2"),
                greeterOneResponse("Hello Pluto #3"))
            .named("GreeterOne/countGreetings: get and set state for multiple keys multiple times"),
        endToEndTestInvocation()
            .withServices(new GreeterOne())
            .withInput(
                testInput()
                    .withMethod(GreeterOneGrpc.getCountGreetingsMethod())
                    .withMessage(greeterOneRequest("Goofy")),
                testInput()
                    .withMethod(GreeterOneGrpc.getCountGreetingsMethod())
                    .withMessage(greeterOneRequest("Goofy")),
                testInput()
                    .withMethod(GreeterOneGrpc.getResetGreetingCounterMethod())
                    .withMessage(greeterOneRequest("Goofy")))
            .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
            .expectingOutput(
                greeterOneResponse("Hello Goofy #1"),
                greeterOneResponse("Hello Goofy #2"),
                greeterOneResponse("State got cleared"))
            .named("GreeterOne/resetGreetingCounter: set and clear state"),
        endToEndTestInvocation()
            .withServices(new GreeterOne())
            .withInput(
                testInput()
                    .withMethod(GreeterOneGrpc.getCountGreetingsMethod())
                    .withMessage(greeterOneRequest("Goofy")),
                testInput()
                    .withMethod(GreeterOneGrpc.getResetGreetingCounterMethod())
                    .withMessage(greeterOneRequest("Goofy")),
                testInput()
                    .withMethod(GreeterOneGrpc.getCountGreetingsMethod())
                    .withMessage(greeterOneRequest("Goofy")))
            .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
            .expectingOutput(
                greeterOneResponse("Hello Goofy #1"),
                greeterOneResponse("State got cleared"),
                greeterOneResponse("Hello Goofy #1"))
            .named("GreeterOne/resetGreetingCounter: set state, clear state, set state"),
        endToEndTestInvocation()
            .withServices(new GreeterOne(), new GreeterTwo())
            .withInput(
                testInput()
                    .withMethod(GreeterOneGrpc.getForwardGreetingMethod())
                    .withMessage(greeterOneRequest("Goofy")))
            .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
            .expectingOutput(
                outputMessage(
                    GreeterOneResponse.newBuilder()
                        .setMessage(
                            "Greeting has been forwarded to GreeterTwo. Response was: Hello Goofy #1")))
            .named("GreeterOne/forwardGreeting: synchronous inter-service call"),
        endToEndTestInvocation()
            .withServices(new GreeterOne(), new GreeterTwo())
            .withInput(
                testInput()
                    .withMethod(GreeterOneGrpc.getForwardBackgroundGreetingMethod())
                    .withMessage(greeterOneRequest("Goofy")),
                testInput()
                    .withMethod(GreeterOneGrpc.getForwardGreetingMethod())
                    .withMessage(greeterOneRequest("Goofy")))
            .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
            .expectingOutput(
                greeterOneResponse(
                    "Greeting has been forwarded to GreeterTwo! Not waiting for a response."),
                greeterOneResponse(
                    "Greeting has been forwarded to GreeterTwo. Response was: Hello Goofy #2"))
            .named("GreeterOne/forwardBackgroundGreeting: async and sync inter-service calls"),
        endToEndTestInvocation()
            .withServices(new GreeterOne(), new GreeterTwo())
            .withInput(
                testInput()
                    .withMethod(GreeterOneGrpc.getForwardBackgroundGreetingMethod())
                    .withMessage(greeterOneRequest("Goofy")),
                testInput()
                    .withMethod(GreeterTwoGrpc.getCountForwardedGreetingsMethod())
                    .withMessage(GreeterTwoRequest.newBuilder().setName("Goofy")),
                testInput()
                    .withMethod(GreeterOneGrpc.getForwardGreetingMethod())
                    .withMessage(greeterOneRequest("Goofy")))
            .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
            .expectingOutput(
                greeterOneResponse(
                    "Greeting has been forwarded to GreeterTwo! Not waiting for a response."),
                outputMessage(GreeterTwoResponse.newBuilder().setMessage("Hello Goofy #2")),
                greeterOneResponse(
                    "Greeting has been forwarded to GreeterTwo. Response was: Hello Goofy #3"))
            .named(
                "GreeterOne/forwardGreeting: async and sync inter-service calls to different services"),
        endToEndTestInvocation()
            .withServices(new GreeterOne(), new GreeterTwo())
            .withInput(
                testInput()
                    .withMethod(GreeterOneGrpc.getGetMultipleGreetingsMethod())
                    .withMessage(greeterOneRequest("Goofy")))
            .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
            .expectingOutput(
                greeterOneResponse(
                    "Two greetings have been forwarded to GreeterTwo! "
                        + "Response: Hello Goofy #1, Hello Goofy #2"))
            .named(
                "GreeterOne/getMultipleGreetings: await multiple synchronous inter-service calls"),
        endToEndTestInvocation()
            .withServices(new GreeterOne(), new GreeterTwo())
            .withInput(
                testInput()
                    .withMethod(GreeterOneGrpc.getGetOneOfMultipleGreetingsMethod())
                    .withMessage(greeterOneRequest("Goofy")))
            .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
            .expectingOutput(
                greeterOneResponse(
                    "Two greetings have been forwarded to GreeterTwo! Response: Hello Goofy #1"))
            .named(
                "GreeterOne/getOneOfMultipleGreetings: await multiple synchronous inter-service calls"),
        endToEndTestInvocation()
            .withServices(new GreeterOne())
            .withInput(
                testInput()
                    .withMethod(GreeterOneGrpc.getFailingGreetMethod())
                    .withMessage(greeterOneRequest("Goofy")))
            .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
            .expectingOutput(outputMessage(new IllegalStateException("Whatever")))
            .named("GreeterOne/failingGreet: failing call"),
        endToEndTestInvocation()
            .withServices(new GreeterOne())
            .withInput(
                testInput()
                    .withMethod(GreeterOneGrpc.getGreetWithSideEffectMethod())
                    .withMessage(greeterOneRequest("Goofy")))
            .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
            .expectingOutput(greeterOneResponse("Hello"))
            .named("GreeterOne/greetWithSideEffect: side effect."),
        endToEndTestInvocation()
            .withServices(new GreeterOne(), new AwakeService())
            .withInput(
                testInput()
                    .withMethod(GreeterOneGrpc.getSleepAndGetWokenUpMethod())
                    .withMessage(greeterOneRequest("Goofy")))
            .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
            .expectingOutput(greeterOneResponse("Wake up!"))
            .named("GreeterOne/sleepAndGetWokenUp: awakeable and unkeyed service"),
        endToEndTestInvocation()
            .withServices(new GreeterOne(), new GreeterThree())
            .withInput(
                testInput()
                    .withMethod(GreeterThreeGrpc.getCountAllGreetingsMethod())
                    .withMessage(greeterThreeRequest("Goofy")),
                testInput()
                    .withMethod(GreeterThreeGrpc.getCountAllGreetingsMethod())
                    .withMessage(greeterThreeRequest("Pluto")),
                testInput()
                    .withMethod(GreeterThreeGrpc.getCountAllGreetingsMethod())
                    .withMessage(greeterThreeRequest("Pluto")),
                testInput()
                    .withMethod(GreeterThreeGrpc.getCountAllGreetingsMethod())
                    .withMessage(greeterThreeRequest("Goofy")))
            .usingThreadingModels(ThreadingModel.BUFFERED_SINGLE_THREAD)
            .expectingOutput(
                greeterThreeResponse("Hello Goofy, you are greeter #1"),
                greeterThreeResponse("Hello Pluto, you are greeter #2"),
                greeterThreeResponse("Hello Pluto, you are greeter #3"),
                greeterThreeResponse("Hello Goofy, you are greeter #4"))
            .named("GreeterThree/countAllGreetings: singleton service"));
  }

  private GreeterOneRequestOrBuilder greeterOneRequest(String name) {
    return GreeterOneRequest.newBuilder().setName(name);
  }

  private Protocol.OutputStreamEntryMessage greeterOneResponse(String message) {
    return outputMessage(GreeterOneResponse.newBuilder().setMessage(message));
  }

  private GreeterThreeRequestOrBuilder greeterThreeRequest(String name) {
    return GreeterThreeRequest.newBuilder().setName(name);
  }

  private Protocol.OutputStreamEntryMessage greeterThreeResponse(String message) {
    return outputMessage(GreeterThreeResponse.newBuilder().setMessage(message));
  }
}
