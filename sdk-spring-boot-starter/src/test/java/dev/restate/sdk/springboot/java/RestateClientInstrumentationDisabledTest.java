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
import dev.restate.sdk.springboot.RestateClientAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies that {@link RestateClientAutoConfiguration} leaves the underlying JDK HttpClient
 * untouched when no {@code MeterRegistry} bean is available, even with Micrometer on the classpath.
 */
@SpringBootTest(
    classes = RestateClientAutoConfiguration.class,
    properties = {"restate.client.base-uri=http://localhost:10000"})
public class RestateClientInstrumentationDisabledTest {

  @Autowired private Client client;

  @Test
  public void underlyingHttpClientShouldNotBeInstrumented() throws Exception {
    assertThat(
            RestateClientInstrumentationTest.underlyingHttpClient(client)
                .getClass()
                .getSimpleName())
        .doesNotContain("Micrometer");
  }
}
