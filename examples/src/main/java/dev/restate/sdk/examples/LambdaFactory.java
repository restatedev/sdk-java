package dev.restate.sdk.examples;

import dev.restate.sdk.lambda.LambdaRestateServer;
import dev.restate.sdk.lambda.LambdaRestateServerBuilder;
import dev.restate.sdk.lambda.LambdaRestateServerFactory;
import java.util.Objects;
import java.util.regex.Pattern;

public class LambdaFactory implements LambdaRestateServerFactory {

  @Override
  public LambdaRestateServer create() {
    LambdaRestateServerBuilder builder = LambdaRestateServer.builder();

    for (String serviceClass :
        Objects.requireNonNullElse(
                System.getenv("LAMBDA_FACTORY_SERVICE_CLASS"), Counter.class.getCanonicalName())
            .split(Pattern.quote(","))) {
      if (Counter.class.getCanonicalName().equals(serviceClass)) {
        builder.withService(new Counter());
      } else if (VanillaGrpcCounter.class.getCanonicalName().equals(serviceClass)) {
        builder.withService(new VanillaGrpcCounter());
      } else if (CounterKt.class.getCanonicalName().equals(serviceClass)) {
        builder.withService(new CounterKt());
      } else {
        throw new IllegalArgumentException(
            "Bad \"LAMBDA_FACTORY_SERVICE_CLASS\" env: " + serviceClass);
      }
    }

    return builder.build();
  }
}
