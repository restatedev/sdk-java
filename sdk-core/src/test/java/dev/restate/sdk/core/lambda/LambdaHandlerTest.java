// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.lambda;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.restate.sdk.core.DiscoveryProtocol;
import dev.restate.sdk.core.generated.manifest.EndpointManifestSchema;
import dev.restate.sdk.core.generated.manifest.Service;
import dev.restate.sdk.core.lambda.testservices.JavaCounterServiceHandlers;
import dev.restate.sdk.core.lambda.testservices.MyServicesHandler;
import dev.restate.sdk.lambda.BaseRestateLambdaHandler;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LambdaHandlerTest {

  @Test
  public void testDiscovery() throws IOException {
    BaseRestateLambdaHandler handler = new MyServicesHandler();

    // Mock request
    APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
    request.setPath("/a/path/prefix/discover");
    request.setHeaders(Map.of("accept", DiscoveryProtocol.Version.MAX.getHeader()));

    // Send request
    APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext());

    // Assert response
    assertThat(response.getStatusCode()).isEqualTo(200);
    assertThat(response.getHeaders())
        .containsEntry("content-type", DiscoveryProtocol.Version.MAX.getHeader());
    assertThat(response.getIsBase64Encoded()).isTrue();
    byte[] decodedStringResponse = Base64.getDecoder().decode(response.getBody());
    // Compute response and write it back
    EndpointManifestSchema discoveryResponse =
        new ObjectMapper().readValue(decodedStringResponse, EndpointManifestSchema.class);

    assertThat(discoveryResponse.getServices())
        .map(Service::getName)
        .containsOnly(JavaCounterServiceHandlers.Metadata.SERVICE_NAME);
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
