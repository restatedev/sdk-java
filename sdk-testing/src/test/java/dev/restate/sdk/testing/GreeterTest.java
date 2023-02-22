package dev.restate.sdk.testing;

import static org.assertj.core.api.Assertions.*;

import dev.restate.sdk.testing.services.AwakeService;
import dev.restate.sdk.testing.services.GreeterOne;
import dev.restate.sdk.testing.services.GreeterThree;
import dev.restate.sdk.testing.services.GreeterTwo;
import dev.restate.sdk.testing.testservices.*;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GreeterTest {

  TestRestateRuntime runtime;

  @BeforeEach
  public void init() {
    runtime =
        TestRestateRuntime.init(
            new GreeterOne(), new GreeterTwo(), new GreeterThree(), new AwakeService());
  }

  @AfterEach
  public void teardown() {
    runtime.close();
  }

  @Test
  @DisplayName("GreeterOne/greet: send response")
  void greetTest() {
    GreeterOneResponse response =
        runtime.invoke(GreeterOneGrpc.getGreetMethod(), greeterOneRequest("User1"));

    assertThat(greeterOneResponse("Hello User1")).isEqualTo(response);
  }

  @Test
  @DisplayName("GreeterOne/storeAndGreet: get and set state")
  void storeAndGreetTest() {
    GreeterOneResponse response =
        runtime.invoke(GreeterOneGrpc.getStoreAndGreetMethod(), greeterOneRequest("User1"));

    assertThat(greeterOneResponse("Hello User1")).isEqualTo(response);
  }

  @Test
  @DisplayName("GreeterOne/countGreetings: get and set state for multiple keys")
  void countGreetingsTest() {
    List<GreeterOneResponse> responses =
        List.of(
            runtime.invoke(GreeterOneGrpc.getCountGreetingsMethod(), greeterOneRequest("User1")),
            runtime.invoke(GreeterOneGrpc.getCountGreetingsMethod(), greeterOneRequest("User2")));

    assertThat(List.of(greeterOneResponse("Hello User1 #1"), greeterOneResponse("Hello User2 #1")))
        .isEqualTo(responses);
  }

  @Test
  @DisplayName("GreeterOne/countGreetings: get and set state for multiple keys multiple times")
  void countMultipleGreetingsTest() {
    List<GreeterOneResponse> responses =
        List.of(
            runtime.invoke(GreeterOneGrpc.getCountGreetingsMethod(), greeterOneRequest("User1")),
            runtime.invoke(GreeterOneGrpc.getCountGreetingsMethod(), greeterOneRequest("User2")),
            runtime.invoke(GreeterOneGrpc.getCountGreetingsMethod(), greeterOneRequest("User1")),
            runtime.invoke(GreeterOneGrpc.getCountGreetingsMethod(), greeterOneRequest("User1")),
            runtime.invoke(GreeterOneGrpc.getCountGreetingsMethod(), greeterOneRequest("User2")),
            runtime.invoke(GreeterOneGrpc.getCountGreetingsMethod(), greeterOneRequest("User2")));

    assertThat(
            List.of(
                greeterOneResponse("Hello User1 #1"),
                greeterOneResponse("Hello User2 #1"),
                greeterOneResponse("Hello User1 #2"),
                greeterOneResponse("Hello User1 #3"),
                greeterOneResponse("Hello User2 #2"),
                greeterOneResponse("Hello User2 #3")))
        .isEqualTo(responses);
  }

  @Test
  @DisplayName("GreeterOne/resetGreetingCounter: set and clear state")
  void resetGreetingCounter() {
    List<GreeterOneResponse> responses =
        List.of(
            runtime.invoke(GreeterOneGrpc.getCountGreetingsMethod(), greeterOneRequest("User1")),
            runtime.invoke(GreeterOneGrpc.getCountGreetingsMethod(), greeterOneRequest("User1")),
            runtime.invoke(
                GreeterOneGrpc.getResetGreetingCounterMethod(), greeterOneRequest("User1")));

    assertThat(
            List.of(
                greeterOneResponse("Hello User1 #1"),
                greeterOneResponse("Hello User1 #2"),
                greeterOneResponse("State got cleared")))
        .isEqualTo(responses);
  }

  @Test
  @DisplayName("GreeterOne/resetGreetingCounter: set state, clear state, set state")
  void resetAndSetGreetingTest() {
    List<GreeterOneResponse> responses =
        List.of(
            runtime.invoke(GreeterOneGrpc.getCountGreetingsMethod(), greeterOneRequest("User1")),
            runtime.invoke(
                GreeterOneGrpc.getResetGreetingCounterMethod(), greeterOneRequest("User1")),
            runtime.invoke(GreeterOneGrpc.getCountGreetingsMethod(), greeterOneRequest("User1")));

    assertThat(
            List.of(
                greeterOneResponse("Hello User1 #1"),
                greeterOneResponse("State got cleared"),
                greeterOneResponse("Hello User1 #1")))
        .isEqualTo(responses);
  }

  @Test
  @DisplayName("GreeterOne/forwardGreeting: synchronous inter-service call")
  void forwardGreetingTest() {
    GreeterOneResponse response =
        runtime.invoke(GreeterOneGrpc.getForwardGreetingMethod(), greeterOneRequest("User1"));

    assertThat(
            GreeterOneResponse.newBuilder()
                .setMessage(
                    "Greeting has been forwarded to GreeterTwo. Response was: Hello User1 #1")
                .build())
        .isEqualTo(response);
  }

  @Test
  @DisplayName("GreeterOne/forwardBackgroundGreeting: async and sync inter-service calls")
  void forwardBackgroundGreetingTest() {
    List<GreeterOneResponse> responses =
        List.of(
            runtime.invoke(
                GreeterOneGrpc.getForwardBackgroundGreetingMethod(), greeterOneRequest("User1")),
            runtime.invoke(GreeterOneGrpc.getForwardGreetingMethod(), greeterOneRequest("User1")));

    assertThat(
            List.of(
                greeterOneResponse(
                    "Greeting has been forwarded to GreeterTwo! Not waiting for a response."),
                greeterOneResponse(
                    "Greeting has been forwarded to GreeterTwo. Response was: Hello User1 #2")))
        .isEqualTo(responses);
  }

  @Test
  @DisplayName(
      "GreeterOne/forwardGreeting: async and sync inter-service calls to different services")
  void asyncAndSyncCallsTest() {
    GreeterOneResponse response1 =
        runtime.invoke(
            GreeterOneGrpc.getForwardBackgroundGreetingMethod(), greeterOneRequest("User1"));
    runtime.invoke(
        GreeterTwoGrpc.getCountForwardedGreetingsMethod(),
        GreeterTwoRequest.newBuilder().setName("User1").build());
    GreeterOneResponse response2 =
        runtime.invoke(GreeterOneGrpc.getForwardGreetingMethod(), greeterOneRequest("User1"));

    assertThat(
            greeterOneResponse(
                "Greeting has been forwarded to GreeterTwo! Not waiting for a response."))
        .isEqualTo(response1);
    assertThat(
            greeterOneResponse(
                "Greeting has been forwarded to GreeterTwo. Response was: Hello User1 #3"))
        .isEqualTo(response2);
  }

  @Test
  @DisplayName("GreeterOne/getMultipleGreetings: await multiple synchronous inter-service calls")
  void getMultipleGreetingsTest() {
    GreeterOneResponse response1 =
        runtime.invoke(GreeterOneGrpc.getGetMultipleGreetingsMethod(), greeterOneRequest("User1"));

    assertThat(
            greeterOneResponse(
                "Two greetings have been forwarded to GreeterTwo! Response: Hello User1 #1, Hello User1 #2"))
        .isEqualTo(response1);
  }

  @Test
  @DisplayName(
      "GreeterOne/getOneOfMultipleGreetings: await multiple synchronous inter-service calls")
  void getOneOfMultipleGreetings() {
    GreeterOneResponse response1 =
        runtime.invoke(
            GreeterOneGrpc.getGetOneOfMultipleGreetingsMethod(), greeterOneRequest("User1"));

    assertThat(
            greeterOneResponse(
                "Two greetings have been forwarded to GreeterTwo! Response: Hello User1 #1"))
        .isEqualTo(response1);
  }

  @Test
  @DisplayName("GreeterOne/greetWithSideEffect: side effect.")
  void greetWithSideEffectTest() {
    GreeterOneResponse response =
        runtime.invoke(GreeterOneGrpc.getGreetWithSideEffectMethod(), greeterOneRequest("User1"));

    assertThat(greeterOneResponse("Hello")).isEqualTo(response);
  }

  @Test
  @DisplayName("GreeterOne/sleepAndGetWokenUp: awakeable and unkeyed service")
  void sleepAndGetWokenUpTest() {
    GreeterOneResponse response =
        runtime.invoke(GreeterOneGrpc.getSleepAndGetWokenUpMethod(), greeterOneRequest("User1"));

    assertThat(greeterOneResponse("Wake up!")).isEqualTo(response);
  }

  @Test
  @DisplayName("GreeterThree/countAllGreetings: singleton service")
  void countAllGreetingsTest() {
    List<GreeterThreeResponse> responses =
        List.of(
            runtime.invoke(
                GreeterThreeGrpc.getCountAllGreetingsMethod(), greeterThreeRequest("User1")),
            runtime.invoke(
                GreeterThreeGrpc.getCountAllGreetingsMethod(), greeterThreeRequest("User2")),
            runtime.invoke(
                GreeterThreeGrpc.getCountAllGreetingsMethod(), greeterThreeRequest("User2")),
            runtime.invoke(
                GreeterThreeGrpc.getCountAllGreetingsMethod(), greeterThreeRequest("User1")));

    assertThat(
            List.of(
                greeterThreeResponse("Hello User1, you are greeter #1"),
                greeterThreeResponse("Hello User2, you are greeter #2"),
                greeterThreeResponse("Hello User2, you are greeter #3"),
                greeterThreeResponse("Hello User1, you are greeter #4")))
        .isEqualTo(responses);
  }

  private GreeterOneRequest greeterOneRequest(String name) {
    return GreeterOneRequest.newBuilder().setName(name).build();
  }

  private GreeterOneResponse greeterOneResponse(String message) {
    return GreeterOneResponse.newBuilder().setMessage(message).build();
  }

  private GreeterThreeRequest greeterThreeRequest(String name) {
    return GreeterThreeRequest.newBuilder().setName(name).build();
  }

  private GreeterThreeResponse greeterThreeResponse(String message) {
    return GreeterThreeResponse.newBuilder().setMessage(message).build();
  }
}
