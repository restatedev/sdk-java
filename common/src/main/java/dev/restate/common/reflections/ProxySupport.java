// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.common.reflections;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

public final class ProxySupport {

  private static class ProxySupportSingleton {
    private static final ProxySupport INSTANCE = new ProxySupport();
  }

  private static final Logger LOG = LogManager.getLogger(ProxySupport.class);

  private final List<ProxyFactory> factories;

  public ProxySupport() {
    this.factories = new ArrayList<>(2);
    this.factories.add(new JdkProxyFactory());

    var serviceLoaderIterator = ServiceLoader.load(ProxyFactory.class).iterator();
    while (serviceLoaderIterator.hasNext()) {
      try {
        this.factories.add(serviceLoaderIterator.next());
      } catch (ServiceConfigurationError | Exception e) {
        LOG.error(
            "Found proxy factory that cannot be loaded using service provider. Proxy clients might not work correctly.",
            e);
        throw e;
      }
    }
  }

  /** Resolve the code generated {@link ProxyFactory} */
  public static <T> T createProxy(Class<T> clazz, MethodInterceptor interceptor) {
    ProxySupport proxySupport = ProxySupportSingleton.INSTANCE;

    for (ProxyFactory proxyFactory : proxySupport.factories) {
      T proxy = proxyFactory.createProxy(clazz, interceptor);
      if (proxy != null) {
        return proxy;
      }
    }

    throw new IllegalStateException(
        "Class "
            + clazz.toString()
            + " cannot be proxied. If the type is a concrete class, make sure to have sdk-proxy-bytebuddy in your dependencies. Registered proxies: "
            + proxySupport.factories.stream()
                .map(pf -> pf.getClass().toString())
                .collect(Collectors.joining(", ")));
  }

  public interface MethodInvocation {
    Object[] getArguments();

    Method getMethod();
  }

  @FunctionalInterface
  public interface MethodInterceptor {
    @Nullable Object invoke(MethodInvocation invocation) throws Throwable;
  }

  @FunctionalInterface
  public interface ProxyFactory {
    /** If returns null, it's not supported. */
    <T> @Nullable T createProxy(Class<T> clazz, MethodInterceptor interceptor);
  }

  private static final class JdkProxyFactory implements ProxyFactory {

    /**
     * Mutable InvocationHandler wrapper that holds the interceptor. This provides consistency with
     * ByteBuddy proxies where the interceptor is set via a field after instantiation.
     */
    private static class InterceptorHolder implements InvocationHandler {
      MethodInterceptor interceptor;

      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (interceptor == null) {
          throw new IllegalStateException("Interceptor not set on JDK proxy instance");
        }

        MethodInvocation invocation =
            new MethodInvocation() {
              @Override
              public Object[] getArguments() {
                return args != null ? args : new Object[0];
              }

              @Override
              public Method getMethod() {
                return method;
              }
            };
        return interceptor.invoke(invocation);
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T createProxy(Class<T> clazz, MethodInterceptor interceptor) {
      if (!clazz.isInterface()) {
        return null;
      }

      // Create holder with interceptor field (similar to ByteBuddy approach)
      InterceptorHolder holder = new InterceptorHolder();

      // Create proxy with the holder (JDK caches proxy class automatically)
      T proxy = (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[] {clazz}, holder);

      // Set the interceptor after proxy creation (consistent with ByteBuddy)
      holder.interceptor = interceptor;

      return proxy;
    }
  }
}
