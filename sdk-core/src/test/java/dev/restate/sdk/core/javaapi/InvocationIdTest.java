// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.javaapi;

import static dev.restate.sdk.core.javaapi.JavaAPITests.testDefinitionForService;

import dev.restate.sdk.core.InvocationIdTestSuite;
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import dev.restate.sdk.core.TestSerdes;
import dev.restate.serde.Serde;

public class InvocationIdTest extends InvocationIdTestSuite {

  @Override
  protected TestInvocationBuilder returnInvocationId() {
    return testDefinitionForService(
        "ReturnInvocationId",
        Serde.VOID,
        TestSerdes.STRING,
        (ctx, unused) -> ctx.request().invocationId().toString());
  }
}
