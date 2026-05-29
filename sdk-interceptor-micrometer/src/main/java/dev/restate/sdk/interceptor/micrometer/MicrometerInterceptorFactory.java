// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.interceptor.micrometer;

import static dev.restate.sdk.interceptor.micrometer.MicrometerHelpers.*;

import dev.restate.sdk.endpoint.HeadersAccessor;
import dev.restate.sdk.interceptor.HandlerInterceptor;
import dev.restate.sdk.interceptor.RunInterceptor;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.ReceiverContext;
import org.jspecify.annotations.Nullable;

/**
 * Micrometer Observation interceptor factory for Java handlers. Implements both {@link
 * HandlerInterceptor.Factory} and {@link RunInterceptor.Factory} so a single registration covers
 * both invocation- and run-level observations.
 *
 * <p>Spans produced through the Micrometer → tracing bridge mirror those emitted by {@code
 * sdk-interceptor-opentelemetry}: {@code attempt <target>} for the handler and {@code run (<name>)}
 * for each {@code ctx.run}, with {@code restate.invocation.id}, {@code restate.invocation.target},
 * and {@code restate.run.name} attributes.
 *
 * <p>Observation scope is thread-local; intended for the Java handler runner. Kotlin coroutine
 * handlers should use the Kotlin variant in this module.
 */
public final class MicrometerInterceptorFactory
    implements HandlerInterceptor.Factory, RunInterceptor.Factory {

  private final ObservationRegistry registry;

  // If you change this constructor, change the reflections in spring boot module too
  public MicrometerInterceptorFactory(ObservationRegistry registry) {
    this.registry = registry;
  }

  @Override
  public @Nullable HandlerInterceptor createHandlerInterceptor() {
    if (registry.isNoop()) {
      return null;
    }

    return (ctx, next) -> {
      ReceiverContext<HeadersAccessor> recvCtx = headersReceiverContext(ctx.attemptHeaders());
      Observation observation = startHandlerObservation(registry, recvCtx, ctx.request());
      try (Observation.Scope ignored = observation.openScope()) {
        next.proceed();
      } catch (Throwable t) {
        observation.error(t);
        throw t;
      } finally {
        observation.stop();
      }
    };
  }

  @Override
  public @Nullable RunInterceptor createRunInterceptor() {
    if (registry.isNoop()) {
      return null;
    }

    return (runCtx, next) -> {
      Observation observation = startRunObservation(registry, runCtx.runName());
      try (Observation.Scope ignored = observation.openScope()) {
        next.proceed();
      } catch (Throwable t) {
        observation.error(t);
        throw t;
      } finally {
        observation.stop();
      }
    };
  }
}
