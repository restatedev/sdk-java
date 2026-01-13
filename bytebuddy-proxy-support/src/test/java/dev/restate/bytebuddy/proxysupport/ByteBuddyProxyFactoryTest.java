// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.bytebuddy.proxysupport;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Fail.fail;

import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Service;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ByteBuddyProxyFactoryTest {

  @Service
  public static class InvokeNonRestateMethod {
    public void somethingElse() {}
  }

  @Test
  @DisplayName("Invoking non restate method should fail")
  public void badCallToNonRestateMethod() {
    var proxyFactory = new ByteBuddyProxyFactory();
    var proxy =
        proxyFactory.createProxy(
            InvokeNonRestateMethod.class,
            invocation -> fail("Unexpected call to method interceptor"));

    assertThatCode(() -> proxy.somethingElse())
        .hasMessageContaining(
            "Calling a method not annotated with a Restate handler annotation on the proxy class")
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Service
  public static class PackagePrivateMethod {
    @Handler
    void handler() {
      fail("This code should not be executed");
    }
  }

  @Test
  @DisplayName("Package private method should fail")
  public void packagePrivateMethod() {
    var proxyFactory = new ByteBuddyProxyFactory();
    assertThatCode(
            () ->
                proxyFactory.createProxy(
                    PackagePrivateMethod.class,
                    invocation -> fail("Unexpected call to method interceptor")))
        .cause()
        .cause()
        .hasMessageContaining("MUST be public to be used as Restate handler");
  }

  @Service
  public static class FinalMethod {
    @Handler
    public final void handler() {
      fail("This code should not be executed");
    }
  }

  @Test
  @DisplayName("Final method should fail")
  public void finalMethod() {
    var proxyFactory = new ByteBuddyProxyFactory();
    assertThatCode(
            () ->
                proxyFactory.createProxy(
                    FinalMethod.class, invocation -> fail("Unexpected call to method interceptor")))
        .cause()
        .cause()
        .hasMessageContaining("is final");
  }

  @Service
  public static final class FinalClass {
    @Handler
    public void handler() {
      fail("This code should not be executed");
    }
  }

  @Test
  @DisplayName("Final class should fail")
  public void finalClass() {
    var proxyFactory = new ByteBuddyProxyFactory();
    assertThatCode(
            () ->
                proxyFactory.createProxy(
                    FinalClass.class, invocation -> fail("Unexpected call to method interceptor")))
        .hasMessageContaining("is final, cannot be proxied");
  }
}
