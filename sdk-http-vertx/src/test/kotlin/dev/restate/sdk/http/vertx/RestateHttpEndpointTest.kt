// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.http.vertx

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.ByteString
import com.google.protobuf.MessageLite
import dev.restate.generated.service.discovery.Discovery
import dev.restate.generated.service.protocol.Protocol.*
import dev.restate.sdk.common.CoreSerdes
import dev.restate.sdk.core.ProtoUtils.*
import dev.restate.sdk.core.ServiceProtocol
import dev.restate.sdk.core.manifest.DeploymentManifestSchema
import dev.restate.sdk.http.vertx.testservices.BlockingGreeter
import dev.restate.sdk.http.vertx.testservices.greeter
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.*
import io.vertx.junit5.Timeout
import io.vertx.junit5.VertxExtension
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.kotlin.coroutines.receiveChannelHandler
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Isolated

@Isolated
@ExtendWith(VertxExtension::class)
internal class RestateHttpEndpointTest {

  companion object {
    val HTTP_CLIENT_OPTIONS: HttpClientOptions =
        HttpClientOptions()
            // Set prior knowledge
            .setProtocolVersion(HttpVersion.HTTP_2)
            .setHttp2ClearTextUpgrade(false)
  }

  @Timeout(value = 1, timeUnit = TimeUnit.SECONDS)
  @Test
  fun endpointWithNonBlockingService(vertx: Vertx): Unit =
      greetTest(vertx, "KtGreeter") { it.bind(greeter()) }

  @Timeout(value = 1, timeUnit = TimeUnit.SECONDS)
  @Test
  fun endpointWithBlockingService(vertx: Vertx): Unit =
      greetTest(vertx, BlockingGreeter::class.simpleName!!) { it.bind(BlockingGreeter()) }

  private fun greetTest(
      vertx: Vertx,
      componentName: String,
      consumeBuilderFn: (RestateHttpEndpointBuilder) -> RestateHttpEndpointBuilder
  ): Unit =
      runBlocking(vertx.dispatcher()) {
        val endpointBuilder = RestateHttpEndpointBuilder.builder(vertx)
        consumeBuilderFn(endpointBuilder)

        val endpointPort: Int =
            endpointBuilder
                .withOptions(HttpServerOptions().setPort(0))
                .build()
                .listen()
                .coAwait()
                .actualPort()

        val client = vertx.createHttpClient(HTTP_CLIENT_OPTIONS)

        val request =
            client
                .request(HttpMethod.POST, endpointPort, "localhost", "/invoke/$componentName/greet")
                .coAwait()

        // Prepare request header
        request
            .setChunked(true)
            .putHeader(
                HttpHeaders.CONTENT_TYPE,
                ServiceProtocol.serviceProtocolVersionToHeaderValue(ServiceProtocolVersion.V1))

        // Send start message and PollInputStreamEntry
        request.write(encode(startMessage(1).build()))
        request.write(encode(inputMessage("Francesco")))

        val response = request.response().coAwait()

        // Start the input decoder
        val inputChannel = vertx.receiveChannelHandler<MessageLite>()
        val decoder = MessageDecoder()
        response.handler {
          decoder.offer(it)
          while (true) {
            val m = decoder.poll() ?: break
            inputChannel.handle(m.message())
          }
        }
        response.resume()

        // Wait for Get State Entry
        val getStateEntry = inputChannel.receive()

        assertThat(getStateEntry).isInstanceOf(GetStateEntryMessage::class.java)
        assertThat(getStateEntry as GetStateEntryMessage)
            .returns(ByteString.copyFromUtf8("counter"), GetStateEntryMessage::getKey)

        // Send completion
        request.write(
            encode(
                completionMessage(1)
                    .setValue(CoreSerdes.JSON_LONG.serializeToByteString(2))
                    .build()))

        // Wait for Set State Entry
        val setStateEntry = inputChannel.receive()

        assertThat(setStateEntry).isInstanceOf(SetStateEntryMessage::class.java)
        assertThat(setStateEntry as SetStateEntryMessage)
            .returns(ByteString.copyFromUtf8("counter"), SetStateEntryMessage::getKey)
            .returns(ByteString.copyFromUtf8("3"), SetStateEntryMessage::getValue)

        // Wait for the sleep and complete it
        val sleepEntry = inputChannel.receive()

        assertThat(sleepEntry).isInstanceOf(SleepEntryMessage::class.java)

        // Wait a bit, then send the completion
        delay(1.seconds)
        request.write(
            encode(
                CompletionMessage.newBuilder()
                    .setEntryIndex(3)
                    .setEmpty(Empty.getDefaultInstance())
                    .build()))

        // Now wait for response
        val outputEntry = inputChannel.receive()

        assertThat(outputEntry).isInstanceOf(OutputEntryMessage::class.java)
        assertThat(outputEntry).isEqualTo(outputMessage("Hello Francesco. Count: 3"))

        // Wait for closing request and response
        request.end().coAwait()
      }

  @Test
  fun return404(vertx: Vertx): Unit =
      runBlocking(vertx.dispatcher()) {
        val endpointPort: Int =
            RestateHttpEndpointBuilder.builder(vertx)
                .bind(BlockingGreeter())
                .withOptions(HttpServerOptions().setPort(0))
                .build()
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
                    "/invoke/" + BlockingGreeter::class.java.simpleName + "/unknownMethod")
                .coAwait()

        // Prepare request header
        request
            .setChunked(true)
            .putHeader(
                HttpHeaders.CONTENT_TYPE,
                ServiceProtocol.serviceProtocolVersionToHeaderValue(ServiceProtocolVersion.V1))
            .putHeader(
                HttpHeaders.ACCEPT,
                ServiceProtocol.serviceProtocolVersionToHeaderValue(ServiceProtocolVersion.V1))
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
            RestateHttpEndpointBuilder.builder(vertx)
                .bind(BlockingGreeter())
                .withOptions(HttpServerOptions().setPort(0))
                .build()
                .listen()
                .coAwait()
                .actualPort()

        val client = vertx.createHttpClient(HTTP_CLIENT_OPTIONS)

        // Send request
        val request =
            client.request(HttpMethod.GET, endpointPort, "localhost", "/discover").coAwait()
        request.putHeader(
            HttpHeaders.ACCEPT,
            ServiceProtocol.serviceDiscoveryProtocolVersionToHeaderValue(
                Discovery.ServiceDiscoveryProtocolVersion.V1))
        request.end().coAwait()

        // Assert response
        val response = request.response().coAwait()

        // Response status and content type header
        assertThat(response.statusCode()).isEqualTo(HttpResponseStatus.OK.code())
        assertThat(response.getHeader(HttpHeaders.CONTENT_TYPE))
            .isEqualTo(
                ServiceProtocol.serviceDiscoveryProtocolVersionToHeaderValue(
                    Discovery.ServiceDiscoveryProtocolVersion.V1))

        // Parse response
        val responseBody = response.body().coAwait()
        // Compute response and write it back
        val discoveryResponse: DeploymentManifestSchema =
            ObjectMapper().readValue(responseBody.bytes, DeploymentManifestSchema::class.java)

        assertThat(discoveryResponse.services)
            .map<String> { it.name }
            .containsOnly(BlockingGreeter::class.java.simpleName)
      }

  fun encode(msg: MessageLite): Buffer {
    val buffer = Buffer.buffer(MessageEncoder.encodeLength(msg))
    val header = headerFromMessage(msg)
    buffer.appendLong(header.encode())
    buffer.appendBytes(msg.toByteArray())
    return buffer
  }
}
