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

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.restate.sdk.core.manifest.EndpointManifestSchema;
import dev.restate.sdk.springboot.RestateHttpEndpointBean;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {RestateHttpEndpointBean.class, Greeter.class},
    properties = {"restate.sdk.http.port=0"})
public class RestateHttpEndpointBeanTest {

  @Autowired private RestateHttpEndpointBean restateHttpEndpointBean;

  @Test
  public void httpEndpointShouldBeRunning() throws IOException, InterruptedException {
    assertThat(restateHttpEndpointBean.isRunning()).isTrue();
    assertThat(restateHttpEndpointBean.actualPort()).isPositive();

    // Check if discovery replies containing the Greeter service
    var client = HttpClient.newHttpClient();
    var response =
        client.send(
            HttpRequest.newBuilder()
                .GET()
                .uri(
                    URI.create(
                        "http://localhost:" + restateHttpEndpointBean.actualPort() + "/discover"))
                .header("Accept", "application/vnd.restate.endpointmanifest.v1+json")
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);

    var endpointManifest =
        new ObjectMapper().readValue(response.body(), EndpointManifestSchema.class);

    assertThat(endpointManifest.getServices())
        .map(dev.restate.sdk.core.manifest.Service::getName)
        .containsOnly("greeter");
  }
}
