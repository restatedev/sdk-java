package dev.restate.sdk.lambda;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import dev.restate.generated.service.discovery.Discovery;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.impl.MessageHeader;
import dev.restate.sdk.core.impl.ProtoUtils;
import dev.restate.sdk.lambda.testservices.CounterRequest;
import dev.restate.sdk.lambda.testservices.JavaCounterGrpc;
import dev.restate.sdk.lambda.testservices.KotlinCounterGrpc;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LambdaHandlerTest {

  @ValueSource(strings = {JavaCounterGrpc.SERVICE_NAME, KotlinCounterGrpc.SERVICE_NAME})
  @ParameterizedTest
  public void testInvoke(String serviceName) throws IOException {
    LambdaHandler handler = new LambdaHandler();

    // Mock request
    APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
    request.setHeaders(Map.of("content-type", "application/restate"));
    request.setPath("/a/path/prefix/invoke/" + serviceName + "/Get");
    request.setHttpMethod("POST");
    request.setIsBase64Encoded(true);
    request.setBody(
        Base64.getEncoder()
            .encodeToString(
                serializeEntries(
                    Protocol.StartMessage.newBuilder()
                        .setDebugId("123")
                        .setId(ByteString.copyFromUtf8("123"))
                        .setKnownEntries(1)
                        .setPartialState(true)
                        .build(),
                    Protocol.PollInputStreamEntryMessage.newBuilder()
                        .setValue(
                            CounterRequest.newBuilder()
                                .setCounterName("my-counter")
                                .build()
                                .toByteString())
                        .build())));

    // Send request
    APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext());

    // Assert response
    assertThat(response.getStatusCode()).isEqualTo(200);
    assertThat(response.getHeaders()).containsEntry("content-type", "application/restate");
    assertThat(response.getIsBase64Encoded()).isTrue();
    assertThat(response.getBody())
        .asBase64Decoded()
        .isEqualTo(
            serializeEntries(
                Protocol.GetStateEntryMessage.newBuilder()
                    .setKey(ByteString.copyFromUtf8("counter"))
                    .build(),
                Protocol.SuspensionMessage.newBuilder().addEntryIndexes(1).build()));
  }

  @Test
  public void testDiscovery() throws InvalidProtocolBufferException {
    LambdaHandler handler = new LambdaHandler();

    // Mock request
    APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
    request.setHeaders(Map.of("content-type", "application/proto"));
    request.setPath("/a/path/prefix/discover");
    request.setHttpMethod("POST");
    request.setIsBase64Encoded(true);
    request.setBody(
        Base64.getEncoder()
            .encodeToString(Discovery.ServiceDiscoveryRequest.newBuilder().build().toByteArray()));

    // Send request
    APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext());

    // Assert response
    assertThat(response.getStatusCode()).isEqualTo(200);
    assertThat(response.getHeaders()).containsEntry("content-type", "application/proto");
    assertThat(response.getIsBase64Encoded()).isTrue();
    Discovery.ServiceDiscoveryResponse decodedResponse =
        Discovery.ServiceDiscoveryResponse.parseFrom(
            Base64.getDecoder().decode(response.getBody()));
    assertThat(decodedResponse.getServicesList())
        .containsExactlyInAnyOrder(JavaCounterGrpc.SERVICE_NAME, KotlinCounterGrpc.SERVICE_NAME);
    assertThat(decodedResponse.getFiles().getFileList())
        .map(DescriptorProtos.FileDescriptorProto::getName)
        .containsExactlyInAnyOrder(
            "dev/restate/ext.proto", "google/protobuf/descriptor.proto", "counter.proto");
  }

  private static byte[] serializeEntries(MessageLite... msgs) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    for (MessageLite msg : msgs) {
      ByteBuffer headerBuf = ByteBuffer.allocate(8);
      headerBuf.putLong(ProtoUtils.headerFromMessage(msg).encode());
      outputStream.write(headerBuf.array());
      msg.writeTo(outputStream);
    }
    return outputStream.toByteArray();
  }

  private static List<MessageLite> deserializeEntries(byte[] array) throws IOException {
    List<MessageLite> msgs = new ArrayList<>();
    ByteBuffer buffer = ByteBuffer.wrap(array);
    while (buffer.hasRemaining()) {
      MessageHeader header = MessageHeader.parse(buffer.getLong());

      // Prepare the ByteBuffer and pass it to the Protobuf message parser
      ByteBuffer messageBuffer = buffer.slice();
      messageBuffer.limit(header.getLength());
      msgs.add(header.getType().messageParser().parseFrom(messageBuffer));

      // Move the buffer after this message
      buffer.position(buffer.position() + header.getLength());
    }
    return msgs;
  }

  private Context mockContext() {
    return new Context() {
      @Override
      public String getAwsRequestId() {
        return null;
      }

      @Override
      public String getLogGroupName() {
        return null;
      }

      @Override
      public String getLogStreamName() {
        return null;
      }

      @Override
      public String getFunctionName() {
        return null;
      }

      @Override
      public String getFunctionVersion() {
        return null;
      }

      @Override
      public String getInvokedFunctionArn() {
        return null;
      }

      @Override
      public CognitoIdentity getIdentity() {
        return null;
      }

      @Override
      public ClientContext getClientContext() {
        return null;
      }

      @Override
      public int getRemainingTimeInMillis() {
        return 0;
      }

      @Override
      public int getMemoryLimitInMB() {
        return 0;
      }

      @Override
      public LambdaLogger getLogger() {
        return null;
      }
    };
  }
}
