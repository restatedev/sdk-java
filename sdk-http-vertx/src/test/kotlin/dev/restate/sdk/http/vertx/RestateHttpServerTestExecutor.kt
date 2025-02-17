// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.http.vertx

import dev.restate.sdk.core.TestDefinitions.TestDefinition
import dev.restate.sdk.core.TestDefinitions.TestExecutor
import dev.restate.sdk.core.statemachine.ProtoUtils
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.endpoint.definition.ServiceDefinition
import io.netty.buffer.Unpooled
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerOptions
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.kotlin.coroutines.dispatcher
import java.nio.ByteBuffer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

class RestateHttpServerTestExecutor(private val vertx: Vertx) : TestExecutor {
  override fun buffered(): Boolean {
    return false
  }

  override fun executeTest(definition: TestDefinition) {
    runBlocking(vertx.dispatcher()) {
      // Build server
      val endpointBuilder =
          Endpoint.builder()
              .bind(definition.serviceDefinition as ServiceDefinition, definition.serviceOptions)
      if (definition.isEnablePreviewContext()) {
        endpointBuilder.enablePreviewContext()
      }

      // Start server
      val server =
          RestateHttpServer.fromEndpoint(
              vertx, endpointBuilder.build(), HttpServerOptions().setPort(0))
      server.listen().coAwait()

      val client = vertx.createHttpClient(RestateHttpServerTest.HTTP_CLIENT_OPTIONS)

      val request =
          client
              .request(
                  HttpMethod.POST,
                  server.actualPort(),
                  "localhost",
                  "/invoke/${definition.serviceDefinition.serviceName}/${definition.method}")
              .coAwait()

      // Prepare request header and send them
      request
          .setChunked(true)
          .putHeader(
              HttpHeaders.CONTENT_TYPE,
              ProtoUtils.serviceProtocolContentTypeHeader(definition.isEnablePreviewContext))
          .putHeader(
              HttpHeaders.ACCEPT,
              ProtoUtils.serviceProtocolContentTypeHeader(definition.isEnablePreviewContext))
      request.sendHead().coAwait()

      launch {
        for (msg in definition.input) {
          request
              .write(
                  Buffer.buffer(
                      Unpooled.wrappedBuffer(ProtoUtils.invocationInputToByteString(msg))))
              .coAwait()
          yield()
        }

        request.end().coAwait()
      }

      val response = request.response().coAwait()

      // Start the response receiver
      val inputChannel = Channel<Buffer>()
      response.handler { launch(vertx.dispatcher()) { inputChannel.send(it) } }
      response.endHandler { inputChannel.close() }
      response.resume()

      // Collect all the output messages
      val buffers = inputChannel.receiveAsFlow().toList()

      definition.outputAssert.accept(
          ProtoUtils.bufferToMessages(buffers.map { ByteBuffer.wrap(it.bytes) }))

      // Close the server
      server.close().coAwait()
    }
  }
}
