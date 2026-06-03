// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot;

import java.net.http.HttpClient;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationContext;

/**
 * Bridge for instrumenting the JDK {@link HttpClient} used by the autoconfigured Restate {@link
 * dev.restate.client.Client}.
 *
 * <p>Same trick as {@link OptionsSupport}: the implementation referencing Micrometer classes is
 * loaded only after checking that Micrometer's JDK HttpClient instrumentation is on the classpath,
 * so this module doesn't take a runtime dependency on it.
 */
interface ClientInstrumentationSupport {

  /** Instrument the given {@link HttpClient}. */
  HttpClient instrument(HttpClient httpClient, ApplicationContext applicationContext);

  /**
   * Load the Micrometer-based implementation, or {@code null} if Micrometer's <a
   * href="https://docs.micrometer.io/micrometer/reference/reference/java-httpclient.html">JDK
   * HttpClient instrumentation</a> ({@code io.micrometer:micrometer-java11}) isn't on the
   * classpath.
   */
  static @Nullable ClientInstrumentationSupport load() {
    try {
      Class.forName(
          "io.micrometer.java11.instrument.binder.jdk.MicrometerHttpClient",
          false,
          ClientInstrumentationSupport.class.getClassLoader());
    } catch (ClassNotFoundException | LinkageError e) {
      return null;
    }
    try {
      return (ClientInstrumentationSupport)
          Class.forName("dev.restate.sdk.springboot.MicrometerClientInstrumentationSupport")
              .getDeclaredConstructor()
              .newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to load MicrometerClientInstrumentationSupport", e);
    }
  }
}
