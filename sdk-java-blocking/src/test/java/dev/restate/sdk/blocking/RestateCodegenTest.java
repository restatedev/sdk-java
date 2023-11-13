package dev.restate.sdk.blocking;

import static dev.restate.sdk.core.impl.ProtoUtils.*;
import static dev.restate.sdk.core.impl.TestDefinitions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.impl.testservices.*;
import java.time.Duration;
import java.util.stream.Stream;

public class RestateCodegenTest implements TestSuite {

  private static class UseRestateClientAndServerCodegen
      extends GreeterRestate.GreeterRestateImplBase {

    @Override
    public GreetingResponse greet(RestateContext context, GreetingRequest request) {
      GreeterRestate.GreeterRestateClient client = GreeterRestate.newClient(context);
      client.delayed(Duration.ofSeconds(1)).greet(request);
      client.oneWay().greet(request);
      return client.greet(request).await();
    }
  }

  @Override
  public Stream<TestDefinition> definitions() {
    return Stream.of(
        testInvocation(new UseRestateClientAndServerCodegen(), GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(greetingRequest("Francesco")),
                completionMessage(3, greetingResponse("Till")))
            .onlyUnbuffered()
            .assertingOutput(
                msgs -> {
                  assertThat(msgs)
                      .element(0)
                      .asInstanceOf(type(Protocol.BackgroundInvokeEntryMessage.class))
                      .satisfies(
                          backgroundInvokeEntryMessage -> {
                            // Check invoke time is non zero
                            assertThat(backgroundInvokeEntryMessage.getInvokeTime())
                                .isGreaterThan(0);
                            // Check background invoke header
                            assertThat(
                                    backgroundInvokeEntryMessage.toBuilder()
                                        .clearInvokeTime()
                                        .build())
                                .isEqualTo(
                                    backgroundInvokeMessage(
                                            GreeterGrpc.getGreetMethod(),
                                            greetingRequest("Francesco"))
                                        .build());
                          });
                  assertThat(msgs)
                      .elements(1, 2, 3)
                      .containsExactly(
                          backgroundInvokeMessage(
                                  GreeterGrpc.getGreetMethod(), greetingRequest("Francesco"))
                              .build(),
                          invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco"))
                              .build(),
                          outputMessage(greetingResponse("Till")));
                }));
  }
}
