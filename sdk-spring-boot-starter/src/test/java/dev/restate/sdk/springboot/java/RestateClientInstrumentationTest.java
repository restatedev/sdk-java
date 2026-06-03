// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot.java;

import static org.assertj.core.api.Assertions.assertThat;

import dev.restate.client.Client;
import dev.restate.client.jdk.JdkClient;
import dev.restate.sdk.springboot.RestateClientAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies that {@link RestateClientAutoConfiguration} wraps the underlying JDK {@link HttpClient}
 * with Micrometer's {@code MicrometerHttpClient} instrumentation when a {@code MeterRegistry} bean
 * is available.
 */
@SpringBootTest(
    classes = {
      RestateClientAutoConfiguration.class,
      RestateClientInstrumentationTest.ObservabilityConfig.class
    },
    properties = {"restate.client.base-uri=http://localhost:10000"})
public class RestateClientInstrumentationTest {

  @Configuration
  static class ObservabilityConfig {
    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }

    @Bean
    ObservationRegistry observationRegistry() {
      return TestObservationRegistry.create();
    }
  }

  @Autowired private Client client;

  @Test
  public void underlyingHttpClientShouldBeInstrumented() throws Exception {
    assertThat(underlyingHttpClient(client).getClass().getName())
        .isEqualTo("io.micrometer.java11.instrument.binder.jdk.MicrometerHttpClient");
  }

  static HttpClient underlyingHttpClient(Client client) throws Exception {
    Field field = JdkClient.class.getDeclaredField("httpClient");
    field.setAccessible(true);
    return (HttpClient) field.get(client);
  }
}
