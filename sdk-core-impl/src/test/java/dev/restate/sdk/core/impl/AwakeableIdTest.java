package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.CoreTestRunner.TestCaseBuilder.testInvocation;
import static dev.restate.sdk.core.impl.ProtoUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.stub.StreamObserver;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Stream;

class AwakeableIdTest extends CoreTestRunner {

  private static class ReturnAwakeableId extends GreeterGrpc.GreeterImplBase
      implements RestateBlockingService {

    @Override
    public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
      String id = restateContext().awakeable(TypeTag.STRING_UTF8).id();
      responseObserver.onNext(greetingResponse(id));
      responseObserver.onCompleted();
    }
  }

  @Override
  Stream<TestDefinition> definitions() {
    UUID id = UUID.randomUUID();
    String debugId = id.toString();
    byte[] serializedId = serializeUUID(id);

    ByteBuffer expectedAwakeableId = ByteBuffer.allocate(serializedId.length + 4);
    expectedAwakeableId.put(serializedId);
    expectedAwakeableId.putInt(1);
    expectedAwakeableId.rewind();
    String base64ExpectedAwakeableId =
        Base64.getUrlEncoder().encodeToString(expectedAwakeableId.array());

    return Stream.of(
        testInvocation(new ReturnAwakeableId(), GreeterGrpc.getGreetMethod())
            .withInput(
                Protocol.StartMessage.newBuilder()
                    .setDebugId(debugId)
                    .setId(ByteString.copyFrom(serializedId))
                    .setKnownEntries(1),
                inputMessage(GreetingRequest.getDefaultInstance()))
            .usingThreadingModels(ThreadingModel.UNBUFFERED_MULTI_THREAD)
            .assertingOutput(
                messages -> {
                  assertThat(messages)
                      .element(0)
                      .isInstanceOf(Protocol.AwakeableEntryMessage.class);
                  assertThat(messages)
                      .element(1)
                      .asInstanceOf(type(Protocol.OutputStreamEntryMessage.class))
                      .extracting(
                          out -> {
                            try {
                              return GreetingResponse.parseFrom(out.getValue()).getMessage();
                            } catch (InvalidProtocolBufferException e) {
                              throw new RuntimeException(e);
                            }
                          })
                      .isEqualTo(base64ExpectedAwakeableId);
                }));
  }

  private byte[] serializeUUID(UUID uuid) {
    ByteBuffer serializedId = ByteBuffer.allocate(16);
    serializedId.putLong(uuid.getMostSignificantBits());
    serializedId.putLong(uuid.getLeastSignificantBits());
    serializedId.rewind();
    return serializedId.array();
  }
}
