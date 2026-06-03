// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.interceptor.opentelemetry;

import dev.restate.sdk.interceptor.RunContextPropagator;
import io.opentelemetry.context.Context;

/** {@link RunContextPropagator} based on the OpenTelemetry {@link Context}. */
public final class OpenTelemetryRunContextPropagator implements RunContextPropagator {

  public OpenTelemetryRunContextPropagator() {}

  @Override
  public CapturedContext capture() {
    // Cheap and harmless even when GlobalOpenTelemetry is the noop instance.
    return Context.current()::wrap;
  }
}
