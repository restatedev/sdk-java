// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.restate.client.Client;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@RestateTest(containerImage = "ghcr.io/restatedev/restate:main")
class CounterTest {

  @BindService private final Counter counter = new Counter();

  @Test
  @Timeout(value = 10)
  void testGreet(@RestateClient Client ingressClient) {
    var client = CounterClient.fromClient(ingressClient, "my-counter");

    long response = client.get();
    assertThat(response).isEqualTo(0L);
  }

  @Test
  @Timeout(value = 10)
  void adminUrlIsInjected(@RestateAdminURL URL adminUrl) throws Exception {
    HttpResponse<Void> response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create(adminUrl.toString()).resolve("/health")).build(),
                HttpResponse.BodyHandlers.discarding());
    assertThat(response.statusCode()).isEqualTo(200);
  }
}
