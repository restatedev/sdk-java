package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.CoreTestRunner.TestCaseBuilder.testInvocation;
import static dev.restate.sdk.core.impl.ProtoUtils.*;

import com.google.protobuf.ByteString;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import io.grpc.BindableService;
import java.util.stream.Stream;

public abstract class InvocationIdTestSuite extends CoreTestRunner {

  protected abstract BindableService returnInvocationId();

  @Override
  protected Stream<TestDefinition> definitions() {
    String debugId = "my-debug-id";
    ByteString id = ByteString.copyFromUtf8(debugId);

    return Stream.of(
        testInvocation(this::returnInvocationId, GreeterGrpc.getGreetMethod())
            .withInput(
                Protocol.StartMessage.newBuilder().setDebugId(debugId).setId(id).setKnownEntries(1),
                inputMessage(GreetingRequest.getDefaultInstance()))
            .usingThreadingModels(ThreadingModel.UNBUFFERED_MULTI_THREAD)
            .expectingOutput(outputMessage(greetingResponse(debugId))));
  }
}
