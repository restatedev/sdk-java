// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.lambda.testservices;

import dev.restate.sdk.lambda.BaseRestateLambdaHandler;
import dev.restate.sdk.lambda.RestateLambdaEndpointBuilder;

public class MyServicesHandler extends BaseRestateLambdaHandler {
  @Override
  public void register(RestateLambdaEndpointBuilder builder) {
    builder.withService(new JavaCounterService()).withService(new KotlinCounterService());
  }
}
