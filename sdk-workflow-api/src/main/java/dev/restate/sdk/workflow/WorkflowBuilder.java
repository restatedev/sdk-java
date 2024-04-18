// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.workflow;

import dev.restate.sdk.Service;
import dev.restate.sdk.common.BindableService;
import dev.restate.sdk.common.HandlerType;
import dev.restate.sdk.workflow.impl.WorkflowImpl;
import java.util.HashMap;
import java.util.function.BiFunction;

public final class WorkflowBuilder {

  private final String name;
  private final Service.Handler<?, ?> workflowMethod;
  private final HashMap<String, Service.Handler<?, ?>> sharedMethods;

  private WorkflowBuilder(String name, Service.Handler<?, ?> workflowMethod) {
    this.name = name;
    this.workflowMethod = workflowMethod;
    this.sharedMethods = new HashMap<>();
  }

  public <REQ, RES> WorkflowBuilder withShared(
      Service.HandlerSignature<REQ, RES> sig, BiFunction<WorkflowSharedContext, REQ, RES> runner) {
    this.sharedMethods.put(sig.getName(), new Service.Handler<>(sig, HandlerType.SHARED, runner));
    return this;
  }

  public BindableService<Service.Options> build(Service.Options options) {
    return new WorkflowImpl(name, options, workflowMethod, sharedMethods);
  }

  public static <REQ, RES> WorkflowBuilder named(
      String name,
      Service.HandlerSignature<REQ, RES> sig,
      BiFunction<WorkflowContext, REQ, RES> runner) {
    return new WorkflowBuilder(name, new Service.Handler<>(sig, HandlerType.SHARED, runner));
  }
}
