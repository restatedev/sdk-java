// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.common.syscalls;

import com.google.protobuf.ByteString;

public interface InvocationHandler<REQ, RES, O> {
  /**
   * Thread local to store {@link Syscalls}.
   *
   * <p>Implementations of {@link InvocationHandler} should correctly propagate this thread local in
   * order for logging to work correctly. Could be improved if ScopedContext <a
   * href="https://github.com/apache/logging-log4j2/pull/2438">will ever be introduced in
   * log4j2</a>.
   */
  ThreadLocal<Syscalls> SYSCALLS_THREAD_LOCAL = new ThreadLocal<>();

  void handle(HandlerSpecification<REQ, RES> handlerSpecification, Syscalls syscalls, O options, SyscallCallback<ByteString> callback);
}
