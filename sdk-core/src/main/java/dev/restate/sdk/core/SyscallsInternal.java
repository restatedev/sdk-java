// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.sdk.common.syscalls.DeferredResult;
import dev.restate.sdk.common.syscalls.Syscalls;
import dev.restate.sdk.core.DeferredResults.DeferredResultInternal;
import java.util.List;
import java.util.stream.Collectors;

public interface SyscallsInternal extends Syscalls {

  @Override
  default DeferredResult<Integer> createAnyDeferred(List<DeferredResult<?>> children) {
    return DeferredResults.any(
        children.stream().map(dr -> (DeferredResultInternal<?>) dr).collect(Collectors.toList()));
  }

  @Override
  default DeferredResult<Void> createAllDeferred(List<DeferredResult<?>> children) {
    return DeferredResults.all(
        children.stream().map(dr -> (DeferredResultInternal<?>) dr).collect(Collectors.toList()));
  }

  // -- Lifecycle methods

  void close();
}
