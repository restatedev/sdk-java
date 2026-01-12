// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.common.reflections;

import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface ProxyFactory {

  interface MethodInvocation {
    Object[] getArguments();

    Method getMethod();
  }

  @FunctionalInterface
  interface MethodInterceptor {
    @Nullable Object invoke(MethodInvocation invocation) throws Throwable;
  }

  /** If returns null, it's not supported. */
  <T> @Nullable T createProxy(Class<T> clazz, MethodInterceptor interceptor);
}
