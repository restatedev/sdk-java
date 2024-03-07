// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.workflow;

import dev.restate.sdk.Component;
import dev.restate.sdk.common.BindableComponent;
import dev.restate.sdk.workflow.impl.WorkflowImpl;
import java.util.HashMap;
import java.util.function.BiFunction;

public final class WorkflowBuilder {

  private final String name;
  private final Component.Handler<?, ?> workflowMethod;
  private final HashMap<String, Component.Handler<?, ?>> sharedMethods;

  private WorkflowBuilder(String name, Component.Handler<?, ?> workflowMethod) {
    this.name = name;
    this.workflowMethod = workflowMethod;
    this.sharedMethods = new HashMap<>();
  }

  public <REQ, RES> WorkflowBuilder withShared(
      Component.HandlerSignature<REQ, RES> sig,
      BiFunction<WorkflowSharedContext, REQ, RES> runner) {
    this.sharedMethods.put(sig.getName(), new Component.Handler<>(sig, runner));
    return this;
  }

  public BindableComponent build() {
    return new WorkflowImpl(name, workflowMethod, sharedMethods);
  }

  public static <REQ, RES> WorkflowBuilder named(
      String name,
      Component.HandlerSignature<REQ, RES> sig,
      BiFunction<WorkflowContext, REQ, RES> runner) {
    return new WorkflowBuilder(name, new Component.Handler<>(sig, runner));
  }
}
