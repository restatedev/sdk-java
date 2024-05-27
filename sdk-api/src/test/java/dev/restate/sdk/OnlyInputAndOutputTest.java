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

import dev.restate.sdk.common.JsonSerdes;
import dev.restate.sdk.core.OnlyInputAndOutputTestSuite;
import dev.restate.sdk.core.TestDefinitions;

public class OnlyInputAndOutputTest extends OnlyInputAndOutputTestSuite {

  protected TestDefinitions.TestInvocationBuilder noSyscallsGreeter() {
    return testDefinitionForService(
        "NoSyscallsGreeter",
        JsonSerdes.STRING,
        JsonSerdes.STRING,
        (ctx, input) -> "Hello " + input);
  }
}
