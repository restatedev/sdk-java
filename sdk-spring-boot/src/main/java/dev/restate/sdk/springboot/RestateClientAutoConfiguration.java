// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot;

import dev.restate.client.Client;
import dev.restate.client.RequestOptions;
import dev.restate.client.jdk.JdkClient;
import java.net.http.HttpClient;
import java.util.Collections;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Restate's {@link Client}, to send requests to Restate services.
 *
 * <h2>Auto instrumentation</h2>
 *
 * <p>When Micrometer's <a
 * href="https://docs.micrometer.io/micrometer/reference/reference/java-httpclient.html">JDK
 * HttpClient instrumentation</a> is on the classpath and a {@code MeterRegistry} bean is available,
 * the underlying {@link HttpClient} is instrumented. To enable it, make sure {@code
 * spring-boot-starter-actuator} and {@code io.micrometer:micrometer-java11} are in the classpath.
 *
 * @see RestateClientProperties
 */
@Configuration
@EnableConfigurationProperties(RestateClientProperties.class)
public class RestateClientAutoConfiguration {

  /** Micrometer instrumentation support, resolved at class-load time. */
  private static final @Nullable ClientInstrumentationSupport CLIENT_INSTRUMENTATION_SUPPORT =
      ClientInstrumentationSupport.load();

  /**
   * @deprecated Use {@link #restateClient(ApplicationContext, RestateClientProperties)} instead.
   */
  @Deprecated(forRemoval = true)
  public Client client(RestateClientProperties restateClientProperties) {
    Map<String, String> headers = restateClientProperties.getHeaders();
    if (headers == null) {
      headers = Collections.emptyMap();
    }
    return Client.connect(
        restateClientProperties.getBaseUri(), RequestOptions.withHeaders(headers).build());
  }

  @Bean
  public Client restateClient(
      ApplicationContext context, RestateClientProperties restateClientProperties) {
    Map<String, String> headers = restateClientProperties.getHeaders();
    if (headers == null) {
      headers = Collections.emptyMap();
    }

    HttpClient httpClient = HttpClient.newHttpClient();
    if (CLIENT_INSTRUMENTATION_SUPPORT != null) {
      httpClient = CLIENT_INSTRUMENTATION_SUPPORT.instrument(httpClient, context);
    }

    return JdkClient.of(
        httpClient,
        restateClientProperties.getBaseUri(),
        null,
        RequestOptions.withHeaders(headers).build());
  }
}
