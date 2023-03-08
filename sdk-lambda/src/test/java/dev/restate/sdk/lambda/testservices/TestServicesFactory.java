package dev.restate.sdk.lambda.testservices;

import dev.restate.sdk.lambda.LambdaRestateServer;
import dev.restate.sdk.lambda.LambdaRestateServerFactory;

public class TestServicesFactory implements LambdaRestateServerFactory {
  @Override
  public LambdaRestateServer create() {
    return LambdaRestateServer.builder()
        .withService(new JavaCounterService())
        .withService(new KotlinCounterService())
        .build();
  }
}
