// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.client;

import static dev.restate.common.reflections.ReflectionUtils.mustHaveAnnotation;

import dev.restate.common.Output;
import dev.restate.common.Request;
import dev.restate.common.Target;
import dev.restate.common.WorkflowRequest;
import dev.restate.sdk.annotation.Service;
import dev.restate.sdk.annotation.VirtualObject;
import dev.restate.sdk.annotation.Workflow;
import dev.restate.serde.SerdeFactory;
import dev.restate.serde.TypeTag;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public interface Client {

  /**
   * Future version of {@link #call(Request)}
   *
   * @see #call(Request)
   */
  <Req, Res> CompletableFuture<Response<Res>> callAsync(Request<Req, Res> request);

  /** Call a service and wait for the response. */
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

  /**
   * Future version of {@link #send(Request)}
   *
   * @see #send(Request)
   */
  default <Req, Res> CompletableFuture<SendResponse<Res>> sendAsync(Request<Req, Res> request) {
    return sendAsync(request, null);
  }

  /** Send a request to a service without waiting for the response. */
  default <Req, Res> SendResponse<Res> send(Request<Req, Res> request) throws IngressException {
    return send(request, null);
  }

  /**
   * Future version of {@link #send(Request, Duration)}
   *
   * @see #send(Request, Duration)
   */
  <Req, Res> CompletableFuture<SendResponse<Res>> sendAsync(
      Request<Req, Res> request, @Nullable Duration delay);

  /**
   * Send a request to a service without waiting for the response, optionally providing an execution
   * delay to wait for.
   */
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

  /**
   * Future version of {@link #submit(WorkflowRequest)}
   *
   * @see #submit(WorkflowRequest)
   */
  default <Req, Res> CompletableFuture<SendResponse<Res>> submitAsync(
      WorkflowRequest<Req, Res> request) {
    return submitAsync(request, null);
  }

  /** Submit a workflow. */
  default <Req, Res> SendResponse<Res> submit(WorkflowRequest<Req, Res> request)
      throws IngressException {
    return submit(request, null);
  }

  /**
   * Future version of {@link #submit(WorkflowRequest, Duration)}
   *
   * @see #submit(WorkflowRequest, Duration)
   */
  default <Req, Res> CompletableFuture<SendResponse<Res>> submitAsync(
      WorkflowRequest<Req, Res> request, @Nullable Duration delay) {
    return sendAsync(request, delay);
  }

  /** Submit a workflow, optionally providing an execution delay to wait for. */
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

  /**
   * Create a new {@link InvocationHandle} for the provided invocation identifier.
   *
   * @param invocationId the invocation identifier
   * @param resTypeTag type tag used to deserialize the invocation result
   * @return the invocation handle
   */
  <Res> InvocationHandle<Res> invocationHandle(String invocationId, TypeTag<Res> resTypeTag);

  /**
   * Create a new {@link InvocationHandle} for the provided invocation identifier.
   *
   * @param invocationId the invocation identifier
   * @param clazz used to deserialize the invocation result
   * @return the invocation handle
   */
  default <Res> InvocationHandle<Res> invocationHandle(String invocationId, Class<Res> clazz) {
    return invocationHandle(invocationId, TypeTag.of(clazz));
  }

  interface InvocationHandle<Res> {

    /**
     * @return the invocation identifier
     */
    String invocationId();

    /**
     * Future version of {@link #attach()}, with options.
     *
     * @see #attach()
     */
    CompletableFuture<Response<Res>> attachAsync(RequestOptions options);

    /**
     * Future version of {@link #attach()}
     *
     * @see #attach()
     */
    default CompletableFuture<Response<Res>> attachAsync() {
      return attachAsync(RequestOptions.DEFAULT);
    }

    /**
     * Like {@link #attach()}, with request options.
     *
     * @see #attach()
     */
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

    /** Attach to a running invocation, waiting for its output. */
    default Response<Res> attach() throws IngressException {
      return attach(RequestOptions.DEFAULT);
    }

    /**
     * Future version of {@link #getOutput()}, with options.
     *
     * @see #getOutput()
     */
    CompletableFuture<Response<Output<Res>>> getOutputAsync(RequestOptions options);

    /**
     * Future version of {@link #getOutput()}
     *
     * @see #getOutput()
     */
    default CompletableFuture<Response<Output<Res>>> getOutputAsync() {
      return getOutputAsync(RequestOptions.DEFAULT);
    }

    /**
     * Like {@link #getOutput()}, with request options.
     *
     * @see #getOutput()
     */
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

    /** Get the output of an invocation. If running, {@link Output#isReady()} will be false. */
    default Response<Output<Res>> getOutput() throws IngressException {
      return getOutput(RequestOptions.DEFAULT);
    }
  }

  /**
   * Create a new {@link IdempotentInvocationHandle} for the provided target and idempotency key.
   *
   * @param target the target service/method
   * @param idempotencyKey the idempotency key
   * @param resTypeTag type tag used to deserialize the invocation result
   * @return the idempotent invocation handle
   */
  <Res> IdempotentInvocationHandle<Res> idempotentInvocationHandle(
      Target target, String idempotencyKey, TypeTag<Res> resTypeTag);

  /**
   * Create a new {@link IdempotentInvocationHandle} for the provided target and idempotency key.
   *
   * @param target the target service/method
   * @param idempotencyKey the idempotency key
   * @param clazz used to deserialize the invocation result
   * @return the idempotent invocation handle
   */
  default <Res> IdempotentInvocationHandle<Res> idempotentInvocationHandle(
      Target target, String idempotencyKey, Class<Res> clazz) {
    return idempotentInvocationHandle(target, idempotencyKey, TypeTag.of(clazz));
  }

  interface IdempotentInvocationHandle<Res> {

    /**
     * Future version of {@link #attach()}, with options.
     *
     * @see #attach()
     */
    CompletableFuture<Response<Res>> attachAsync(RequestOptions options);

    /**
     * Future version of {@link #attach()}
     *
     * @see #attach()
     */
    default CompletableFuture<Response<Res>> attachAsync() {
      return attachAsync(RequestOptions.DEFAULT);
    }

    /**
     * Like {@link #attach()}, with request options.
     *
     * @see #attach()
     */
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

    /** Attach to a running idempotent invocation, waiting for its output. */
    default Response<Res> attach() throws IngressException {
      return attach(RequestOptions.DEFAULT);
    }

    /**
     * Future version of {@link #getOutput()}, with options.
     *
     * @see #getOutput()
     */
    CompletableFuture<Response<Output<Res>>> getOutputAsync(RequestOptions options);

    /**
     * Future version of {@link #getOutput()}
     *
     * @see #getOutput()
     */
    default CompletableFuture<Response<Output<Res>>> getOutputAsync() {
      return getOutputAsync(RequestOptions.DEFAULT);
    }

    /**
     * Like {@link #getOutput()}, with request options.
     *
     * @see #getOutput()
     */
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

    /**
     * Get the output of an idempotent invocation. If running, {@link Output#isReady()} will be
     * false.
     */
    default Response<Output<Res>> getOutput() throws IngressException {
      return getOutput(RequestOptions.DEFAULT);
    }
  }

  /**
   * Create a new {@link WorkflowHandle} for the provided workflow name and identifier.
   *
   * @param workflowName the workflow name
   * @param workflowId the workflow identifier
   * @param resTypeTag type tag used to deserialize the invocation result
   * @return the workflow handle
   */
  <Res> WorkflowHandle<Res> workflowHandle(
      String workflowName, String workflowId, TypeTag<Res> resTypeTag);

  /**
   * Create a new {@link WorkflowHandle} for the provided workflow name and identifier.
   *
   * @param workflowName the workflow name
   * @param workflowId the workflow identifier
   * @param clazz used to deserialize the workflow result
   * @return the workflow handle
   */
  default <Res> WorkflowHandle<Res> workflowHandle(
      String workflowName, String workflowId, Class<Res> clazz) {
    return workflowHandle(workflowName, workflowId, TypeTag.of(clazz));
  }

  interface WorkflowHandle<Res> {
    /**
     * Future version of {@link #attach()}, with options.
     *
     * @see #attach()
     */
    CompletableFuture<Response<Res>> attachAsync(RequestOptions options);

    /**
     * Future version of {@link #attach()}
     *
     * @see #attach()
     */
    default CompletableFuture<Response<Res>> attachAsync() {
      return attachAsync(RequestOptions.DEFAULT);
    }

    /**
     * Like {@link #attach()}, with request options.
     *
     * @see #attach()
     */
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

    /** Attach to a running workflow, waiting for its output. */
    default Response<Res> attach() throws IngressException {
      return attach(RequestOptions.DEFAULT);
    }

    /**
     * Future version of {@link #getOutput()}, with options.
     *
     * @see #getOutput()
     */
    CompletableFuture<Response<Output<Res>>> getOutputAsync(RequestOptions options);

    /**
     * Future version of {@link #getOutput()}
     *
     * @see #getOutput()
     */
    default CompletableFuture<Response<Output<Res>>> getOutputAsync() {
      return getOutputAsync(RequestOptions.DEFAULT);
    }

    /**
     * Like {@link #getOutput()}, with request options.
     *
     * @see #getOutput()
     */
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

    /** Get the output of a workflow. If running, {@link Output#isReady()} will be false. */
    default Response<Output<Res>> getOutput() throws IngressException {
      return getOutput(RequestOptions.DEFAULT);
    }
  }

  /**
   * <b>EXPERIMENTAL API:</b> Create a reference to invoke a Restate service from the ingress. This
   * API may change in future releases.
   *
   * <p>You can invoke the service in three ways:
   *
   * <pre>{@code
   * Client client = Client.connect("http://localhost:8080");
   *
   * // 1. Create a client proxy and call it directly (returns output directly)
   * var greeterProxy = client.service(Greeter.class).client();
   * GreetingResponse output = greeterProxy.greet(new Greeting("Alice"));
   *
   * // 2. Use call() with method reference and wait for the result
   * Response<GreetingResponse> response = client.service(Greeter.class)
   *   .call(Greeter::greet, new Greeting("Alice"));
   *
   * // 3. Use send() for one-way invocation without waiting
   * SendResponse<GreetingResponse> sendResponse = client.service(Greeter.class)
   *   .send(Greeter::greet, new Greeting("Alice"));
   * }</pre>
   *
   * @param clazz the service class annotated with {@link Service}
   * @return a reference to invoke the service
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <SVC> ClientServiceReference<SVC> service(Class<SVC> clazz) {
    mustHaveAnnotation(clazz, Service.class);
    return new ClientServiceReferenceImpl<>(this, clazz, null);
  }

  /**
   * <b>EXPERIMENTAL API:</b> Create a reference to invoke a Restate Virtual Object from the
   * ingress. This API may change in future releases.
   *
   * <p>You can invoke the virtual object in three ways:
   *
   * <pre>{@code
   * Client client = Client.connect("http://localhost:8080");
   *
   * // 1. Create a client proxy and call it directly (returns output directly)
   * var counterProxy = client.virtualObject(Counter.class, "my-counter").client();
   * int count = counterProxy.increment();
   *
   * // 2. Use call() with method reference and wait for the result
   * Response<Integer> response = client.virtualObject(Counter.class, "my-counter")
   *   .call(Counter::increment);
   *
   * // 3. Use send() for one-way invocation without waiting
   * SendResponse<Integer> sendResponse = client.virtualObject(Counter.class, "my-counter")
   *   .send(Counter::increment);
   * }</pre>
   *
   * @param clazz the virtual object class annotated with {@link VirtualObject}
   * @param key the key identifying the specific virtual object instance
   * @return a reference to invoke the virtual object
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <SVC> ClientServiceReference<SVC> virtualObject(Class<SVC> clazz, String key) {
    mustHaveAnnotation(clazz, VirtualObject.class);
    return new ClientServiceReferenceImpl<>(this, clazz, key);
  }

  /**
   * <b>EXPERIMENTAL API:</b> Create a reference to invoke a Restate Workflow from the ingress. This
   * API may change in future releases.
   *
   * <p>You can invoke the workflow in three ways:
   *
   * <pre>{@code
   * Client client = Client.connect("http://localhost:8080");
   *
   * // 1. Create a client proxy and call it directly (returns output directly)
   * var workflowProxy = client.workflow(OrderWorkflow.class, "order-123").client();
   * OrderResult result = workflowProxy.start(new OrderRequest(...));
   *
   * // 2. Use call() with method reference and wait for the result
   * Response<OrderResult> response = client.workflow(OrderWorkflow.class, "order-123")
   *   .call(OrderWorkflow::start, new OrderRequest(...));
   *
   * // 3. Use send() for one-way invocation without waiting
   * SendResponse<OrderResult> sendResponse = client.workflow(OrderWorkflow.class, "order-123")
   *   .send(OrderWorkflow::start, new OrderRequest(...));
   * }</pre>
   *
   * @param clazz the workflow class annotated with {@link Workflow}
   * @param key the key identifying the specific workflow instance
   * @return a reference to invoke the workflow
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <SVC> ClientServiceReference<SVC> workflow(Class<SVC> clazz, String key) {
    mustHaveAnnotation(clazz, Workflow.class);
    return new ClientServiceReferenceImpl<>(this, clazz, key);
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
