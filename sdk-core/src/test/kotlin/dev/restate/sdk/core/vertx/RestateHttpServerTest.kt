// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.vertx

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.MessageLite
import dev.restate.sdk.core.generated.manifest.EndpointManifestSchema
import dev.restate.sdk.core.statemachine.ProtoUtils.*
import dev.restate.sdk.endpoint.definition.HandlerDefinition
import dev.restate.sdk.endpoint.definition.HandlerType
import dev.restate.sdk.endpoint.definition.ServiceDefinition
import dev.restate.sdk.endpoint.definition.ServiceType
import dev.restate.sdk.http.vertx.RestateHttpServer
import dev.restate.sdk.kotlin.HandlerRunner
import dev.restate.sdk.kotlin.ObjectContext
import dev.restate.sdk.kotlin.endpoint.endpoint
import dev.restate.sdk.kotlin.stateKey
import dev.restate.serde.kotlinx.*
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.*
import io.vertx.junit5.VertxExtension
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.kotlin.coroutines.dispatcher
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.LogManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Isolated

@Isolated
@ExtendWith(VertxExtension::class)
internal class RestateHttpServerTest {

  companion object {
    val HTTP_CLIENT_OPTIONS: HttpClientOptions =
        HttpClientOptions()
            // Set prior knowledge
            .setProtocolVersion(HttpVersion.HTTP_2)
            .setHttp2ClearTextUpgrade(false)

    private val LOG = LogManager.getLogger()
    private val COUNTER = stateKey<Long>("counter")

    const val GREETER_NAME = "Greeter"

    fun greeter(): ServiceDefinition =
        ServiceDefinition.of(
            GREETER_NAME,
            ServiceType.VIRTUAL_OBJECT,
            listOf(
                HandlerDefinition.of(
                    "greet",
                    HandlerType.EXCLUSIVE,
                    jsonSerde<String>(),
                    jsonSerde<String>(),
                    HandlerRunner.of(KotlinSerializationSerdeFactory()) {
                        ctx: ObjectContext,
                        request: String ->
                      LOG.info("Greet invoked!")

                      val count = (ctx.get(COUNTER) ?: 0) + 1
                      ctx.set(COUNTER, count)

                      ctx.sleep(1.seconds)

                      "Hello $request. Count: $count"
                    })))
  }

  @Test
  fun return404(vertx: Vertx): Unit =
      runBlocking(vertx.dispatcher()) {
        val endpointPort: Int =
            RestateHttpServer.fromEndpoint(
                    vertx, endpoint { bind(greeter()) }, HttpServerOptions().setPort(0))
                .listen()
                .coAwait()
                .actualPort()

        val client = vertx.createHttpClient(HTTP_CLIENT_OPTIONS)

        val request =
            client
                .request(
                    HttpMethod.POST,
                    endpointPort,
                    "localhost",
                    "/invoke/$GREETER_NAME/unknownMethod")
                .coAwait()

        // Prepare request header
        request
            .setChunked(true)
            .putHeader(HttpHeaders.CONTENT_TYPE, serviceProtocolContentTypeHeader(false))
            .putHeader(HttpHeaders.ACCEPT, serviceProtocolContentTypeHeader(false))
        request.write(encode(startMessage(0).build()))

        val response = request.response().coAwait()

        // Response status should be 404
        assertThat(response.statusCode()).isEqualTo(HttpResponseStatus.NOT_FOUND.code())

        response.end().coAwait()
      }

  @Test
  fun serviceDiscovery(vertx: Vertx): Unit =
      runBlocking(vertx.dispatcher()) {
        val endpointPort: Int =
            RestateHttpServer.fromEndpoint(
                    vertx, endpoint { bind(greeter()) }, HttpServerOptions().setPort(0))
                .listen()
                .coAwait()
                .actualPort()

        val client = vertx.createHttpClient(HTTP_CLIENT_OPTIONS)

        // Send request
        val request =
            client.request(HttpMethod.GET, endpointPort, "localhost", "/discover").coAwait()
        request.putHeader(HttpHeaders.ACCEPT, serviceProtocolDiscoveryContentTypeHeader())
        request.end().coAwait()

        // Assert response
        val response = request.response().coAwait()

        // Response status and content type header
        assertThat(response.statusCode()).isEqualTo(HttpResponseStatus.OK.code())
        assertThat(response.getHeader(HttpHeaders.CONTENT_TYPE))
            .isEqualTo(serviceProtocolDiscoveryContentTypeHeader())

        // Parse response
        val responseBody = response.body().coAwait()
        // Compute response and write it back
        val discoveryResponse: EndpointManifestSchema =
            ObjectMapper().readValue(responseBody.bytes, EndpointManifestSchema::class.java)

        assertThat(discoveryResponse.services).map<String> { it.name }.containsOnly(GREETER_NAME)
      }

  private fun encode(msg: MessageLite): Buffer {
    return Buffer.buffer(Unpooled.wrappedBuffer(encodeMessageToByteBuffer(msg)))
  }
}
