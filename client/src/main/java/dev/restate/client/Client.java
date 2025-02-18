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
import dev.restate.serde.Serde;
import dev.restate.serde.SerdeFactory;
import dev.restate.serde.TypeTag;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.jspecify.annotations.NonNull;

public interface Client {

  <Req, Res> CompletableFuture<ClientResponse<Res>> callAsync(Request<Req, Res> request);

  default <Req, Res> CompletableFuture<ClientResponse<Res>> callAsync(
      Request.Builder<Req, Res> request) {
    return callAsync(request.build());
  }

  default <Req, Res> ClientResponse<Res> call(Request<Req, Res> request) throws IngressException {
    try {
      return callAsync(request).join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw new RuntimeException(e.getCause());
    }
  }

  default <Req, Res> ClientResponse<Res> call(Request.Builder<Req, Res> request)
      throws IngressException {
    return call(request.build());
  }

  <Req, Res> CompletableFuture<ClientResponse<SendResponse<Res>>> sendAsync(
      Request<Req, Res> request);

  default <Req, Res> ClientResponse<SendResponse<Res>> send(Request<Req, Res> request)
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

  default <Req, Res> CompletableFuture<ClientResponse<SendResponse<Res>>> sendAsync(
      Request.Builder<Req, Res> request) {
    return sendAsync(request.build());
  }

  default <Req, Res> ClientResponse<SendResponse<Res>> send(Request.Builder<Req, Res> request)
      throws IngressException {
    return send(request.build());
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
    <T> CompletableFuture<ClientResponse<Void>> resolveAsync(
        TypeTag<T> serde, @NonNull T payload, ClientRequestOptions options);

    /** Same as {@link #resolve(TypeTag, Object)} but async. */
    default <T> CompletableFuture<ClientResponse<Void>> resolveAsync(
        TypeTag<T> serde, @NonNull T payload) {
      return resolveAsync(serde, payload, ClientRequestOptions.DEFAULT);
    }

    /** Same as {@link #resolve(TypeTag, Object)} with options. */
    default <T> ClientResponse<Void> resolve(
        TypeTag<T> serde, @NonNull T payload, ClientRequestOptions options) {
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
     * @param serde used to serialize the Awakeable result payload.
     * @param payload the result payload. MUST NOT be null.
     */
    default <T> ClientResponse<Void> resolve(TypeTag<T> serde, @NonNull T payload) {
      return this.resolve(serde, payload, ClientRequestOptions.DEFAULT);
    }

    /** Same as {@link #reject(String)} but async with options. */
    CompletableFuture<ClientResponse<Void>> rejectAsync(
        String reason, ClientRequestOptions options);

    /** Same as {@link #reject(String)} but async. */
    default CompletableFuture<ClientResponse<Void>> rejectAsync(String reason) {
      return rejectAsync(reason, ClientRequestOptions.DEFAULT);
    }

    /** Same as {@link #reject(String)} with options. */
    default ClientResponse<Void> reject(String reason, ClientRequestOptions options) {
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
    default ClientResponse<Void> reject(String reason) {
      return this.reject(reason, ClientRequestOptions.DEFAULT);
    }
  }

  <Res> InvocationHandle<Res> invocationHandle(String invocationId, TypeTag<Res> resSerde);

  interface InvocationHandle<Res> {

    String invocationId();

    CompletableFuture<ClientResponse<Res>> attachAsync(ClientRequestOptions options);

    default CompletableFuture<ClientResponse<Res>> attachAsync() {
      return attachAsync(ClientRequestOptions.DEFAULT);
    }

    default ClientResponse<Res> attach(ClientRequestOptions options) throws IngressException {
      try {
        return attachAsync(options).join();
      } catch (CompletionException e) {
        if (e.getCause() instanceof RuntimeException) {
          throw (RuntimeException) e.getCause();
        }
        throw new RuntimeException(e.getCause());
      }
    }

    default ClientResponse<Res> attach() throws IngressException {
      return attach(ClientRequestOptions.DEFAULT);
    }

    CompletableFuture<ClientResponse<Output<Res>>> getOutputAsync(ClientRequestOptions options);

    default CompletableFuture<ClientResponse<Output<Res>>> getOutputAsync() {
      return getOutputAsync(ClientRequestOptions.DEFAULT);
    }

    default ClientResponse<Output<Res>> getOutput(ClientRequestOptions options)
        throws IngressException {
      try {
        return getOutputAsync(options).join();
      } catch (CompletionException e) {
        if (e.getCause() instanceof RuntimeException) {
          throw (RuntimeException) e.getCause();
        }
        throw new RuntimeException(e.getCause());
      }
    }

    default ClientResponse<Output<Res>> getOutput() throws IngressException {
      return getOutput(ClientRequestOptions.DEFAULT);
    }
  }

  <Res> IdempotentInvocationHandle<Res> idempotentInvocationHandle(
      Target target, String idempotencyKey, TypeTag<Res> resSerde);

  interface IdempotentInvocationHandle<Res> {

    CompletableFuture<ClientResponse<Res>> attachAsync(ClientRequestOptions options);

    default CompletableFuture<ClientResponse<Res>> attachAsync() {
      return attachAsync(ClientRequestOptions.DEFAULT);
    }

    default ClientResponse<Res> attach(ClientRequestOptions options) throws IngressException {
      try {
        return attachAsync(options).join();
      } catch (CompletionException e) {
        if (e.getCause() instanceof RuntimeException) {
          throw (RuntimeException) e.getCause();
        }
        throw new RuntimeException(e.getCause());
      }
    }

    default ClientResponse<Res> attach() throws IngressException {
      return attach(ClientRequestOptions.DEFAULT);
    }

    CompletableFuture<ClientResponse<Output<Res>>> getOutputAsync(ClientRequestOptions options);

    default CompletableFuture<ClientResponse<Output<Res>>> getOutputAsync() {
      return getOutputAsync(ClientRequestOptions.DEFAULT);
    }

    default ClientResponse<Output<Res>> getOutput(ClientRequestOptions options)
        throws IngressException {
      try {
        return getOutputAsync(options).join();
      } catch (CompletionException e) {
        if (e.getCause() instanceof RuntimeException) {
          throw (RuntimeException) e.getCause();
        }
        throw new RuntimeException(e.getCause());
      }
    }

    default ClientResponse<Output<Res>> getOutput() throws IngressException {
      return getOutput(ClientRequestOptions.DEFAULT);
    }
  }

  <Res> WorkflowHandle<Res> workflowHandle(
      String workflowName, String workflowId, TypeTag<Res> resSerde);

  interface WorkflowHandle<Res> {
    CompletableFuture<ClientResponse<Res>> attachAsync(ClientRequestOptions options);

    default CompletableFuture<ClientResponse<Res>> attachAsync() {
      return attachAsync(ClientRequestOptions.DEFAULT);
    }

    default ClientResponse<Res> attach(ClientRequestOptions options) throws IngressException {
      try {
        return attachAsync(options).join();
      } catch (CompletionException e) {
        if (e.getCause() instanceof RuntimeException) {
          throw (RuntimeException) e.getCause();
        }
        throw new RuntimeException(e.getCause());
      }
    }

    default ClientResponse<Res> attach() throws IngressException {
      return attach(ClientRequestOptions.DEFAULT);
    }

    CompletableFuture<ClientResponse<Output<Res>>> getOutputAsync(ClientRequestOptions options);

    default CompletableFuture<ClientResponse<Output<Res>>> getOutputAsync() {
      return getOutputAsync(ClientRequestOptions.DEFAULT);
    }

    default ClientResponse<Output<Res>> getOutput(ClientRequestOptions options)
        throws IngressException {
      try {
        return getOutputAsync(options).join();
      } catch (CompletionException e) {
        if (e.getCause() instanceof RuntimeException) {
          throw (RuntimeException) e.getCause();
        }
        throw new RuntimeException(e.getCause());
      }
    }

    default ClientResponse<Output<Res>> getOutput() throws IngressException {
      return getOutput(ClientRequestOptions.DEFAULT);
    }
  }

  /**
   * Create a default JDK client.
   *
   * @param baseUri uri to connect to.
   */
  static Client connect(String baseUri) {
    return connect(baseUri, SerdeFactory.NOOP, ClientRequestOptions.DEFAULT);
  }

  /**
   * Create a default JDK client.
   *
   * @param baseUri uri to connect to
   * @param options default options to use in all the requests.
   */
  static Client connect(String baseUri, ClientRequestOptions options) {
    return connect(baseUri, SerdeFactory.NOOP, options);
  }

  /**
   * Create a default JDK client.
   *
   * @param baseUri uri to connect to
   * @param serdeFactory Serde factory to use. You must provide this when the provided {@link
   *     TypeTag} are not {@link Serde} instances. If you're just wrapping this client in a
   *     code-generated client, you don't need to provide this parameter.
   */
  static Client connect(String baseUri, SerdeFactory serdeFactory) {
    return connect(baseUri, serdeFactory, ClientRequestOptions.DEFAULT);
  }

  /**
   * Create a default JDK client.
   *
   * @param baseUri uri to connect to
   * @param serdeFactory Serde factory to use. You must provide this when the provided {@link
   *     TypeTag} are not {@link Serde} instances. If you're just wrapping this client in a
   *     code-generated client, you don't need to provide this parameter.
   * @param options default options to use in all the requests.
   */
  static Client connect(String baseUri, SerdeFactory serdeFactory, ClientRequestOptions options) {
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
              .getMethod("of", String.class, SerdeFactory.class, ClientRequestOptions.class)
              .invoke(null, baseUri, serdeFactory, options);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot instantiate the client", e);
    }
  }
}
