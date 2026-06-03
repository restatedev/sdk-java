// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot;

import dev.restate.sdk.endpoint.definition.HandlerRunner;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;

/** Language-specific bridge for constructing {@code HandlerRunner.Options}. */
interface OptionsSupport {

  /** Default options for the language. */
  HandlerRunner.Options createOptions();

  /** Set executor. */
  void setExecutor(HandlerRunner.@Nullable Options opts, Executor executor);

  /**
   * Set Micrometer tracing to the options.
   *
   * <p>Returns {@code base} (or language defaults) unchanged if the {@code
   * sdk-interceptor-micrometer} module isn't on the classpath — so callers can still use the rest
   * of {@code OptionsSupport} (e.g. {@link #setExecutor}) without it.
   *
   * @param registry an {@code io.micrometer.observation.ObservationRegistry} (typed as Object to
   *     keep this interface free of Micrometer compile-time references).
   */
  void setMicrometerTracing(HandlerRunner.@Nullable Options opts, Object registry);

  /**
   * Reflectively instantiate {@code factoryClassName} with the given Micrometer {@code
   * ObservationRegistry}. Returns {@code null} if the factory class or {@code ObservationRegistry}
   * isn't on the classpath — implementations should fall back to the unmodified base options in
   * that case.
   */
  static @Nullable Object loadMicrometerFactory(String factoryClassName, Object registry) {
    Class<?> factoryClass;
    Class<?> registryClass;
    try {
      factoryClass = Class.forName(factoryClassName);
      registryClass = Class.forName("io.micrometer.observation.ObservationRegistry");
    } catch (ClassNotFoundException | LinkageError e) {
      return null;
    }
    try {
      //noinspection JavaReflectionInvocation
      return factoryClass.getDeclaredConstructor(registryClass).newInstance(registry);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to instantiate " + factoryClassName, e);
    }
  }
}
