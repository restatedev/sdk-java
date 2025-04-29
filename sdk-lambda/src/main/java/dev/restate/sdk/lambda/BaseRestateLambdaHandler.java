// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import dev.restate.sdk.endpoint.Endpoint;
import org.apache.logging.log4j.CloseableThreadContext;

/**
 * Base implementation of a Lambda handler to execute restate services
 *
 * <p>Implementation of <a href="https://aws.amazon.com/lambda/">AWS Lambda</a> {@link
 * RequestHandler} for serving Restate functions.
 *
 * <p>Restate can invoke Lambda functions directly or through AWS API gateway. For both cases, it
 * will invoke the Lambda using the same envelope of an API Gateway request/response. See <a
 * href="https://docs.restate.dev/deploy/services/faas/lambda/lambda-java">Restate Lambda
 * documentation</a> for more details.
 */
public abstract class BaseRestateLambdaHandler
    implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

  private static final String AWS_REQUEST_ID = "AWSRequestId";

  private final LambdaEndpointRequestHandler lambdaEndpointRequestHandler;

  protected BaseRestateLambdaHandler() {
    Endpoint.Builder endpointBuilder = Endpoint.builder();
    register(endpointBuilder);
    this.lambdaEndpointRequestHandler = new LambdaEndpointRequestHandler(endpointBuilder.build());
  }

  /** Configure your services in this method. */
  public abstract void register(Endpoint.Builder builder);

  @Override
  public APIGatewayProxyResponseEvent handleRequest(
      APIGatewayProxyRequestEvent input, Context context) {
    try (var requestId = CloseableThreadContext.put(AWS_REQUEST_ID, context.getAwsRequestId())) {
      return lambdaEndpointRequestHandler.handleRequest(input, context);
    }
  }
}
