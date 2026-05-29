// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.interceptor.micrometer;

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
 * <ul>
 *   <li>Per invocation: opens a {@code restate.invocation} observation with standard {@code
 *       restate.service}, {@code restate.handler}, {@code restate.handler.type}, {@code
 *       restate.invocation.id}, and {@code restate.invocation.target} key-values.
 *   <li>Per {@code ctx.run(name, ...)} call: opens a {@code restate.run} child observation with the
 *       {@code restate.run.name} key-value.
 * </ul>
 *
 * <p>Observation scope is thread-local; intended for the Java handler runner. Kotlin coroutine
 * handlers should use the Kotlin variant in this module.
 */
public final class MicrometerInterceptorFactory
    implements HandlerInterceptor.Factory, RunInterceptor.Factory {

  static final String INVOCATION_OBSERVATION = "restate.invocation";
  static final String RUN_OBSERVATION = "restate.run";

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
      String target = ctx.request().serviceName() + "/" + ctx.request().handlerName();
      // Use a ReceiverContext so any PropagatingReceiverTracingObservationHandler registered on
      // the registry (Spring Boot auto-configures one when micrometer-tracing is present) reads
      // the W3C / B3 trace context out of the attempt headers and parents this span accordingly.
      ReceiverContext<HeadersAccessor> recvCtx = new ReceiverContext<>(HeadersAccessor::get);
      recvCtx.setCarrier(ctx.attemptHeaders());
      Observation observation =
          Observation.createNotStarted(INVOCATION_OBSERVATION, () -> recvCtx, registry)
              .contextualName("restate " + target)
              .lowCardinalityKeyValue("restate.service", ctx.request().serviceName())
              .lowCardinalityKeyValue("restate.handler", ctx.request().handlerName())
              .lowCardinalityKeyValue(
                  "restate.handler.type",
                  ctx.request().handlerType() == null
                      ? "UNKNOWN"
                      : ctx.request().handlerType().name())
              .highCardinalityKeyValue(
                  "restate.invocation.id", ctx.request().invocationId().toString())
              .highCardinalityKeyValue("restate.invocation.target", target)
              .start();
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
      String name = runCtx.runName() != null ? runCtx.runName() : "run";
      Observation observation =
          Observation.createNotStarted(RUN_OBSERVATION, registry)
              .contextualName("restate run " + name)
              .lowCardinalityKeyValue("restate.run.name", name)
              .start();
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
