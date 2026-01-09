// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.bytebuddy.proxysupport;

import dev.restate.common.reflections.ProxySupport;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.TypeCache;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.scaffold.TypeValidation;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;
import org.jspecify.annotations.Nullable;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;

/**
 * ByteBuddy-based proxy factory that supports both interfaces and concrete classes. This
 * implementation can create proxies for any class that is not final. Uses Objenesis to instantiate
 * objects without calling constructors, which allows proxying classes that don't have a no-arg
 * constructor. Uses TypeCache to cache generated proxy classes for better performance
 * (thread-safe).
 */
public final class ByteBuddyProxyFactory implements ProxySupport.ProxyFactory {

  private static final String INTERCEPTOR_FIELD_NAME = "$$interceptor$$";

  private final Objenesis objenesis = new ObjenesisStd();
  private final TypeCache<Class<?>> proxyClassCache =
      new TypeCache.WithInlineExpunction<>(TypeCache.Sort.SOFT);

  @Override
  @SuppressWarnings("unchecked")
  public <T> @Nullable T createProxy(Class<T> clazz, ProxySupport.MethodInterceptor interceptor) {
    // Cannot proxy final classes
    if (Modifier.isFinal(clazz.getModifiers())) {
      return null;
    }

    try {
      // Find or create the proxy class (cached)
      Class<? extends T> proxyClass =
          (Class<? extends T>)
              proxyClassCache.findOrInsert(
                  clazz.getClassLoader(), clazz, () -> generateProxyClass(clazz), proxyClassCache);

      // Instantiate the proxy class using Objenesis (no constructor call)
      T proxyInstance = objenesis.newInstance(proxyClass);

      // Set the interceptor field
      Field interceptorField = proxyClass.getDeclaredField(INTERCEPTOR_FIELD_NAME);
      interceptorField.setAccessible(true);
      interceptorField.set(proxyInstance, interceptor);

      return proxyInstance;

    } catch (Exception e) {
      // Could not create or instantiate the proxy
      return null;
    }
  }

  private <T> Class<?> generateProxyClass(Class<T> clazz) {
    ByteBuddy byteBuddy = new ByteBuddy().with(TypeValidation.DISABLED);

    var builder =
        clazz.isInterface()
            ? byteBuddy.subclass(Object.class).implement(clazz)
            : byteBuddy.subclass(clazz);

    try (var unloaded =
        builder
            // Add a field to store the interceptor
            .defineField(
                INTERCEPTOR_FIELD_NAME, ProxySupport.MethodInterceptor.class, Visibility.PUBLIC)
            // Intercept all methods
            .method(ElementMatchers.any())
            .intercept(
                InvocationHandlerAdapter.of(
                    (proxy, method, args) -> {
                      // Get the interceptor from the field
                      Field field = proxy.getClass().getDeclaredField(INTERCEPTOR_FIELD_NAME);
                      field.setAccessible(true);
                      ProxySupport.MethodInterceptor interceptor =
                          (ProxySupport.MethodInterceptor) field.get(proxy);

                      if (interceptor == null) {
                        throw new IllegalStateException("Interceptor not set on proxy instance");
                      }

                      ProxySupport.MethodInvocation invocation =
                          new ProxySupport.MethodInvocation() {
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
                    }))
            .make()) {
      return unloaded.load(clazz.getClassLoader()).getLoaded();
    }
  }
}
