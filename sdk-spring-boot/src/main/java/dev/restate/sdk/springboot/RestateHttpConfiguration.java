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
import dev.restate.sdk.http.vertx.RestateHttpServer;
import java.util.Collections;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;

/** This class takes care of handling */
@Configuration
@EnableConfigurationProperties(RestateHttpServerProperties.class)
public class RestateHttpConfiguration {

  @Bean
  @ConditionalOnProperty(prefix = "restate.sdk.http", name = "port")
  @ConditionalOnClass(RestateHttpServer.class)
  RestateHttpEndpointBean restateHttpEndpointBean(
      @Nullable Endpoint endpoint, RestateHttpServerProperties restateHttpServerProperties) {
    return new RestateHttpEndpointBean(endpoint, restateHttpServerProperties);
  }

  @Bean
  @ConditionalOnProperty(prefix = "restate.sdk.http", name = "port")
  @ConditionalOnMissingClass(value = "dev.restate.sdk.http.vertx.RestateHttpServer")
  Object restateHttpEndpointBean() {
    throw new IllegalStateException(
        "RestateHttpEndpointBean cannot be instantiated, because you miss the dependency dev.restate:sdk-http-vertx in your classpath.");
  }

  @Bean
  @ConditionalOnProperty(prefix = "restate.sdk.http", name = "path", matchIfMissing = true)
  public SimpleUrlHandlerMapping restateRootMapping(
      @Nullable Endpoint endpoint, RestateHttpServerProperties restateHttpServerProperties) {
    String path = restateHttpServerProperties.getPath();
    if (!path.endsWith("/*")) {
      path = path.endsWith("/") ? path + "*" : path + "/*";
    }

    return new SimpleUrlHandlerMapping(
        Collections.singletonMap(
            path,
            new RestateReactiveHttpHandlerAdapter(
                endpoint, !restateHttpServerProperties.isDisableBidirectionalStreaming())),
        Ordered.LOWEST_PRECEDENCE - 1);
  }
}
