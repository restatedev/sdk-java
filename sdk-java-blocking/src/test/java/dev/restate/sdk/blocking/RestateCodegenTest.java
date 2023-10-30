package dev.restate.sdk.blocking;

import static dev.restate.sdk.core.impl.ProtoUtils.*;
import static dev.restate.sdk.core.impl.TestDefinitions.*;

import dev.restate.sdk.core.impl.testservices.*;
import java.util.stream.Stream;

public class RestateCodegenTest implements TestSuite {

  private static class UseRestateClientAndServerCodegen
      extends GreeterRestate.GreeterRestateImplBase {

    @Override
    public GreetingResponse greet(RestateContext context, GreetingRequest request) {
      GreeterRestate.GreeterRestateClient client = GreeterRestate.newClient();
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
                completionMessage(1, greetingResponse("Till")))
            .onlyUnbuffered()
            .expectingOutput(
                invokeMessage(GreeterGrpc.getGreetMethod(), greetingRequest("Francesco")),
                outputMessage(greetingResponse("Till"))));
  }
}
