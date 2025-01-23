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
import java.util.Collections;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Restate's {@link Client}, to send requests to Restate services.
 *
 * @see RestateClientProperties
 */
@Configuration
@EnableConfigurationProperties(RestateClientProperties.class)
public class RestateClientAutoConfiguration {

  @Bean
  public Client client(RestateClientProperties restateClientProperties) {
    Map<String, String> headers = restateClientProperties.getHeaders();
    if (headers == null) {
      headers = Collections.emptyMap();
    }
    return Client.connect(restateClientProperties.getBaseUri(), headers);
  }
}
