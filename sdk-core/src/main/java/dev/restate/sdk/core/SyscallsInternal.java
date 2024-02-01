// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import dev.restate.sdk.common.syscalls.Deferred;
import dev.restate.sdk.common.syscalls.Result;
import dev.restate.sdk.common.syscalls.SyscallCallback;
import dev.restate.sdk.common.syscalls.Syscalls;
import dev.restate.sdk.core.DeferredResults.DeferredInternal;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

interface SyscallsInternal extends Syscalls {

  @Override
  default Deferred<Integer> createAnyDeferred(List<Deferred<?>> children) {
    return DeferredResults.any(
        children.stream().map(dr -> (DeferredInternal<?>) dr).collect(Collectors.toList()));
  }

  @Override
  default Deferred<Void> createAllDeferred(List<Deferred<?>> children) {
    return DeferredResults.all(
        children.stream().map(dr -> (DeferredInternal<?>) dr).collect(Collectors.toList()));
  }

  // -- Helper for pollInput

  default <T extends MessageLite> void pollInputAndResolve(
      Function<ByteString, T> mapper, SyscallCallback<Result<T>> callback) {
    this.pollInput(
        mapper,
        SyscallCallback.of(
            deferredValue ->
                this.resolveDeferred(
                    deferredValue,
                    SyscallCallback.ofVoid(
                        () -> callback.onSuccess(deferredValue.toResult()), callback::onCancel)),
            callback::onCancel));
  }

  // -- Lifecycle methods

  void close();

  // -- State machine introspection (used by logging propagator)

  /**
   * @return fully qualified method name in the form {fullyQualifiedServiceName}/{methodName}
   */
  String getFullyQualifiedMethodName();

  InvocationState getInvocationState();
}
