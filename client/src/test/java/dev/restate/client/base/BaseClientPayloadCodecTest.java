// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.client.base;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.restate.client.IngressException;
import dev.restate.client.Response;
import dev.restate.client.SendResponse;
import dev.restate.common.Output;
import dev.restate.common.Request;
import dev.restate.common.Slice;
import dev.restate.common.Target;
import dev.restate.serde.PayloadCodec;
import dev.restate.serde.Serde;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class BaseClientPayloadCodecTest {

  private static final Target TARGET = Target.service("MyService", "myHandler");

  /** Prepends {@code enc:} when encoding, and strips it when decoding. */
  private static final PayloadCodec PREFIX_CODEC =
      new PayloadCodec() {
        @Override
        public CompletableFuture<Slice> encode(Slice value) {
          return CompletableFuture.supplyAsync(() -> Slice.wrap("enc:" + asString(value)));
        }

        @Override
        public CompletableFuture<Slice> decode(Slice value) {
          return CompletableFuture.supplyAsync(
              () -> {
                String str = asString(value);
                if (!str.startsWith("enc:")) {
                  throw new IllegalStateException("Value is not encoded");
                }
                return Slice.wrap(str.substring("enc:".length()));
              });
        }
      };

  private static String asString(Slice slice) {
    return new String(slice.toByteArray(), StandardCharsets.UTF_8);
  }

  private record CannedResponse(int statusCode, String body) {}

  /** Records the request bodies, and replies with a canned response. */
  private static final class FakeClient extends BaseClient {
    private final CannedResponse response;
    private final List<String> requestBodies = new ArrayList<>();

    private FakeClient(PayloadCodec codec, CannedResponse response) {
      super(URI.create("http://localhost:8080"), null, codec, null);
      this.response = response;
    }

    @Override
    protected <Res> CompletableFuture<Res> doPostRequest(
        URI target,
        Stream<Map.Entry<String, String>> headers,
        Slice payload,
        ResponseMapper<Res> responseMapper) {
      requestBodies.add(asString(payload));
      return respond(responseMapper);
    }

    @Override
    protected <Res> CompletableFuture<Res> doGetRequest(
        URI target, Stream<Map.Entry<String, String>> headers, ResponseMapper<Res> responseMapper) {
      return respond(responseMapper);
    }

    private <Res> CompletableFuture<Res> respond(ResponseMapper<Res> responseMapper) {
      try {
        return CompletableFuture.completedFuture(
            responseMapper.mapResponse(
                response.statusCode(),
                EMPTY_HEADERS,
                response.body() == null ? null : Slice.wrap(response.body())));
      } catch (Throwable e) {
        return CompletableFuture.failedFuture(e);
      }
    }
  }

  private static final Response.Headers EMPTY_HEADERS =
      new Response.Headers() {
        @Override
        public String get(String key) {
          return null;
        }

        @Override
        public Set<String> keys() {
          return Set.of();
        }

        @Override
        public Map<String, String> toLowercaseMap() {
          return Map.of();
        }
      };

  private static Request<byte[], byte[]> request(String body) {
    return Request.of(TARGET, body.getBytes(StandardCharsets.UTF_8)).build();
  }

  @Test
  void callEncodesRequestAndDecodesResponse() {
    var client = new FakeClient(PREFIX_CODEC, new CannedResponse(200, "enc:world"));

    Response<byte[]> response = client.callAsync(request("hello")).join();

    assertThat(client.requestBodies).containsExactly("enc:hello");
    assertThat(new String(response.response(), StandardCharsets.UTF_8)).isEqualTo("world");
  }

  @Test
  void withoutCodecBodiesArePassedThrough() {
    var client = new FakeClient(null, new CannedResponse(200, "enc:world"));

    Response<byte[]> response = client.callAsync(request("hello")).join();

    assertThat(client.requestBodies).containsExactly("hello");
    assertThat(new String(response.response(), StandardCharsets.UTF_8)).isEqualTo("enc:world");
  }

  @Test
  void sendEncodesRequestButNotTheIngressResponse() {
    var client =
        new FakeClient(
            PREFIX_CODEC,
            new CannedResponse(200, "{\"invocationId\":\"inv_123\",\"status\":\"Accepted\"}"));

    SendResponse<byte[]> response = client.sendAsync(request("hello")).join();

    assertThat(client.requestBodies).containsExactly("enc:hello");
    assertThat(response.invocationId()).isEqualTo("inv_123");
  }

  @Test
  void awakeableResolveIsEncodedRejectIsNot() {
    var client = new FakeClient(PREFIX_CODEC, new CannedResponse(202, null));

    client.awakeableHandle("awk_1").resolveAsync(Serde.RAW, "hello".getBytes()).join();
    client.awakeableHandle("awk_1").rejectAsync("my failure").join();

    assertThat(client.requestBodies).containsExactly("enc:hello", "my failure");
  }

  @Test
  void attachAndGetOutputDecodeResponse() {
    var client = new FakeClient(PREFIX_CODEC, new CannedResponse(200, "enc:world"));
    var handle = client.invocationHandle("inv_123", Serde.RAW);

    assertThat(new String(handle.attachAsync().join().response(), StandardCharsets.UTF_8))
        .isEqualTo("world");
    Output<byte[]> output = handle.getOutputAsync().join().response();
    assertThat(output.isReady()).isTrue();
    assertThat(new String(output.getValue(), StandardCharsets.UTF_8)).isEqualTo("world");
  }

  @Test
  void getOutputNotReadyIsNotDecoded() {
    var client = new FakeClient(PREFIX_CODEC, new CannedResponse(470, null));

    Output<byte[]> output =
        client.invocationHandle("inv_123", Serde.RAW).getOutputAsync().join().response();

    assertThat(output.isReady()).isFalse();
  }

  @Test
  void decodeFailureIsReportedAsIngressException() {
    var client = new FakeClient(PREFIX_CODEC, new CannedResponse(200, "world"));

    assertThatThrownBy(() -> client.callAsync(request("hello")).join())
        .isInstanceOf(CompletionException.class)
        .cause()
        .isInstanceOf(IngressException.class)
        .hasMessageContaining("Cannot deserialize the response")
        .rootCause()
        .hasMessage("Value is not encoded");
  }
}
