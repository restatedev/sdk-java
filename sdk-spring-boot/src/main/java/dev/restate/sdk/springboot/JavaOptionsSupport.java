// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot;

import dev.restate.sdk.HandlerRunner;
import dev.restate.sdk.endpoint.definition.HandlerRunner.Options;
import dev.restate.sdk.interceptor.HandlerInterceptor;
import dev.restate.sdk.interceptor.RunInterceptor;
import java.util.concurrent.Executor;

/**
 * {@link OptionsSupport} for the Java handler runner. Instantiated by {@link
 * RestateEndpointConfiguration} only when {@code dev.restate.sdk.HandlerRunner} is on the runtime
 * classpath.
 *
 * <p>References Micrometer classes only via reflection in {@link #setMicrometerTracing}, so a user
 * can pull in {@code sdk-spring-boot} for the executor/options machinery without taking a runtime
 * dependency on {@code sdk-interceptor-micrometer} or {@code micrometer-observation}.
 */
final class JavaOptionsSupport implements OptionsSupport {

  private static final String FACTORY_CLASS_NAME =
      "dev.restate.sdk.interceptor.micrometer.MicrometerInterceptorFactory";

  public JavaOptionsSupport() {}

  @Override
  public Options createOptions() {
    return new HandlerRunner.Options();
  }

  @Override
  public void setExecutor(Options opts, Executor executor) {
    ((HandlerRunner.Options) opts).setExecutor(executor);
  }

  @Override
  public void setMicrometerTracing(Options opts, Object registry) {
    Object factory = OptionsSupport.loadMicrometerFactory(FACTORY_CLASS_NAME, registry);
    if (factory == null) {
      // sdk-interceptor-micrometer isn't on the classpath — leave options unmodified.
      return;
    }
    HandlerRunner.Options javaOpts = (HandlerRunner.Options) opts;
    javaOpts.addHandlerInterceptorFactory((HandlerInterceptor.Factory) factory);
    javaOpts.addRunInterceptorFactory((RunInterceptor.Factory) factory);
  }
}
