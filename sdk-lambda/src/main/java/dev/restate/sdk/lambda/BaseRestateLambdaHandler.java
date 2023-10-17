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

/**
 * Base implementation of a Lambda handler to execute restate services
 *
 * <p>Implementation of <a href="https://aws.amazon.com/lambda/">AWS Lambda</a> {@link
 * RequestHandler} for serving Restate functions.
 *
 * <p>Restate can invoke Lambda functions directly or through AWS API gateway. For both cases, it
 * will invoke the Lambda using the same envelope of an API Gateway request/response. See <a
 * href="https://docs.restate.dev/services/deployment/lambda">Restate Lambda documentation</a> for
 * more details.
 */
public abstract class BaseRestateLambdaHandler
    implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

  private final RestateLambdaEndpoint restateLambdaEndpoint;

  protected BaseRestateLambdaHandler() {
    RestateLambdaEndpointBuilder builder = RestateLambdaEndpoint.builder();
    register(builder);
    this.restateLambdaEndpoint = builder.build();
  }

  /** Configure your services in this method. */
  public abstract void register(RestateLambdaEndpointBuilder builder);

  @Override
  public APIGatewayProxyResponseEvent handleRequest(
      APIGatewayProxyRequestEvent input, Context context) {
    return restateLambdaEndpoint.handleRequest(input, context);
  }
}
