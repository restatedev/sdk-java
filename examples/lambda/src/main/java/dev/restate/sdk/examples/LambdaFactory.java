package dev.restate.sdk.examples;

import dev.restate.sdk.lambda.LambdaRestateServer;
import dev.restate.sdk.lambda.LambdaRestateServerFactory;

public class LambdaFactory implements LambdaRestateServerFactory {
  @Override
  public LambdaRestateServer create() {
    // For example purpose, we implement the same service both in Java and Kotlin
    if (System.getenv("USE_KOTLIN") != null) {
      return LambdaRestateServer.builder().withService(new Counter()).build();
    }

    return LambdaRestateServer.builder().withService(new BlockingCounter()).build();
  }
}
