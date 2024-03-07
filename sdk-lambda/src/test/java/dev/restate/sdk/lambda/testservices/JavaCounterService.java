// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.lambda.testservices;

import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.VirtualObject;
import dev.restate.sdk.common.Serde;
import dev.restate.sdk.common.StateKey;
import java.nio.charset.StandardCharsets;

@VirtualObject(name = "JavaCounter")
public class JavaCounterService {

  public static final StateKey<Long> COUNTER =
      StateKey.of(
          "counter",
          Serde.using(
              l -> l.toString().getBytes(StandardCharsets.UTF_8),
              v -> Long.parseLong(new String(v, StandardCharsets.UTF_8))));

  @Handler
  public Long get(ObjectContext context) {
    return context.get(COUNTER).orElse(-1L);
  }
}
