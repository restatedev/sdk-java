// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import static dev.restate.sdk.JavaBlockingTests.testDefinitionForService;

import dev.restate.sdk.common.CoreSerdes;
import dev.restate.sdk.core.InvocationIdTestSuite;
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;

public class InvocationIdTest extends InvocationIdTestSuite {

  protected TestInvocationBuilder returnInvocationId() {
    return testDefinitionForService(
        "ReturnInvocationId",
        CoreSerdes.VOID,
        CoreSerdes.JSON_STRING,
        (ctx, unused) -> ctx.invocationId().toString());
  }
}
