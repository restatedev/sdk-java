// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.java11.instrument.binder.jdk.MicrometerHttpClient;
import io.micrometer.observation.ObservationRegistry;
import java.net.http.HttpClient;
import org.springframework.context.ApplicationContext;

/**
 * {@link ClientInstrumentationSupport} based on Micrometer's {@link MicrometerHttpClient}.
 * Instantiated by {@link ClientInstrumentationSupport#load()} only when the instrumentation is on
 * the runtime classpath.
 */
final class MicrometerClientInstrumentationSupport implements ClientInstrumentationSupport {

  public MicrometerClientInstrumentationSupport() {}

  @Override
  public HttpClient instrument(HttpClient httpClient, ApplicationContext applicationContext) {
    MeterRegistry meterRegistry =
        applicationContext.getBeanProvider(MeterRegistry.class).getIfUnique();
    if (meterRegistry == null) {
      return httpClient;
    }
    var builder = MicrometerHttpClient.instrumentationBuilder(httpClient, meterRegistry);
    ObservationRegistry observationRegistry =
        applicationContext.getBeanProvider(ObservationRegistry.class).getIfUnique();
    if (observationRegistry != null) {
      builder.observationRegistry(observationRegistry);
    }
    return builder.build();
  }
}
