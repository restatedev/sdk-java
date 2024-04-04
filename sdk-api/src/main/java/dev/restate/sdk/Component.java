// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import com.google.protobuf.ByteString;
import dev.restate.sdk.common.*;
import dev.restate.sdk.common.ComponentType;
import dev.restate.sdk.common.syscalls.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public final class Component implements BindableComponent<Component.Options> {
  private final ComponentDefinition<Component.Options> componentDefinition;
  private final Component.Options options;

  private Component(
      String fqsn, boolean isKeyed, HashMap<String, Handler<?, ?>> handlers, Options options) {
    this.componentDefinition =
        new ComponentDefinition<>(
            fqsn,
            isKeyed ? ComponentType.VIRTUAL_OBJECT : ComponentType.SERVICE,
            handlers.values().stream()
                .map(Handler::toHandlerDefinition)
                .collect(Collectors.toList()));
    this.options = options;
  }

  @Override
  public Options options() {
    return this.options;
  }

  @Override
  public List<ComponentDefinition<Component.Options>> definitions() {
    return List.of(this.componentDefinition);
  }

  public static ServiceBuilder service(String name) {
    return new ServiceBuilder(name);
  }

  public static VirtualObjectBuilder virtualObject(String name) {
    return new VirtualObjectBuilder(name);
  }

  public static class AbstractComponentBuilder {

    protected final String name;
    protected final HashMap<String, Handler<?, ?>> handlers;

    public AbstractComponentBuilder(String name) {
      this.name = name;
      this.handlers = new HashMap<>();
    }
  }

  public static class VirtualObjectBuilder extends AbstractComponentBuilder {

    VirtualObjectBuilder(String name) {
      super(name);
    }

    public <REQ, RES> VirtualObjectBuilder with(
        HandlerSignature<REQ, RES> sig, BiFunction<ObjectContext, REQ, RES> runner) {
      this.handlers.put(sig.getName(), new Handler<>(sig, runner));
      return this;
    }

    public Component build(Component.Options options) {
      return new Component(this.name, true, this.handlers, options);
    }
  }

  public static class ServiceBuilder extends AbstractComponentBuilder {

    ServiceBuilder(String name) {
      super(name);
    }

    public <REQ, RES> ServiceBuilder with(
        HandlerSignature<REQ, RES> sig, BiFunction<Context, REQ, RES> runner) {
      this.handlers.put(sig.getName(), new Handler<>(sig, runner));
      return this;
    }

    public Component build(Component.Options options) {
      return new Component(this.name, false, this.handlers, options);
    }
  }

  @SuppressWarnings("unchecked")
  public static class Handler<REQ, RES> implements InvocationHandler<Component.Options> {
    private final HandlerSignature<REQ, RES> handlerSignature;
    private final BiFunction<Context, REQ, RES> runner;

    public Handler(
        HandlerSignature<REQ, RES> handlerSignature,
        BiFunction<? extends Context, REQ, RES> runner) {
      this.handlerSignature = handlerSignature;
      this.runner = (BiFunction<Context, REQ, RES>) runner;
    }

    public HandlerSignature<REQ, RES> getHandlerSignature() {
      return handlerSignature;
    }

    public BiFunction<Context, REQ, RES> getRunner() {
      return runner;
    }

    public HandlerDefinition<Component.Options> toHandlerDefinition() {
      return new HandlerDefinition<>(
          this.handlerSignature.name,
          this.handlerSignature.requestSerde.schema(),
          this.handlerSignature.responseSerde.schema(),
          this);
    }

    @Override
    public void handle(
        Syscalls syscalls, Component.Options options, SyscallCallback<ByteString> callback) {
      // Wrap the executor for setting/unsetting the thread local
      Executor wrapped =
          runnable ->
              options.executor.execute(
                  () -> {
                    SYSCALLS_THREAD_LOCAL.set(syscalls);
                    try {
                      runnable.run();
                    } finally {
                      SYSCALLS_THREAD_LOCAL.remove();
                    }
                  });
      wrapped.execute(
          () -> {
            // Any context switching, if necessary, will be done by ResolvedEndpointHandler
            Context ctx = new ContextImpl(syscalls);

            // Parse input
            REQ req;
            try {
              req = this.handlerSignature.requestSerde.deserialize(syscalls.request().bodyBuffer());
            } catch (Error e) {
              throw e;
            } catch (Throwable e) {
              callback.onCancel(
                  new TerminalException(
                      TerminalException.BAD_REQUEST_CODE,
                      "Cannot deserialize input: " + e.getMessage()));
              return;
            }

            // Execute user code
            RES res;
            try {
              res = this.runner.apply(ctx, req);
            } catch (Error e) {
              throw e;
            } catch (Throwable e) {
              callback.onCancel(e);
              return;
            }

            // Serialize output
            ByteString serializedResult;
            try {
              serializedResult = this.handlerSignature.responseSerde.serializeToByteString(res);
            } catch (Error e) {
              throw e;
            } catch (Throwable e) {
              callback.onCancel(
                  new TerminalException(
                      TerminalException.INTERNAL_SERVER_ERROR_CODE,
                      "Cannot serialize output: " + e.getMessage()));
              return;
            }

            // Complete callback
            callback.onSuccess(serializedResult);
          });
    }
  }

  public static class HandlerSignature<REQ, RES> {

    private final String name;
    private final Serde<REQ> requestSerde;
    private final Serde<RES> responseSerde;

    HandlerSignature(String name, Serde<REQ> requestSerde, Serde<RES> responseSerde) {
      this.name = name;
      this.requestSerde = requestSerde;
      this.responseSerde = responseSerde;
    }

    public static <T, R> HandlerSignature<T, R> of(
        String method, Serde<T> requestSerde, Serde<R> responseSerde) {
      return new HandlerSignature<>(method, requestSerde, responseSerde);
    }

    public String getName() {
      return name;
    }

    public Serde<REQ> getRequestSerde() {
      return requestSerde;
    }

    public Serde<RES> getResponseSerde() {
      return responseSerde;
    }
  }

  public static class Options {
    public static final Options DEFAULT = new Options(Executors.newCachedThreadPool());

    private final Executor executor;

    /**
     * You can run on virtual threads by using the executor {@code
     * Executors.newVirtualThreadPerTaskExecutor()}.
     */
    public Options(Executor executor) {
      this.executor = executor;
    }
  }
}
