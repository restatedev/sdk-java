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

import dev.restate.common.Slice;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.springboot.RestateClientAutoConfiguration;
import dev.restate.sdk.springboot.RestateEndpointConfiguration;
import dev.restate.serde.PayloadCodec;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * With multiple {@link PayloadCodec} beans, the application must fail to start rather than silently
 * not encoding the journal, unless one of them is primary.
 */
public class PayloadCodecMultipleBeansTest {

  private static PayloadCodec codec() {
    return new PayloadCodec() {
      @Override
      public CompletableFuture<Slice> encode(Slice value) {
        return CompletableFuture.completedFuture(value);
      }

      @Override
      public CompletableFuture<Slice> decode(Slice value) {
        return CompletableFuture.completedFuture(value);
      }
    };
  }

  @Configuration
  static class TwoCodecs {
    @Bean
    PayloadCodec firstCodec() {
      return codec();
    }

    @Bean
    PayloadCodec secondCodec() {
      return codec();
    }
  }

  @Configuration
  static class TwoCodecsOnePrimary {
    @Bean
    @Primary
    PayloadCodec primaryCodec() {
      return codec();
    }

    @Bean
    PayloadCodec otherCodec() {
      return codec();
    }
  }

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              RestateEndpointConfiguration.class,
              RestateClientAutoConfiguration.class,
              Greeter.class,
              ServicesConfiguration.class)
          .withPropertyValues(
              "restate.client.base-uri=http://localhost:10000", "greetingPrefix=Hello ");

  @Test
  public void multipleCodecsFailStartup() {
    contextRunner
        .withUserConfiguration(TwoCodecs.class)
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(NoUniqueBeanDefinitionException.class));
  }

  @Test
  public void primaryCodecIsUsed() {
    contextRunner
        .withUserConfiguration(TwoCodecsOnePrimary.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(Endpoint.class).getPayloadCodec())
                  .isSameAs(context.getBean("primaryCodec"));
            });
  }
}
