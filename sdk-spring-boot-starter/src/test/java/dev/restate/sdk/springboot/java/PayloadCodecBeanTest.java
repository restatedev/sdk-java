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
import dev.restate.client.base.BaseClient;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.springboot.RestateClientAutoConfiguration;
import dev.restate.sdk.springboot.RestateEndpointConfiguration;
import dev.restate.serde.PayloadCodec;
import dev.restate.serde.SerdePipeline;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Verifies that a {@link PayloadCodec} bean is picked up by both the endpoint and client. */
@SpringBootTest(
    classes = {
      RestateEndpointConfiguration.class,
      RestateClientAutoConfiguration.class,
      Greeter.class,
      ServicesConfiguration.class,
      PayloadCodecBeanTest.CodecConfig.class
    },
    properties = {"restate.client.base-uri=http://localhost:10000", "greetingPrefix=Hello "})
public class PayloadCodecBeanTest {

  @Configuration
  static class CodecConfig {
    @Bean
    PayloadCodec payloadCodec() {
      return PayloadCodec.NOOP;
    }
  }

  @Autowired private PayloadCodec payloadCodec;
  @Autowired private Endpoint endpoint;
  @Autowired private Client client;

  @Test
  public void endpointUsesCodecBean() {
    assertThat(endpoint.getPayloadCodec()).isSameAs(payloadCodec);
  }

  @Test
  public void clientUsesCodecBean() throws Exception {
    Field field = BaseClient.class.getDeclaredField("serdePipeline");
    field.setAccessible(true);
    assertThat(((SerdePipeline) field.get(client)).payloadCodec()).isSameAs(payloadCodec);
  }
}
