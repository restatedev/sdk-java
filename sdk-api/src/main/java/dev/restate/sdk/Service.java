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
import dev.restate.sdk.common.ServiceType;
import dev.restate.sdk.common.syscalls.*;
import io.opentelemetry.context.Scope;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Service implements BindableService<Service.Options> {
  private final ServiceDefinition<Options> serviceDefinition;
  private final Service.Options options;

  private Service(
      String fqsn,
      boolean isKeyed,
      HashMap<String, HandlerDefinition<?, ?, Options>> handlers,
      Options options) {
    this.serviceDefinition =
        ServiceDefinition.of(
            fqsn,
            isKeyed ? ServiceType.VIRTUAL_OBJECT : ServiceType.SERVICE,
            new ArrayList<>(handlers.values()));
    this.options = options;
  }

  @Override
  public Options options() {
    return this.options;
  }

  @Override
  public List<ServiceDefinition<Options>> definitions() {
    return List.of(this.serviceDefinition);
  }

  public static ServiceBuilder service(String name) {
    return new ServiceBuilder(name);
  }

  public static VirtualObjectBuilder virtualObject(String name) {
    return new VirtualObjectBuilder(name);
  }

  public static class AbstractServiceBuilder {

    protected final String name;
    protected final HashMap<String, HandlerDefinition<?, ?, Options>> handlers;

    public AbstractServiceBuilder(String name) {
      this.name = name;
      this.handlers = new HashMap<>();
    }
  }

  public static class VirtualObjectBuilder extends AbstractServiceBuilder {

    VirtualObjectBuilder(String name) {
      super(name);
    }

    public <REQ, RES> VirtualObjectBuilder withShared(
        String name,
        Serde<REQ> requestSerde,
        Serde<RES> responseSerde,
        BiFunction<SharedObjectContext, REQ, RES> runner) {
      this.handlers.put(
          name,
          HandlerDefinition.of(
              HandlerSpecification.of(name, HandlerType.SHARED, requestSerde, responseSerde),
              new Handler<>(runner)));
      return this;
    }

    public <REQ, RES> VirtualObjectBuilder withExclusive(
        String name,
        Serde<REQ> requestSerde,
        Serde<RES> responseSerde,
        BiFunction<ObjectContext, REQ, RES> runner) {
      this.handlers.put(
          name,
          HandlerDefinition.of(
              HandlerSpecification.of(name, HandlerType.EXCLUSIVE, requestSerde, responseSerde),
              new Handler<>(runner)));
      return this;
    }

    public Service build(Service.Options options) {
      return new Service(this.name, true, this.handlers, options);
    }
  }

  public static class ServiceBuilder extends AbstractServiceBuilder {

    ServiceBuilder(String name) {
      super(name);
    }

    public <REQ, RES> ServiceBuilder with(
        String name,
        Serde<REQ> requestSerde,
        Serde<RES> responseSerde,
        BiFunction<Context, REQ, RES> runner) {
      this.handlers.put(
          name,
          HandlerDefinition.of(
              HandlerSpecification.of(name, HandlerType.SHARED, requestSerde, responseSerde),
              new Handler<>(runner)));
      return this;
    }

    public Service build(Service.Options options) {
      return new Service(this.name, false, this.handlers, options);
    }
  }

  public static class Handler<REQ, RES> implements InvocationHandler<REQ, RES, Service.Options> {
    private final BiFunction<Context, REQ, RES> runner;

    private static final Logger LOG = LogManager.getLogger(Handler.class);

    Handler(BiFunction<? extends Context, REQ, RES> runner) {
      //noinspection unchecked
      this.runner = (BiFunction<Context, REQ, RES>) runner;
    }

    public BiFunction<Context, REQ, RES> getRunner() {
      return runner;
    }

    @Override
    public void handle(
        HandlerSpecification<REQ, RES> handlerSpecification,
        Syscalls syscalls,
        Service.Options options,
        SyscallCallback<ByteString> callback) {
      // Wrap the executor for setting/unsetting the thread local
      Executor wrapped =
          runnable ->
              options.executor.execute(
                  () -> {
                    SYSCALLS_THREAD_LOCAL.set(syscalls);
                    try (Scope ignored = syscalls.request().otelContext().makeCurrent()) {
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
              req =
                  handlerSpecification
                      .getRequestSerde()
                      .deserialize(syscalls.request().bodyBuffer());
            } catch (Error e) {
              throw e;
            } catch (Throwable e) {
              LOG.warn("Cannot deserialize input", e);
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
              serializedResult = handlerSpecification.getResponseSerde().serializeToByteString(res);
            } catch (Error e) {
              throw e;
            } catch (Throwable e) {
              LOG.warn("Cannot serialize output", e);
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

    public static <CTX extends Context, REQ, RES> Handler<REQ, RES> of(BiFunction<CTX, REQ, RES> runner) {
      return new Handler<>(runner);
    }

    @SuppressWarnings("unchecked")
    public static <CTX extends Context, RES> Handler<Void, RES> of(Function<CTX, RES> runner) {
      return new Handler<>((context, o) -> runner.apply((CTX) context));
    }

    @SuppressWarnings("unchecked")
    public static <CTX extends Context, REQ> Handler<REQ, Void> of(BiConsumer<CTX, REQ> runner) {
      return new Handler<>((context, o) -> {
        runner.accept((CTX) context, o);
        return null;
      });
    }

    @SuppressWarnings("unchecked")
    public static <CTX extends Context> Handler<Void, Void> of(Consumer<CTX> runner) {
      return new Handler<>((ctx, o) -> {
        runner.accept((CTX) ctx);
        return null;
      });
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
