// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.common.reflections;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public final class MethodInfoCollector<SVC> {

  private final SVC infoCollectorProxy;

  public MethodInfoCollector(Class<SVC> svcClass) {
    this.infoCollectorProxy = ProxySupport.createProxy(svcClass, METHOD_INFO_COLLECTOR_INTERCEPTOR);
  }

  public <O> MethodInfo resolve(Function<SVC, O> methodReference) {
    try {
      methodReference.apply(this.infoCollectorProxy);
      throw new UnsupportedOperationException(
          "The provided lambda MUST contain ONLY a method reference to the service method");
    } catch (MethodInfo e) {
      return e;
    }
  }

  public <I, O> MethodInfo resolve(BiFunction<SVC, I, O> methodReference, I input) {
    try {
      methodReference.apply(this.infoCollectorProxy, input);
      throw new UnsupportedOperationException(
          "The provided lambda MUST contain ONLY a method reference to the service method");
    } catch (MethodInfo e) {
      return e;
    }
  }

  public <I> MethodInfo resolve(BiConsumer<SVC, I> methodReference, I input) {
    try {
      methodReference.accept(this.infoCollectorProxy, input);
      throw new UnsupportedOperationException(
          "The provided lambda MUST contain ONLY a method reference to a service method");
    } catch (MethodInfo e) {
      return e;
    }
  }

  public MethodInfo resolve(Consumer<SVC> methodReference) {
    try {
      methodReference.accept(this.infoCollectorProxy);
      throw new UnsupportedOperationException(
          "The provided lambda MUST contain ONLY a method reference to a service method");
    } catch (MethodInfo e) {
      return e;
    }
  }

  private static final ProxyFactory.MethodInterceptor METHOD_INFO_COLLECTOR_INTERCEPTOR =
      invocation -> {
        throw MethodInfo.fromMethod(invocation.getMethod());
      };
}
