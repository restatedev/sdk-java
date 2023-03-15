package dev.restate.sdk.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

/**
 * Implementation of <a href="https://aws.amazon.com/lambda/">AWS Lambda</a> {@link RequestHandler}
 * for serving Restate functions through <a href="https://aws.amazon.com/api-gateway/">AWS API
 * Gateway</a>.
 */
public final class LambdaHandler
    implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

  @Override
  public APIGatewayProxyResponseEvent handleRequest(
      APIGatewayProxyRequestEvent input, Context context) {
    return LambdaRestateServer.getInstance().handleRequest(input, context);
  }
}
