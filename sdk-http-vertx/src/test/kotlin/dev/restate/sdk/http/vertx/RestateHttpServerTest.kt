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
import dev.restate.sdk.core.TestSerdes
import dev.restate.sdk.core.generated.manifest.EndpointManifestSchema
import dev.restate.sdk.core.generated.protocol.Protocol
import dev.restate.sdk.core.statemachine.ProtoUtils.*
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.http.vertx.testservices.BlockingGreeter
import dev.restate.sdk.http.vertx.testservices.greeter
import dev.restate.sdk.kotlin.endpoint.endpoint
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.*
import io.vertx.junit5.Timeout
import io.vertx.junit5.VertxExtension
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.kotlin.coroutines.receiveChannelHandler
import java.nio.ByteBuffer
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
internal class RestateHttpServerTest {

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
      greetTest(vertx, "KtGreeter") { bind(greeter()) }

  @Timeout(value = 1, timeUnit = TimeUnit.SECONDS)
  @Test
  fun endpointWithBlockingService(vertx: Vertx): Unit =
      greetTest(vertx, BlockingGreeter::class.simpleName!!) { bind(BlockingGreeter()) }

  private fun greetTest(
      vertx: Vertx,
      componentName: String,
      initEndpoint: Endpoint.Builder.() -> Unit
  ): Unit =
      runBlocking(vertx.dispatcher()) {
        val endpoint = endpoint(initEndpoint)
        val server = RestateHttpServer.fromEndpoint(vertx, endpoint, HttpServerOptions().setPort(0))

        val endpointPort: Int = server.listen().coAwait().actualPort()

        val client = vertx.createHttpClient(HTTP_CLIENT_OPTIONS)

        val request =
            client
                .request(HttpMethod.POST, endpointPort, "localhost", "/invoke/$componentName/greet")
                .coAwait()

        // Prepare request header
        request
            .setChunked(true)
            .putHeader(HttpHeaders.CONTENT_TYPE, serviceProtocolContentTypeHeader())

        // Send start message and PollInputStreamEntry
        request.write(encode(startMessage(1).build()))
        request.write(encode(inputCmd("Francesco")))

        val response = request.response().coAwait()

        // Start the input decoder
        val inputChannel = vertx.receiveChannelHandler<MessageLite>()
        response.handler {
          bufferToMessages(listOf(ByteBuffer.wrap(it.bytes))).forEach(inputChannel::handle)
        }
        response.resume()

        // Wait for Get State Entry
        val getStateEntry = inputChannel.receive()

        assertThat(getStateEntry).isInstanceOf(Protocol.GetLazyStateCommandMessage::class.java)
        assertThat(getStateEntry as Protocol.GetLazyStateCommandMessage)
            .returns(
                ByteString.copyFromUtf8("counter"), Protocol.GetLazyStateCommandMessage::getKey)

        // Send completion
        request.write(encode(getLazyStateCompletion(1, TestSerdes.LONG, 2)))

        // Wait for Set State Entry
        val setStateEntry = inputChannel.receive()

        assertThat(setStateEntry).isInstanceOf(Protocol.SetStateCommandMessage::class.java)
        assertThat(setStateEntry as Protocol.SetStateCommandMessage)
            .returns(ByteString.copyFromUtf8("counter"), Protocol.SetStateCommandMessage::getKey)
            .returns(ByteString.copyFromUtf8("3")) { it.value.content }

        // Wait for the sleep and complete it
        val sleepEntry = inputChannel.receive()

        assertThat(sleepEntry).isInstanceOf(Protocol.SleepCommandMessage::class.java)

        // Wait a bit, then send the completion
        delay(1.seconds)
        request.write(
            encode(
                Protocol.SleepCompletionNotificationMessage.newBuilder()
                    .setCompletionId(2)
                    .setVoid(Protocol.Void.getDefaultInstance())
                    .build()))

        // Now wait for response
        val outputEntry = inputChannel.receive()

        assertThat(outputEntry).isInstanceOf(Protocol.OutputCommandMessage::class.java)
        assertThat(outputEntry).isEqualTo(outputCmd("Hello Francesco. Count: 3"))

        // Wait for closing request and response
        request.end().coAwait()
      }

  @Test
  fun return404(vertx: Vertx): Unit =
      runBlocking(vertx.dispatcher()) {
        val endpointPort: Int =
            RestateHttpServer.fromEndpoint(
                    vertx, endpoint { bind(BlockingGreeter()) }, HttpServerOptions().setPort(0))
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
            .putHeader(HttpHeaders.CONTENT_TYPE, serviceProtocolContentTypeHeader())
            .putHeader(HttpHeaders.ACCEPT, serviceProtocolContentTypeHeader())
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
                    vertx, endpoint { bind(BlockingGreeter()) }, HttpServerOptions().setPort(0))
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

        assertThat(discoveryResponse.services)
            .map<String> { it.name }
            .containsOnly(BlockingGreeter::class.java.simpleName)
      }

  private fun encode(msg: MessageLite): Buffer {
    return Buffer.buffer(Unpooled.wrappedBuffer(encodeMessageToByteBuffer(msg)))
  }
}
