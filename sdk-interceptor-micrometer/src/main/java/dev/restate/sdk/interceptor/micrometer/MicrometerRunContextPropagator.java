// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.interceptor.micrometer;

import dev.restate.sdk.interceptor.RunContextPropagator;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshotFactory;

/** {@link RunContextPropagator} based on Micrometer's {@link ContextSnapshotFactory}. */
public final class MicrometerRunContextPropagator implements RunContextPropagator {

  private static final ContextSnapshotFactory SNAPSHOT_FACTORY =
      ContextSnapshotFactory.builder().contextRegistry(ContextRegistry.getInstance()).build();

  public MicrometerRunContextPropagator() {}

  @Override
  public CapturedContext capture() {
    return SNAPSHOT_FACTORY.captureAll()::wrap;
  }
}
