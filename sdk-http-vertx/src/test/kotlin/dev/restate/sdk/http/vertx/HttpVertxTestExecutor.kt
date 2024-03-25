// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.http.vertx

import com.google.protobuf.MessageLite
import dev.restate.sdk.core.TestDefinitions.TestDefinition
import dev.restate.sdk.core.TestDefinitions.TestExecutor
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerOptions
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions

class HttpVertxTestExecutor(private val vertx: Vertx) : TestExecutor {
  override fun buffered(): Boolean {
    return false
  }

  override fun executeTest(definition: TestDefinition) {
    runBlocking(vertx.dispatcher()) {
      // This test infra supports only components returning one component definition
      val componentDefinition = definition.component.definitions()
      Assertions.assertThat(componentDefinition).size().isEqualTo(1)

      // Build server
      val server =
          RestateHttpEndpointBuilder.builder(vertx)
              .withOptions(HttpServerOptions().setPort(0))
              .bind(definition.component)
              .build()
      server.listen().coAwait()

      val client = vertx.createHttpClient(RestateHttpEndpointTest.HTTP_CLIENT_OPTIONS)

      val request =
          client
              .request(
                  HttpMethod.POST,
                  server.actualPort(),
                  "localhost",
                  "/invoke/${componentDefinition.get(0).fullyQualifiedComponentName}/${definition.method}")
              .coAwait()

      // Prepare request header and send them
      request.setChunked(true).putHeader(HttpHeaders.CONTENT_TYPE, "application/restate")
      request.sendHead().coAwait()

      launch {
        for (msg in definition.input) {
          val buffer = Buffer.buffer(MessageEncoder.encodeLength(msg.message()))
          buffer.appendLong(msg.header().encode())
          buffer.appendBytes(msg.message().toByteArray())
          request.write(buffer).coAwait()
          yield()
        }

        request.end().coAwait()
      }

      val response = request.response().coAwait()

      // Start the coroutine to send input messages

      // Start the response receiver
      val inputChannel = Channel<MessageLite>()
      val decoder = MessageDecoder()
      response.handler {
        decoder.offer(it)
        while (true) {
          val m = decoder.poll() ?: break
          launch(vertx.dispatcher()) { inputChannel.send(m.message()) }
        }
      }
      response.endHandler { inputChannel.close() }
      response.resume()

      // Collect all the output messages
      val messages = inputChannel.receiveAsFlow().toList()
      definition.outputAssert.accept(messages)

      // Close the server
      server.close().coAwait()
    }
  }
}
