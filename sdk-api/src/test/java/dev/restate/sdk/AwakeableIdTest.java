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

import dev.restate.sdk.common.Serde;
import dev.restate.sdk.core.AwakeableIdTestSuite;
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import dev.restate.sdk.serde.jackson.JsonSerdes;

public class AwakeableIdTest extends AwakeableIdTestSuite {

  protected TestInvocationBuilder returnAwakeableId() {
    return testDefinitionForService(
        "ReturnAwakeableId",
        Serde.VOID,
        JsonSerdes.STRING,
        (context, unused) -> context.awakeable(JsonSerdes.STRING).id());
  }
}
