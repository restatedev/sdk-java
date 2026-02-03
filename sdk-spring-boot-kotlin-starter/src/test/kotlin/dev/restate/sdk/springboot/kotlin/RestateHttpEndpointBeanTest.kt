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
import dev.restate.sdk.springboot.RestateEndpointConfiguration
import dev.restate.sdk.springboot.RestateHttpConfiguration
import dev.restate.sdk.springboot.RestateHttpEndpointBean
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    classes =
        [RestateEndpointConfiguration::class, RestateHttpConfiguration::class, Greeter::class],
    properties = ["restate.sdk.http.port=0"],
)
class RestateHttpEndpointBeanTest {
  @Autowired lateinit var restateHttpEndpointBean: RestateHttpEndpointBean

  @Test
  @Throws(IOException::class, InterruptedException::class)
  fun httpEndpointShouldBeRunning() {
    assertThat(restateHttpEndpointBean.isRunning).isTrue()
    assertThat(restateHttpEndpointBean.actualPort()).isPositive()

    // Check if discovery replies containing the Greeter service
    val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build()
    val response =
        client.send(
            HttpRequest.newBuilder()
                .GET()
                .version(HttpClient.Version.HTTP_2)
                .uri(
                    URI.create("http://localhost:${restateHttpEndpointBean.actualPort()}/discover")
                )
                .header("Accept", "application/vnd.restate.endpointmanifest.v1+json")
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    assertThat(response.version()).isEqualTo(HttpClient.Version.HTTP_2)
    assertThat(response.statusCode()).isEqualTo(200)

    val endpointManifest =
        ObjectMapper().readValue(response.body(), EndpointManifestSchema::class.java)

    assertThat(endpointManifest.services).map<String> { it?.name }.containsOnly("greeter")
  }
}
