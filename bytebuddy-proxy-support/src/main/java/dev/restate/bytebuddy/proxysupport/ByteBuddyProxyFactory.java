// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.bytebuddy.proxysupport;

import static dev.restate.common.reflections.ReflectionUtils.findRestateAnnotatedClass;
import static net.bytebuddy.matcher.ElementMatchers.*;

import dev.restate.common.reflections.ProxyFactory;
import dev.restate.common.reflections.ReflectionUtils;
import dev.restate.sdk.annotation.Exclusive;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Shared;
import dev.restate.sdk.annotation.Workflow;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.TypeCache;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.scaffold.TypeValidation;
import net.bytebuddy.implementation.ExceptionMethod;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
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
public final class ByteBuddyProxyFactory implements ProxyFactory {

  private static final String INTERCEPTOR_FIELD_NAME = "$$interceptor$$";

  private final Objenesis objenesis = new ObjenesisStd();
  private final ByteBuddy byteBuddy = new ByteBuddy().with(TypeValidation.ENABLED);
  private final TypeCache<Class<?>> proxyClassCache =
      new TypeCache.WithInlineExpunction<>(TypeCache.Sort.SOFT);

  @Override
  @SuppressWarnings("unchecked")
  public <T> @Nullable T createProxy(Class<T> clazz, MethodInterceptor interceptor) {
    // Cannot proxy final classes
    if (Modifier.isFinal(clazz.getModifiers())) {
      if (ReflectionUtils.isKotlinClass(clazz)) {
        throw new IllegalArgumentException(
            clazz
                +
"""
 is not open, cannot be proxied. Suggestions:
* Extract the @Handler annotated functions in an interface
* Make the class and all its @Handler annotated functions 'open'
* Use the Kotlin allopen compiler plugin https://kotlinlang.org/docs/all-open-plugin.html with the following configuration:

allOpen {
    annotations("dev.restate.sdk.annotation.Service", "dev.restate.sdk.annotation.VirtualObject", "dev.restate.sdk.annotation.Workflow")
}
""");
      }
      throw new IllegalArgumentException(
          clazz
              + " is final, cannot be proxied. Remove the final keyword, or refactor it extracting the restate interface out of it.");
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
      interceptorField.set(
          proxyInstance,
          (InvocationHandler)
              (proxy, method, args) -> {
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
              });

      return proxyInstance;
    } catch (Exception e) {
      throw new IllegalArgumentException("Cannot create proxy for " + clazz, e);
    }
  }

  private <T> Class<?> generateProxyClass(Class<T> clazz) throws NoSuchFieldException {
    if (!clazz.isInterface()) {
      // We perform here some additional validation of the handlers that won't be executed by
      // bytebuddy and can easily lead to strange behavior
      var restateAnnotatedClazz = findRestateAnnotatedClass(clazz);
      var methods = ReflectionUtils.findRestateHandlers(restateAnnotatedClazz);
      for (var method : methods) {
        validateMethod(method);
      }
    }

    var builder =
        clazz.isInterface()
            ? byteBuddy.subclass(Object.class).implement(clazz)
            : byteBuddy.subclass(clazz);

    var annotationMatcher =
        not(isStatic())
            .and(
                isAnnotatedWith(Handler.class)
                    .or(isAnnotatedWith(Exclusive.class))
                    .or(isAnnotatedWith(Shared.class))
                    .or(isAnnotatedWith(Workflow.class)));
    try (var unloaded =
        builder
            // Add a field to store the interceptor
            .defineField(INTERCEPTOR_FIELD_NAME, InvocationHandler.class, Visibility.PUBLIC)
            // Intercept all methods
            .method(annotationMatcher)
            .intercept(InvocationHandlerAdapter.toField(INTERCEPTOR_FIELD_NAME))
            .method(not(annotationMatcher))
            .intercept(
                ExceptionMethod.throwing(
                    UnsupportedOperationException.class,
                    "Calling a method not annotated with a Restate handler annotation on the proxy class"))
            .make()) {

      var proxyClazz = unloaded.load(clazz.getClassLoader()).getLoaded();

      // Make sure the field is accessible
      Field interceptorField = proxyClazz.getDeclaredField(INTERCEPTOR_FIELD_NAME);
      interceptorField.setAccessible(true);
      return proxyClazz;
    }
  }

  private static void validateMethod(Method method) {
    if (!Modifier.isPublic(method.getModifiers())) {
      throw new IllegalArgumentException(
          "Method '"
              + method.getDeclaringClass().getSimpleName()
              + "#"
              + method.getName()
              + "' MUST be public to be used as Restate handler. Modifiers:"
              + Modifier.toString(method.getModifiers()));
    }
    if (Modifier.isStatic(method.getModifiers())) {
      throw new IllegalArgumentException(
          "Method '"
              + method.getDeclaringClass().getSimpleName()
              + "#"
              + method.getName()
              + "' is static, cannot be used as Restate handler");
    }
    if (Modifier.isFinal(method.getModifiers())) {
      throw new IllegalArgumentException(
          "Method '"
              + method.getDeclaringClass().getSimpleName()
              + "#"
              + method.getName()
              + "' is final, cannot be used as Restate handler");
    }
  }
}
