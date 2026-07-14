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

@Service
@Name("Greeter")
private interface Greeter {
  @Handler suspend fun greet(name: String): String
}

@VirtualObject
@Name("Counter")
private interface Counter {
  @Exclusive suspend fun add(value: Int): Int

  @Exclusive suspend fun label(name: String): String
}

@Workflow
@Name("SignupWorkflow")
private interface SignupWorkflow {
  @Workflow suspend fun run(email: String): String
}

private object NoHeaders : ResponseHead.Headers {
  override fun get(key: String): String? = null

  override fun keys(): Set<String> = emptySet()

  override fun toLowercaseMap(): Map<String, String> = emptyMap()
}

private class RequestRecordingClient(private val mockResponse: Any? = null) :
    BaseClient(URI.create("http://localhost:8080"), KotlinSerializationSerdeFactory(), null) {

  var lastRequest: Request<*, *>? = null
  var lastDelay: Duration? = null

  override fun <Req, Res> callAsync(request: Request<Req, Res>): CompletableFuture<Response<Res>> {
    lastRequest = request
    @Suppress("UNCHECKED_CAST")
    return CompletableFuture.completedFuture(Response(200, NoHeaders, mockResponse as Res))
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
            return CompletableFuture.completedFuture(Response(200, NoHeaders, mockResponse as Res))
          }

          override fun getOutputAsync(
              options: RequestOptions
          ): CompletableFuture<Response<Output<Res>>> = throw UnsupportedOperationException()
        }
    return CompletableFuture.completedFuture(
        SendResponse(200, NoHeaders, SendResponse.SendStatus.ACCEPTED, handle)
    )
  }

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
}

private class UriRecordingClient :
    BaseClient(URI.create("http://localhost:8080"), KotlinSerializationSerdeFactory(), null) {

  var lastUri: URI? = null

  override fun <Res> doPostRequest(
      target: URI,
      headers: Stream<Map.Entry<String, String>>,
      payload: Slice,
      responseMapper: ResponseMapper<Res>,
  ): CompletableFuture<Res> {
    lastUri = target
    val path = target.path
    val isSend = path.endsWith("/send") || path.contains("/send/")
    val body =
        if (isSend) Slice.wrap("""{"invocationId":"inv-123","status":"Accepted"}""")
        else Slice.wrap("\"ok\"")
    return CompletableFuture.completedFuture(responseMapper.mapResponse(200, NoHeaders, body))
  }

  override fun <Res> doGetRequest(
      target: URI,
      headers: Stream<Map.Entry<String, String>>,
      responseMapper: ResponseMapper<Res>,
  ): CompletableFuture<Res> = throw UnsupportedOperationException()
}

class ScopedClientTest {

  @Test
  fun `scope returns the platform ScopedClient, not a Kotlin-specific type`() {
    // Guards against the regression where a Kotlin Client.scope extension shadowed the Java member.
    val client = RequestRecordingClient()
    assertThat(client.scope("tenant-1")).isInstanceOf(ScopedClient::class.java)
  }

  @Test
  fun `scoped service proxy routes the call within the scope`() = runTest {
    val client = RequestRecordingClient("Hello Alice")

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
    val client = RequestRecordingClient(42)

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
    val client = RequestRecordingClient("Hi Bob")

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
    val client = RequestRecordingClient("Hi Carol")

    client.toService<Greeter>().request { greet("Carol") }.options { limitKey = "limit-2" }.call()

    val request = client.lastRequest!!
    assertThat(request.target.scope).isNull()
    assertThat(request.limitKey).isEqualTo("limit-2")
  }

  @Test
  fun `scoped toWorkflow builder submits within the scope`() = runTest {
    val client = RequestRecordingClient("queued")

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

  @Test
  fun `scoped call encodes scope in the path`() = runTest {
    val client = UriRecordingClient()

    client.scope("tenant-1").toService<Greeter>().request { greet("A") }.call()

    assertThat(client.lastUri!!.path).isEqualTo("/restate/scope/tenant-1/call/Greeter/greet")
    assertThat(client.lastUri!!.query).isNull()
  }

  @Test
  fun `scoped keyed send encodes scope, key and send verb in the path`() = runTest {
    val client = UriRecordingClient()

    client.scope("tenant-1").toVirtualObject<Counter>("my-key").request { label("x") }.send()

    assertThat(client.lastUri!!.path).isEqualTo("/restate/scope/tenant-1/send/Counter/my-key/label")
  }

  @Test
  fun `scoped send with limit key uses the limit-key query param`() = runTest {
    val client = UriRecordingClient()

    client
        .scope("tenant-1")
        .toService<Greeter>()
        .request { greet("A") }
        .options { limitKey = "api-key/user42" }
        .send()

    assertThat(client.lastUri!!.path).isEqualTo("/restate/scope/tenant-1/send/Greeter/greet")
    // The runtime reads "limit-key" (kebab-case), not "limitKey".
    assertThat(client.lastUri!!.query).isEqualTo("limit-key=api-key/user42")
  }

  @Test
  fun `unscoped call keeps the unversioned path`() = runTest {
    val client = UriRecordingClient()

    client.toService<Greeter>().request { greet("A") }.call()

    assertThat(client.lastUri!!.path).isEqualTo("/Greeter/greet")
    assertThat(client.lastUri!!.query).isNull()
  }

  @Test
  fun `unscoped send keeps the unversioned path`() = runTest {
    val client = UriRecordingClient()

    client.toService<Greeter>().request { greet("A") }.send()

    assertThat(client.lastUri!!.path).isEqualTo("/Greeter/greet/send")
  }
}
