// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.client;

import dev.restate.common.CallRequest;
import dev.restate.common.Output;
import dev.restate.common.SendRequest;
import dev.restate.common.Target;
import dev.restate.serde.Serde;
import dev.restate.serde.SerdeFactory;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public interface Client {

  <Req, Res> CompletableFuture<Res> callAsync(CallRequest<Req, Res> request);

  default <Req, Res> CompletableFuture<Res> callAsync(CallRequest.Builder<Req, Res> request) {
    return callAsync(request.build());
  }

  default <Req, Res> Res call(CallRequest<Req, Res> request)
      throws IngressException {
    try {
      return callAsync(request).join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw new RuntimeException(e.getCause());
    }
  }

  default <Req, Res> Res call(CallRequest.Builder<Req, Res> request)
          throws IngressException {
    return call(request.build());
  }

  <Req> CompletableFuture<SendResponse> sendAsync(
          SendRequest<Req> request);

  default <Req> CompletableFuture<SendResponse> sendAsync(
          SendRequest.Builder<Req> request) {
    return sendAsync(request.build());
  }

  default <Req> SendResponse send(SendRequest<Req> request)
      throws IngressException {
    try {
      return sendAsync(request).join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw new RuntimeException(e.getCause());
    }
  }

  default <Req> SendResponse send(SendRequest.Builder<Req> request)
      throws IngressException {
    return send(request.build());
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
    /** Same as {@link #resolve(Serde, Object)} but async with options. */
    <T> CompletableFuture<Void> resolveAsync(
        Serde<T> serde, @NonNull T payload, RequestOptions options);

    /** Same as {@link #resolve(Serde, Object)} but async. */
    default <T> CompletableFuture<Void> resolveAsync(Serde<T> serde, @NonNull T payload) {
      return resolveAsync(serde, payload, RequestOptions.DEFAULT);
    }

    /** Same as {@link #resolve(Serde, Object)} with options. */
    default <T> void resolve(Serde<T> serde, @NonNull T payload, RequestOptions options) {
      try {
        resolveAsync(serde, payload, options).join();
      } catch (CompletionException e) {
        if (e.getCause() instanceof RuntimeException) {
          throw (RuntimeException) e.getCause();
        }
        throw new RuntimeException(e.getCause());
      }
    }

    /**
     * Complete with success the Awakeable.
     *
     * @param serde used to serialize the Awakeable result payload.
     * @param payload the result payload. MUST NOT be null.
     */
    default <T> void resolve(Serde<T> serde, @NonNull T payload) {
      this.resolve(serde, payload, RequestOptions.DEFAULT);
    }

    /** Same as {@link #reject(String)} but async with options. */
    CompletableFuture<Void> rejectAsync(String reason, RequestOptions options);

    /** Same as {@link #reject(String)} but async. */
    default CompletableFuture<Void> rejectAsync(String reason) {
      return rejectAsync(reason, RequestOptions.DEFAULT);
    }

    /** Same as {@link #reject(String)} with options. */
    default void reject(String reason, RequestOptions options) {
      try {
        rejectAsync(reason, options).join();
      } catch (CompletionException e) {
        if (e.getCause() instanceof RuntimeException) {
          throw (RuntimeException) e.getCause();
        }
        throw new RuntimeException(e.getCause());
      }
    }

    /**
     * Complete with failure the Awakeable.
     *
     * @param reason the rejection reason. MUST NOT be null.
     */
    default void reject(String reason) {
      this.reject(reason, RequestOptions.DEFAULT);
    }
  }

  <Res> InvocationHandle<Res> invocationHandle(String invocationId, Serde<Res> resSerde);

  interface InvocationHandle<Res> {

    String invocationId();

    CompletableFuture<Res> attachAsync(RequestOptions options);

    default CompletableFuture<Res> attachAsync() {
      return attachAsync(RequestOptions.DEFAULT);
    }

    default Res attach(RequestOptions options) throws IngressException {
      try {
        return attachAsync(options).join();
      } catch (CompletionException e) {
        if (e.getCause() instanceof RuntimeException) {
          throw (RuntimeException) e.getCause();
        }
        throw new RuntimeException(e.getCause());
      }
    }

    default Res attach() throws IngressException {
      return attach(RequestOptions.DEFAULT);
    }

    CompletableFuture<Output<Res>> getOutputAsync(RequestOptions options);

    default CompletableFuture<Output<Res>> getOutputAsync() {
      return getOutputAsync(RequestOptions.DEFAULT);
    }

    default Output<Res> getOutput(RequestOptions options) throws IngressException {
      try {
        return getOutputAsync(options).join();
      } catch (CompletionException e) {
        if (e.getCause() instanceof RuntimeException) {
          throw (RuntimeException) e.getCause();
        }
        throw new RuntimeException(e.getCause());
      }
    }

    default Output<Res> getOutput() throws IngressException {
      return getOutput(RequestOptions.DEFAULT);
    }
  }

  <Res> IdempotentInvocationHandle<Res> idempotentInvocationHandle(
      Target target, String idempotencyKey, Serde<Res> resSerde);

  interface IdempotentInvocationHandle<Res> {

    CompletableFuture<Res> attachAsync(RequestOptions options);

    default CompletableFuture<Res> attachAsync() {
      return attachAsync(RequestOptions.DEFAULT);
    }

    default Res attach(RequestOptions options) throws IngressException {
      try {
        return attachAsync(options).join();
      } catch (CompletionException e) {
        if (e.getCause() instanceof RuntimeException) {
          throw (RuntimeException) e.getCause();
        }
        throw new RuntimeException(e.getCause());
      }
    }

    default Res attach() throws IngressException {
      return attach(RequestOptions.DEFAULT);
    }

    CompletableFuture<Output<Res>> getOutputAsync(RequestOptions options);

    default CompletableFuture<Output<Res>> getOutputAsync() {
      return getOutputAsync(RequestOptions.DEFAULT);
    }

    default Output<Res> getOutput(RequestOptions options) throws IngressException {
      try {
        return getOutputAsync(options).join();
      } catch (CompletionException e) {
        if (e.getCause() instanceof RuntimeException) {
          throw (RuntimeException) e.getCause();
        }
        throw new RuntimeException(e.getCause());
      }
    }

    default Output<Res> getOutput() throws IngressException {
      return getOutput(RequestOptions.DEFAULT);
    }
  }

  <Res> WorkflowHandle<Res> workflowHandle(
      String workflowName, String workflowId, Serde<Res> resSerde);

  interface WorkflowHandle<Res> {
    CompletableFuture<Res> attachAsync(RequestOptions options);

    default CompletableFuture<Res> attachAsync() {
      return attachAsync(RequestOptions.DEFAULT);
    }

    default Res attach(RequestOptions options) throws IngressException {
      try {
        return attachAsync(options).join();
      } catch (CompletionException e) {
        if (e.getCause() instanceof RuntimeException) {
          throw (RuntimeException) e.getCause();
        }
        throw new RuntimeException(e.getCause());
      }
    }

    default Res attach() throws IngressException {
      return attach(RequestOptions.DEFAULT);
    }

    CompletableFuture<Output<Res>> getOutputAsync(RequestOptions options);

    default CompletableFuture<Output<Res>> getOutputAsync() {
      return getOutputAsync(RequestOptions.DEFAULT);
    }

    default Output<Res> getOutput(RequestOptions options) throws IngressException {
      try {
        return getOutputAsync(options).join();
      } catch (CompletionException e) {
        if (e.getCause() instanceof RuntimeException) {
          throw (RuntimeException) e.getCause();
        }
        throw new RuntimeException(e.getCause());
      }
    }

    default Output<Res> getOutput() throws IngressException {
      return getOutput(RequestOptions.DEFAULT);
    }
  }

  static Client connect(String baseUri) {
    return connect(baseUri, SerdeFactory.NOOP, Collections.emptyMap());
  }

  static Client connect(String baseUri, Map<String, String> headers) {
    return connect(baseUri, SerdeFactory.NOOP, headers);
  }

  static Client connect(String baseUri, SerdeFactory serdeFactory) {
    return connect(baseUri, serdeFactory, Collections.emptyMap());
  }

  static Client connect(String baseUri, SerdeFactory serdeFactory, Map<String, String> headers) {
    // We load through reflections to avoid CNF exceptions in JVMs
    // where JDK's HttpClient is not available (see Android!)
    try {
      Class.forName("java.net.http.HttpClient");
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(
          "Cannot load the JdkClient, because the java.net.http.HttpClient is not available on this JVM. Please use another client",
          e);
    }

    try {
      return (Client)
          Class.forName("dev.restate.client.jdk.JdkClient")
              .getMethod("of", String.class, SerdeFactory.class, Map.class)
              .invoke(null, baseUri, serdeFactory, headers);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot instantiate the client", e);
    }
  }
}
