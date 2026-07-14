// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.client.kotlin

import dev.restate.client.Client
import dev.restate.client.RequestOptions
import dev.restate.client.Response
import dev.restate.client.ResponseHead
import dev.restate.client.ScopedClient
import dev.restate.client.SendResponse
import dev.restate.client.base.BaseClient
import dev.restate.common.Output
import dev.restate.common.Request
import dev.restate.common.Slice
import dev.restate.sdk.annotation.Exclusive
import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Name
import dev.restate.sdk.annotation.Service
import dev.restate.sdk.annotation.VirtualObject
import dev.restate.sdk.annotation.Workflow
import dev.restate.serde.kotlinx.KotlinSerializationSerdeFactory
import java.net.URI
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.stream.Stream
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// --- Test service definitions (interfaces so the JDK proxy can be used, no bytebuddy needed) ---

@Service
@Name("Greeter")
private interface Greeter {
  @Handler suspend fun greet(name: String): String
}

@VirtualObject
@Name("Counter")
private interface Counter {
  @Exclusive suspend fun add(value: Int): Int
}

@Workflow
@Name("SignupWorkflow")
private interface SignupWorkflow {
  @Workflow suspend fun run(email: String): String
}

/**
 * A [Client] test double built on top of [BaseClient] (so all the handle factory methods come for
 * free). It records the last [Request] that the API produced and returns canned responses without
 * hitting the network.
 */
private class RecordingClient :
    BaseClient(URI.create("http://localhost:8080"), KotlinSerializationSerdeFactory(), null) {

  var lastRequest: Request<*, *>? = null
  var lastDelay: Duration? = null
  var cannedResponse: Any? = null

  override fun <Req, Res> callAsync(request: Request<Req, Res>): CompletableFuture<Response<Res>> {
    lastRequest = request
    @Suppress("UNCHECKED_CAST")
    return CompletableFuture.completedFuture(Response(200, NoHeaders, cannedResponse as Res))
  }

  override fun <Req, Res> sendAsync(
      request: Request<Req, Res>,
      delay: Duration?,
  ): CompletableFuture<SendResponse<Res>> {
    lastRequest = request
    lastDelay = delay
    val handle =
        object : Client.InvocationHandle<Res> {
          override fun invocationId(): String = "inv-123"

          override fun attachAsync(options: RequestOptions): CompletableFuture<Response<Res>> {
            @Suppress("UNCHECKED_CAST")
            return CompletableFuture.completedFuture(
                Response(200, NoHeaders, cannedResponse as Res)
            )
          }

          override fun getOutputAsync(
              options: RequestOptions
          ): CompletableFuture<Response<Output<Res>>> = throw UnsupportedOperationException()
        }
    return CompletableFuture.completedFuture(
        SendResponse(200, NoHeaders, SendResponse.SendStatus.ACCEPTED, handle)
    )
  }

  // Never reached: callAsync/sendAsync are overridden to short-circuit the network.
  override fun <Res> doPostRequest(
      target: URI,
      headers: Stream<Map.Entry<String, String>>,
      payload: Slice,
      responseMapper: ResponseMapper<Res>,
  ): CompletableFuture<Res> = throw UnsupportedOperationException()

  override fun <Res> doGetRequest(
      target: URI,
      headers: Stream<Map.Entry<String, String>>,
      responseMapper: ResponseMapper<Res>,
  ): CompletableFuture<Res> = throw UnsupportedOperationException()

  private object NoHeaders : ResponseHead.Headers {
    override fun get(key: String): String? = null

    override fun keys(): Set<String> = emptySet()

    override fun toLowercaseMap(): Map<String, String> = emptyMap()
  }
}

class ScopedClientTest {

  @Test
  fun `scope returns the platform ScopedClient, not a Kotlin-specific type`() {
    // Guards against the regression where a Kotlin Client.scope extension shadowed the Java member.
    val client = RecordingClient()
    assertThat(client.scope("tenant-1")).isInstanceOf(ScopedClient::class.java)
  }

  @Test
  fun `scoped service proxy routes the call within the scope`() = runTest {
    val client = RecordingClient()
    client.cannedResponse = "Hello Alice"

    val response = client.scope("tenant-1").service<Greeter>().greet("Alice")

    assertThat(response).isEqualTo("Hello Alice")
    val request = client.lastRequest!!
    assertThat(request.target.scope).isEqualTo("tenant-1")
    assertThat(request.target.service).isEqualTo("Greeter")
    assertThat(request.target.handler).isEqualTo("greet")
    assertThat(request.target.key).isNull()
    assertThat(request.request).isEqualTo("Alice")
  }

  @Test
  fun `scoped virtualObject proxy carries scope and key`() = runTest {
    val client = RecordingClient()
    client.cannedResponse = 42

    val response = client.scope("tenant-1").virtualObject<Counter>("my-key").add(1)

    assertThat(response).isEqualTo(42)
    val request = client.lastRequest!!
    assertThat(request.target.scope).isEqualTo("tenant-1")
    assertThat(request.target.service).isEqualTo("Counter")
    assertThat(request.target.key).isEqualTo("my-key")
    assertThat(request.target.handler).isEqualTo("add")
    assertThat(request.request).isEqualTo(1)
  }

  @Test
  fun `scoped toService builder forwards idempotencyKey and limitKey`() = runTest {
    val client = RecordingClient()
    client.cannedResponse = "Hi Bob"

    val response =
        client
            .scope("tenant-1")
            .toService<Greeter>()
            .request { greet("Bob") }
            .options {
              idempotencyKey = "idem-1"
              limitKey = "limit-1"
            }
            .call()

    assertThat(response.response()).isEqualTo("Hi Bob")
    val request = client.lastRequest!!
    assertThat(request.target.scope).isEqualTo("tenant-1")
    assertThat(request.target.service).isEqualTo("Greeter")
    assertThat(request.request).isEqualTo("Bob")
    assertThat(request.idempotencyKey).isEqualTo("idem-1")
    // Regression guard: limitKey used to be silently dropped by the options builder.
    assertThat(request.limitKey).isEqualTo("limit-1")
  }

  @Test
  fun `toService builder forwards limitKey even without a scope`() = runTest {
    val client = RecordingClient()
    client.cannedResponse = "Hi Carol"

    client.toService<Greeter>().request { greet("Carol") }.options { limitKey = "limit-2" }.call()

    val request = client.lastRequest!!
    assertThat(request.target.scope).isNull()
    assertThat(request.limitKey).isEqualTo("limit-2")
  }

  @Test
  fun `scoped toWorkflow builder submits within the scope`() = runTest {
    val client = RecordingClient()
    client.cannedResponse = "queued"

    val sendResponse =
        client
            .scope("tenant-1")
            .toWorkflow<SignupWorkflow>("wf-1")
            .request { run("a@b.com") }
            .send()

    assertThat(sendResponse.invocationId()).isEqualTo("inv-123")
    val request = client.lastRequest!!
    assertThat(request.target.scope).isEqualTo("tenant-1")
    assertThat(request.target.service).isEqualTo("SignupWorkflow")
    assertThat(request.target.key).isEqualTo("wf-1")
    assertThat(request.target.handler).isEqualTo("run")
    assertThat(request.request).isEqualTo("a@b.com")
  }
}
