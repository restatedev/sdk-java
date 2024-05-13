// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.client;

import dev.restate.sdk.common.Serde;
import dev.restate.sdk.common.Target;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public interface IngressClient {

  <Req, Res> CompletableFuture<Res> callAsync(
      Target target, Serde<Req> reqSerde, Serde<Res> resSerde, Req req, CallRequestOptions options);

  default <Req, Res> CompletableFuture<Res> callAsync(
      Target target, Serde<Req> reqSerde, Serde<Res> resSerde, Req req) {
    return callAsync(target, reqSerde, resSerde, req, CallRequestOptions.DEFAULT);
  }

  default <Req, Res> Res call(
      Target target, Serde<Req> reqSerde, Serde<Res> resSerde, Req req, CallRequestOptions options)
      throws IngressException {
    try {
      return callAsync(target, reqSerde, resSerde, req, options).join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw new RuntimeException(e.getCause());
    }
  }

  default <Req, Res> Res call(Target target, Serde<Req> reqSerde, Serde<Res> resSerde, Req req)
      throws IngressException {
    return call(target, reqSerde, resSerde, req, CallRequestOptions.DEFAULT);
  }

  <Req> CompletableFuture<String> sendAsync(
      Target target,
      Serde<Req> reqSerde,
      Req req,
      @Nullable Duration delay,
      CallRequestOptions options);

  default <Req> CompletableFuture<String> sendAsync(
      Target target, Serde<Req> reqSerde, Req req, @Nullable Duration delay) {
    return sendAsync(target, reqSerde, req, delay, CallRequestOptions.DEFAULT);
  }

  default <Req> CompletableFuture<String> sendAsync(Target target, Serde<Req> reqSerde, Req req) {
    return sendAsync(target, reqSerde, req, null, CallRequestOptions.DEFAULT);
  }

  default <Req> String send(
      Target target,
      Serde<Req> reqSerde,
      Req req,
      @Nullable Duration delay,
      CallRequestOptions options)
      throws IngressException {
    try {
      return sendAsync(target, reqSerde, req, delay, options).join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw new RuntimeException(e.getCause());
    }
  }

  default <Req> String send(Target target, Serde<Req> reqSerde, Req req, @Nullable Duration delay)
      throws IngressException {
    return send(target, reqSerde, req, delay, CallRequestOptions.DEFAULT);
  }

  default <Req> String send(Target target, Serde<Req> reqSerde, Req req) throws IngressException {
    return send(target, reqSerde, req, null, CallRequestOptions.DEFAULT);
  }

  /**
   * Create a new {@link AwakeableHandle} for the provided identifier. You can use it to {@link
   * AwakeableHandle#resolve(Serde, Object)} or {@link AwakeableHandle#reject(String)} an Awakeable
   * from the ingress.
   */
  AwakeableHandle awakeableHandle(String id);

  /**
   * This class represents a handle to an Awakeable. It can be used to complete awakeables from the
   * ingress
   */
  interface AwakeableHandle {
    /** Same as {@link #resolve(Serde, Object)} but async. */
    <T> CompletableFuture<Void> resolveAsync(Serde<T> serde, @NonNull T payload);

    /**
     * Complete with success the Awakeable.
     *
     * @param serde used to serialize the Awakeable result payload.
     * @param payload the result payload. MUST NOT be null.
     */
    default <T> void resolve(Serde<T> serde, @NonNull T payload) {
      try {
        resolveAsync(serde, payload).join();
      } catch (CompletionException e) {
        if (e.getCause() instanceof RuntimeException) {
          throw (RuntimeException) e.getCause();
        }
        throw new RuntimeException(e.getCause());
      }
    }

    /** Same as {@link #reject(String)} but async. */
    CompletableFuture<Void> rejectAsync(String reason);

    /**
     * Complete with failure the Awakeable.
     *
     * @param reason the rejection reason. MUST NOT be null.
     */
    default void reject(String reason) {
      try {
        rejectAsync(reason).join();
      } catch (CompletionException e) {
        if (e.getCause() instanceof RuntimeException) {
          throw (RuntimeException) e.getCause();
        }
        throw new RuntimeException(e.getCause());
      }
    }
  }

  static IngressClient defaultClient(String baseUri) {
    return defaultClient(baseUri, Collections.emptyMap());
  }

  static IngressClient defaultClient(String baseUri, Map<String, String> headers) {
    return new DefaultIngressClient(HttpClient.newHttpClient(), baseUri, headers);
  }
}
