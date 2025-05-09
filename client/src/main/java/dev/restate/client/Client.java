// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.client;

import dev.restate.common.Output;
import dev.restate.common.Request;
import dev.restate.common.Target;
import dev.restate.common.WorkflowRequest;
import dev.restate.serde.SerdeFactory;
import dev.restate.serde.TypeTag;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public interface Client {

  <Req, Res> CompletableFuture<Response<Res>> callAsync(Request<Req, Res> request);

  default <Req, Res> Response<Res> call(Request<Req, Res> request) throws IngressException {
    try {
      return callAsync(request).join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw new RuntimeException(e.getCause());
    }
  }

  default <Req, Res> CompletableFuture<SendResponse<Res>> sendAsync(Request<Req, Res> request) {
    return sendAsync(request, null);
  }

  default <Req, Res> SendResponse<Res> send(Request<Req, Res> request) throws IngressException {
    return send(request, null);
  }

  <Req, Res> CompletableFuture<SendResponse<Res>> sendAsync(
      Request<Req, Res> request, @Nullable Duration delay);

  default <Req, Res> SendResponse<Res> send(Request<Req, Res> request, @Nullable Duration delay)
      throws IngressException {
    try {
      return sendAsync(request, delay).join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw new RuntimeException(e.getCause());
    }
  }

  default <Req, Res> CompletableFuture<SendResponse<Res>> submitAsync(
      WorkflowRequest<Req, Res> request) {
    return submitAsync(request, null);
  }

  default <Req, Res> SendResponse<Res> submit(WorkflowRequest<Req, Res> request)
      throws IngressException {
    return submit(request, null);
  }

  default <Req, Res> CompletableFuture<SendResponse<Res>> submitAsync(
      WorkflowRequest<Req, Res> request, @Nullable Duration delay) {
    return sendAsync(request, delay);
  }

  default <Req, Res> SendResponse<Res> submit(
      WorkflowRequest<Req, Res> request, @Nullable Duration delay) throws IngressException {
    try {
      return submitAsync(request, delay).join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw new RuntimeException(e.getCause());
    }
  }

  /**
   * Create a new {@link AwakeableHandle} for the provided identifier. You can use it to {@link
   * AwakeableHandle#resolve(TypeTag, Object)} or {@link AwakeableHandle#reject(String)} an
   * Awakeable from the ingress.
   */
  AwakeableHandle awakeableHandle(String id);

  /**
   * This class represents a handle to an Awakeable. It can be used to complete awakeables from the
   * ingress
   */
  interface AwakeableHandle {
    /** Same as {@link #resolve(TypeTag, Object)} but async with options. */
    <T> CompletableFuture<Response<Void>> resolveAsync(
        TypeTag<T> serde, @NonNull T payload, RequestOptions options);

    /** Same as {@link #resolve(TypeTag, Object)} but async. */
    default <T> CompletableFuture<Response<Void>> resolveAsync(
        TypeTag<T> serde, @NonNull T payload) {
      return resolveAsync(serde, payload, RequestOptions.DEFAULT);
    }

    /** Same as {@link #resolve(TypeTag, Object)} with options. */
    default <T> Response<Void> resolve(
        TypeTag<T> serde, @NonNull T payload, RequestOptions options) {
      try {
        return resolveAsync(serde, payload, options).join();
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
     * @param clazz used to serialize the Awakeable result payload.
     * @param payload the result payload. MUST NOT be null.
     */
    default <T> Response<Void> resolve(Class<T> clazz, @NonNull T payload) {
      return this.resolve(TypeTag.of(clazz), payload, RequestOptions.DEFAULT);
    }

    /** Same as {@link #resolve(Class, Object)} but async with options. */
    default <T> CompletableFuture<Response<Void>> resolveAsync(
        Class<T> clazz, @NonNull T payload, RequestOptions options) {
      return this.resolveAsync(TypeTag.of(clazz), payload, options);
    }

    /** Same as {@link #resolve(TypeTag, Object)} but async. */
    default <T> CompletableFuture<Response<Void>> resolveAsync(Class<T> clazz, @NonNull T payload) {
      return resolveAsync(TypeTag.of(clazz), payload, RequestOptions.DEFAULT);
    }

    /** Same as {@link #resolve(TypeTag, Object)} with options. */
    default <T> Response<Void> resolve(Class<T> clazz, @NonNull T payload, RequestOptions options) {
      return resolve(TypeTag.of(clazz), payload, options);
    }

    /**
     * Complete with success the Awakeable.
     *
     * @param serde used to serialize the Awakeable result payload.
     * @param payload the result payload. MUST NOT be null.
     */
    default <T> Response<Void> resolve(TypeTag<T> serde, @NonNull T payload) {
      return this.resolve(serde, payload, RequestOptions.DEFAULT);
    }

    /** Same as {@link #reject(String)} but async with options. */
    CompletableFuture<Response<Void>> rejectAsync(String reason, RequestOptions options);

    /** Same as {@link #reject(String)} but async. */
    default CompletableFuture<Response<Void>> rejectAsync(String reason) {
      return rejectAsync(reason, RequestOptions.DEFAULT);
    }

    /** Same as {@link #reject(String)} with options. */
    default Response<Void> reject(String reason, RequestOptions options) {
      try {
        return rejectAsync(reason, options).join();
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
    default Response<Void> reject(String reason) {
      return this.reject(reason, RequestOptions.DEFAULT);
    }
  }

  <Res> InvocationHandle<Res> invocationHandle(String invocationId, TypeTag<Res> resSerde);

  default <Res> InvocationHandle<Res> invocationHandle(String invocationId, Class<Res> clazz) {
    return invocationHandle(invocationId, TypeTag.of(clazz));
  }

  interface InvocationHandle<Res> {

    String invocationId();

    CompletableFuture<Response<Res>> attachAsync(RequestOptions options);

    default CompletableFuture<Response<Res>> attachAsync() {
      return attachAsync(RequestOptions.DEFAULT);
    }

    default Response<Res> attach(RequestOptions options) throws IngressException {
      try {
        return attachAsync(options).join();
      } catch (CompletionException e) {
        if (e.getCause() instanceof RuntimeException) {
          throw (RuntimeException) e.getCause();
        }
        throw new RuntimeException(e.getCause());
      }
    }

    default Response<Res> attach() throws IngressException {
      return attach(RequestOptions.DEFAULT);
    }

    CompletableFuture<Response<Output<Res>>> getOutputAsync(RequestOptions options);

    default CompletableFuture<Response<Output<Res>>> getOutputAsync() {
      return getOutputAsync(RequestOptions.DEFAULT);
    }

    default Response<Output<Res>> getOutput(RequestOptions options) throws IngressException {
      try {
        return getOutputAsync(options).join();
      } catch (CompletionException e) {
        if (e.getCause() instanceof RuntimeException) {
          throw (RuntimeException) e.getCause();
        }
        throw new RuntimeException(e.getCause());
      }
    }

    default Response<Output<Res>> getOutput() throws IngressException {
      return getOutput(RequestOptions.DEFAULT);
    }
  }

  <Res> IdempotentInvocationHandle<Res> idempotentInvocationHandle(
      Target target, String idempotencyKey, TypeTag<Res> resSerde);

  default <Res> IdempotentInvocationHandle<Res> idempotentInvocationHandle(
      Target target, String idempotencyKey, Class<Res> clazz) {
    return idempotentInvocationHandle(target, idempotencyKey, TypeTag.of(clazz));
  }

  interface IdempotentInvocationHandle<Res> {

    CompletableFuture<Response<Res>> attachAsync(RequestOptions options);

    default CompletableFuture<Response<Res>> attachAsync() {
      return attachAsync(RequestOptions.DEFAULT);
    }

    default Response<Res> attach(RequestOptions options) throws IngressException {
      try {
        return attachAsync(options).join();
      } catch (CompletionException e) {
        if (e.getCause() instanceof RuntimeException) {
          throw (RuntimeException) e.getCause();
        }
        throw new RuntimeException(e.getCause());
      }
    }

    default Response<Res> attach() throws IngressException {
      return attach(RequestOptions.DEFAULT);
    }

    CompletableFuture<Response<Output<Res>>> getOutputAsync(RequestOptions options);

    default CompletableFuture<Response<Output<Res>>> getOutputAsync() {
      return getOutputAsync(RequestOptions.DEFAULT);
    }

    default Response<Output<Res>> getOutput(RequestOptions options) throws IngressException {
      try {
        return getOutputAsync(options).join();
      } catch (CompletionException e) {
        if (e.getCause() instanceof RuntimeException) {
          throw (RuntimeException) e.getCause();
        }
        throw new RuntimeException(e.getCause());
      }
    }

    default Response<Output<Res>> getOutput() throws IngressException {
      return getOutput(RequestOptions.DEFAULT);
    }
  }

  <Res> WorkflowHandle<Res> workflowHandle(
      String workflowName, String workflowId, TypeTag<Res> resSerde);

  default <Res> WorkflowHandle<Res> workflowHandle(
      String workflowName, String workflowId, Class<Res> clazz) {
    return workflowHandle(workflowName, workflowId, TypeTag.of(clazz));
  }

  interface WorkflowHandle<Res> {
    CompletableFuture<Response<Res>> attachAsync(RequestOptions options);

    default CompletableFuture<Response<Res>> attachAsync() {
      return attachAsync(RequestOptions.DEFAULT);
    }

    default Response<Res> attach(RequestOptions options) throws IngressException {
      try {
        return attachAsync(options).join();
      } catch (CompletionException e) {
        if (e.getCause() instanceof RuntimeException) {
          throw (RuntimeException) e.getCause();
        }
        throw new RuntimeException(e.getCause());
      }
    }

    default Response<Res> attach() throws IngressException {
      return attach(RequestOptions.DEFAULT);
    }

    CompletableFuture<Response<Output<Res>>> getOutputAsync(RequestOptions options);

    default CompletableFuture<Response<Output<Res>>> getOutputAsync() {
      return getOutputAsync(RequestOptions.DEFAULT);
    }

    default Response<Output<Res>> getOutput(RequestOptions options) throws IngressException {
      try {
        return getOutputAsync(options).join();
      } catch (CompletionException e) {
        if (e.getCause() instanceof RuntimeException) {
          throw (RuntimeException) e.getCause();
        }
        throw new RuntimeException(e.getCause());
      }
    }

    default Response<Output<Res>> getOutput() throws IngressException {
      return getOutput(RequestOptions.DEFAULT);
    }
  }

  /**
   * Create a default JDK client.
   *
   * @param baseUri uri to connect to.
   */
  static Client connect(String baseUri) {
    return connect(baseUri, null, null);
  }

  /**
   * Create a default JDK client.
   *
   * @param baseUri uri to connect to
   * @param options default options to use in all the requests.
   */
  static Client connect(String baseUri, RequestOptions options) {
    return connect(baseUri, null, options);
  }

  /**
   * Create a default JDK client.
   *
   * @param baseUri uri to connect to
   * @param serdeFactory Serde factory to use. If you're just wrapping this client in a
   *     code-generated client, you don't need to provide this parameter.
   */
  static Client connect(String baseUri, SerdeFactory serdeFactory) {
    return connect(baseUri, serdeFactory, RequestOptions.DEFAULT);
  }

  /**
   * Create a default JDK client.
   *
   * @param baseUri uri to connect to
   * @param serdeFactory Serde factory to use. If you're just wrapping this client in a
   *     code-generated client, you don't need to provide this parameter.
   * @param options default options to use in all the requests.
   */
  static Client connect(String baseUri, SerdeFactory serdeFactory, RequestOptions options) {
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
              .getMethod("of", String.class, SerdeFactory.class, RequestOptions.class)
              .invoke(null, baseUri, serdeFactory, options);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot instantiate the client", e);
    }
  }
}
