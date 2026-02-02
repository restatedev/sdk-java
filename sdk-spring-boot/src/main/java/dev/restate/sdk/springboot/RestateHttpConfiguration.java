// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot;

import dev.restate.sdk.endpoint.Endpoint;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RestateHttpServerProperties.class)
public class RestateHttpConfiguration {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Nullable
  @Bean
  RestateHttpEndpointBean restateHttpEndpointBean(
      @Nullable Endpoint endpoint, RestateHttpServerProperties restateHttpServerProperties) {
    if (endpoint == null) {
      logger.info("No Endpoint was injected, SDK server will not start");
      // Don't start anything if no service is registered
      return null;
    }
    return new RestateHttpEndpointBean(endpoint, restateHttpServerProperties);
  }
}
