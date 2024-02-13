// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.workflow;

import dev.restate.sdk.Context;
import dev.restate.sdk.common.StateKey;
import java.util.Optional;

public interface WorkflowSharedContext extends Context {

  String workflowKey();

  <T> Optional<T> get(StateKey<T> key);

  // -- Signals

  <T> DurablePromise<T> durablePromise(DurablePromiseKey<T> key);

  <T> DurablePromiseHandle<T> durablePromiseHandle(DurablePromiseKey<T> key);
}
