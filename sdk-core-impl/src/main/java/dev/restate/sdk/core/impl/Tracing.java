// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.impl;

import io.opentelemetry.api.common.AttributeKey;

final class Tracing {

  private Tracing() {}

  static AttributeKey<String> RESTATE_INVOCATION_ID =
      AttributeKey.stringKey("restate.invocation.id");

  static AttributeKey<String> RESTATE_STATE_KEY = AttributeKey.stringKey("restate.state.key");
  static AttributeKey<Long> RESTATE_SLEEP_WAKE_UP_TIME =
      AttributeKey.longKey("restate.sleep.wake_up_time");

  static AttributeKey<String> RESTATE_COORDINATION_CALL_SERVICE =
      AttributeKey.stringKey("restate.coordination.call.service");
  static AttributeKey<String> RESTATE_COORDINATION_CALL_METHOD =
      AttributeKey.stringKey("restate.coordination.call.method");
}
