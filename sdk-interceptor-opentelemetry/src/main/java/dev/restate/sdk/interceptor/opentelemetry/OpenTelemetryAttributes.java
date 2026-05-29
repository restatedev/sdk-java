// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.interceptor.opentelemetry;

import io.opentelemetry.api.common.AttributeKey;

/** Standard OpenTelemetry attribute keys used by the Restate interceptors. */
public final class OpenTelemetryAttributes {
  private OpenTelemetryAttributes() {}

  public static final String INSTRUMENTATION_NAME = "dev.restate.sdk.interceptor.opentelemetry";

  public static final AttributeKey<String> INVOCATION_ID =
      AttributeKey.stringKey("restate.invocation.id");
  public static final AttributeKey<String> INVOCATION_TARGET =
      AttributeKey.stringKey("restate.invocation.target");
  public static final AttributeKey<String> RUN_NAME = AttributeKey.stringKey("restate.run.name");
}
