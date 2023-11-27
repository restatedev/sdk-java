// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.http.vertx

import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import com.google.protobuf.MessageLite
import dev.restate.generated.service.discovery.Discovery.ServiceDiscoveryRequest
import dev.restate.generated.service.discovery.Discovery.ServiceDiscoveryResponse
import dev.restate.generated.service.protocol.Protocol.*
import dev.restate.sdk.core.ProtoUtils.*
import dev.restate.sdk.core.testservices.*
import dev.restate.sdk.http.vertx.testservices.BlockingGreeterService
import dev.restate.sdk.http.vertx.testservices.GreeterKtService
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.*
import io.vertx.junit5.Timeout
import io.vertx.junit5.VertxExtension
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.kotlin.coroutines.receiveChannelHandler
import java.util.concurrent.*
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

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
      greetTest(vertx) { it.withService(GreeterKtService(coroutineContext = vertx.dispatcher())) }

  @Timeout(value = 1, timeUnit = TimeUnit.SECONDS)
  @Test
  fun endpointWithBlockingService(vertx: Vertx): Unit =
      greetTest(vertx) { it.withService(BlockingGreeterService()) }

  private fun greetTest(
      vertx: Vertx,
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
                .await()
                .actualPort()

        val client = vertx.createHttpClient(HTTP_CLIENT_OPTIONS)

        val request =
            client
                .request(
                    HttpMethod.POST,
                    endpointPort,
                    "localhost",
                    "/invoke/" +
                        dev.restate.sdk.core.testservices.GreeterGrpc.getGreetMethod()
                            .fullMethodName)
                .await()

        // Prepare request header
        request.setChunked(true).putHeader(HttpHeaders.CONTENT_TYPE, "application/restate")

        // Send start message and PollInputStreamEntry
        request.write(encode(startMessage(1).build()))
        request.write(encode(inputMessage(greetingRequest { name = "Francesco" })))

        val response = request.response().await()

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
        request.write(encode(completionMessage(1, "2")))

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

        assertThat(outputEntry).isInstanceOf(OutputStreamEntryMessage::class.java)
        assertThat(outputEntry as OutputStreamEntryMessage)
            .returns(
                greetingResponse { message = "Hello Francesco. Count: 3" }.toByteString(),
                OutputStreamEntryMessage::getValue)

        // Wait for closing request and response
        request.end().await()
      }

  @Test
  fun return404(vertx: Vertx): Unit =
      runBlocking(vertx.dispatcher()) {
        val endpointPort: Int =
            RestateHttpEndpointBuilder.builder(vertx)
                .withService(BlockingGreeterService())
                .withOptions(HttpServerOptions().setPort(0))
                .build()
                .listen()
                .await()
                .actualPort()

        val client = vertx.createHttpClient(HTTP_CLIENT_OPTIONS)

        val request =
            client
                .request(
                    HttpMethod.POST,
                    endpointPort,
                    "localhost",
                    "/invoke/" +
                        dev.restate.sdk.core.testservices.GreeterGrpc.getGreetMethod().serviceName +
                        "/unknownMethod")
                .await()

        // Prepare request header
        request.setChunked(true).putHeader(HttpHeaders.CONTENT_TYPE, "application/restate")
        request.write(encode(startMessage(0).build()))

        val response = request.response().await()

        // Response status should be 404
        assertThat(response.statusCode()).isEqualTo(HttpResponseStatus.NOT_FOUND.code())

        response.end().await()
      }

  @Test
  fun serviceDiscovery(vertx: Vertx): Unit =
      runBlocking(vertx.dispatcher()) {
        val endpointPort: Int =
            RestateHttpEndpointBuilder.builder(vertx)
                .withService(BlockingGreeterService())
                .withOptions(HttpServerOptions().setPort(0))
                .build()
                .listen()
                .await()
                .actualPort()

        val client = vertx.createHttpClient(HTTP_CLIENT_OPTIONS)

        // Send request
        val request =
            client.request(HttpMethod.POST, endpointPort, "localhost", "/discover").await()
        request
            .putHeader(HttpHeaders.CONTENT_TYPE, "application/proto")
            .end(Buffer.buffer(ServiceDiscoveryRequest.getDefaultInstance().toByteArray()))
            .await()

        // Assert response
        val response = request.response().await()

        // Response status and content type header
        assertThat(response.statusCode()).isEqualTo(HttpResponseStatus.OK.code())
        assertThat(response.getHeader(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/proto")

        // Parse response
        val responseBody = response.body().await()
        val serviceDiscoveryResponse = ServiceDiscoveryResponse.parseFrom(responseBody.bytes)
        assertThat(serviceDiscoveryResponse.servicesList)
            .containsOnly(dev.restate.sdk.core.testservices.GreeterGrpc.SERVICE_NAME)
        assertThat(serviceDiscoveryResponse.files.fileList)
            .map<String> { it.name }
            .containsExactlyInAnyOrder(
                "dev/restate/ext.proto", "google/protobuf/descriptor.proto", "greeter.proto")
      }

  fun encode(msg: MessageLite): Buffer {
    val buffer = Buffer.buffer(MessageEncoder.encodeLength(msg))
    val header = headerFromMessage(msg)
    buffer.appendLong(header.encode())
    buffer.appendBytes(msg.toByteArray())
    return buffer
  }
}
