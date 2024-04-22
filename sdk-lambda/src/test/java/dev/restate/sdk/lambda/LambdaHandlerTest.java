// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.lambda;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.ProtoUtils;
import dev.restate.sdk.core.manifest.DeploymentManifestSchema;
import dev.restate.sdk.core.manifest.Service;
import dev.restate.sdk.lambda.testservices.JavaCounterClient;
import dev.restate.sdk.lambda.testservices.MyServicesHandler;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LambdaHandlerTest {

  @ValueSource(strings = {JavaCounterClient.SERVICE_NAME, "KtCounter"})
  @ParameterizedTest
  public void testInvoke(String serviceName) throws IOException {
    MyServicesHandler handler = new MyServicesHandler();

    // Mock request
    APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
    request.setHeaders(Map.of("content-type", "application/restate"));
    request.setPath("/a/path/prefix/invoke/" + serviceName + "/get");
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
                    Protocol.InputEntryMessage.newBuilder().setValue(ByteString.EMPTY).build())));

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
  public void testDiscovery() throws IOException {
    BaseRestateLambdaHandler handler = new MyServicesHandler();

    // Mock request
    APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
    request.setPath("/a/path/prefix/discover");

    // Send request
    APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext());

    // Assert response
    assertThat(response.getStatusCode()).isEqualTo(200);
    assertThat(response.getHeaders()).containsEntry("content-type", "application/json");
    assertThat(response.getIsBase64Encoded()).isTrue();
    byte[] decodedStringResponse = Base64.getDecoder().decode(response.getBody());
    // Compute response and write it back
    DeploymentManifestSchema discoveryResponse =
        new ObjectMapper().readValue(decodedStringResponse, DeploymentManifestSchema.class);

    assertThat(discoveryResponse.getServices())
        .map(Service::getName)
        .containsOnly(JavaCounterClient.SERVICE_NAME, "KtCounter");
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
