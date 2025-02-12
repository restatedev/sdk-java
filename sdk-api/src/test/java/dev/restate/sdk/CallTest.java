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

import dev.restate.common.Slice;
import dev.restate.common.Target;
import dev.restate.sdk.core.CallTestSuite;
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import dev.restate.serde.Serde;
import java.util.Map;

public class CallTest extends CallTestSuite {

  @Override
  protected TestInvocationBuilder oneWayCall(
      Target target, String idempotencyKey, Map<String, String> headers, Slice body) {
    return testDefinitionForService(
        "OneWayCall",
        Serde.VOID,
        Serde.VOID,
        (context, unused) -> {
          context.send(
              target,
              body.toByteArray(),
              SendOptions.builder().headers(headers).idempotencyKey(idempotencyKey).build());
          return null;
        });
  }

  @Override
  protected TestInvocationBuilder implicitCancellation(Target target, Slice body) {
    return testDefinitionForService(
        "Implicit cancellation",
        Serde.VOID,
        Serde.RAW,
        (context, unused) -> context.call(target, body.toByteArray()).await());
  }
}
