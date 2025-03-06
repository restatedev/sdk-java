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

import dev.restate.common.Request;
import dev.restate.common.SendRequest;
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
              SendRequest.of(target, body.toByteArray())
                  .headers(headers)
                  .idempotencyKey(idempotencyKey));
          return null;
        });
  }

  @Override
  protected TestInvocationBuilder implicitCancellation(Target target, Slice body) {
    return testDefinitionForService(
        "ImplicitCancellation",
        Serde.VOID,
        Serde.RAW,
        (context, unused) -> context.call(Request.of(target, body.toByteArray())).await());
  }
}
