// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package my.restate.sdk.examples;

import dev.restate.sdk.lambda.BaseRestateLambdaHandler;
import dev.restate.sdk.lambda.RestateLambdaEndpointBuilder;
import java.util.Objects;
import java.util.regex.Pattern;

public class LambdaHandler extends BaseRestateLambdaHandler {

  @Override
  public void register(RestateLambdaEndpointBuilder builder) {
    for (String serviceClass :
        Objects.requireNonNullElse(
                System.getenv("LAMBDA_FACTORY_SERVICE_CLASS"), Counter.class.getCanonicalName())
            .split(Pattern.quote(","))) {
      if (Counter.class.getCanonicalName().equals(serviceClass)) {
        builder.with(new Counter());
      } else if (CounterKt.class.getCanonicalName().equals(serviceClass)) {
        builder.with(new CounterKt());
      } else {
        throw new IllegalArgumentException(
            "Bad \"LAMBDA_FACTORY_SERVICE_CLASS\" env: " + serviceClass);
      }
    }
  }
}
