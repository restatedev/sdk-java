// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot.kotlin

import com.fasterxml.jackson.databind.ObjectMapper
import dev.restate.sdk.core.generated.manifest.EndpointManifestSchema
import dev.restate.sdk.core.generated.manifest.Service
import dev.restate.sdk.springboot.RestateHttpEndpointBean
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    classes = [RestateHttpEndpointBean::class, Greeter::class],
    properties = ["restate.sdk.http.port=0"])
class RestateHttpEndpointBeanTest {
  @Autowired lateinit var restateHttpEndpointBean: RestateHttpEndpointBean

  @Test
  @Throws(IOException::class, InterruptedException::class)
  fun httpEndpointShouldBeRunning() {
    assertThat(restateHttpEndpointBean.isRunning).isTrue()
    assertThat(restateHttpEndpointBean.actualPort()).isPositive()

    // Check if discovery replies containing the Greeter service
    val client = HttpClient.newHttpClient()
    val response =
        client.send<String?>(
            HttpRequest.newBuilder()
                .GET()
                .uri(
                    URI.create(
                        ("http://localhost:" + restateHttpEndpointBean.actualPort()).toString() +
                            "/discover"))
                .header("Accept", "application/vnd.restate.endpointmanifest.v1+json")
                .build(),
            HttpResponse.BodyHandlers.ofString())
    Assertions.assertThat(response.statusCode()).isEqualTo(200)

    val endpointManifest =
        ObjectMapper()
            .readValue<EndpointManifestSchema>(response.body(), EndpointManifestSchema::class.java)

    Assertions.assertThat<Service?>(endpointManifest.services)
        .map<String>({ it.name })
        .containsOnly("greeter")
  }
}
