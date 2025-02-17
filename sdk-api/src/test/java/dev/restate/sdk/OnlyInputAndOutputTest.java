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

import dev.restate.sdk.core.OnlyInputAndOutputTestSuite;
import dev.restate.sdk.core.TestDefinitions;
import dev.restate.sdk.core.TestSerdes;

public class OnlyInputAndOutputTest extends OnlyInputAndOutputTestSuite {

  @Override
  protected TestDefinitions.TestInvocationBuilder noSyscallsGreeter() {
    return testDefinitionForService(
        "NoSyscallsGreeter",
        TestSerdes.STRING,
        TestSerdes.STRING,
        (ctx, input) -> "Hello " + input);
  }
}
