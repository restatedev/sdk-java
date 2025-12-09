// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.sdk.endpoint.definition.HandlerContext;
import dev.restate.serde.SerdeFactory;
import java.util.concurrent.Executor;

@org.jetbrains.annotations.ApiStatus.Internal
public class ContextInternal {

  @org.jetbrains.annotations.ApiStatus.Internal
  public static WorkflowContext createContext(
      HandlerContext handlerContext, Executor serviceExecutor, SerdeFactory serdeFactory) {
    return new ContextImpl(handlerContext, serviceExecutor, serdeFactory);
  }
}
