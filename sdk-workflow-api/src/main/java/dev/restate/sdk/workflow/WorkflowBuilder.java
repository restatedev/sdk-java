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
import dev.restate.sdk.common.Serde;
import dev.restate.sdk.common.syscalls.HandlerDefinition;
import dev.restate.sdk.common.syscalls.HandlerSpecification;
import dev.restate.sdk.workflow.impl.WorkflowImpl;
import java.util.HashMap;
import java.util.function.BiFunction;

public final class WorkflowBuilder {

  private final String name;
  private final HandlerDefinition<?, ?, Service.Options> workflowMethod;
  private final HashMap<String, HandlerDefinition<?, ?, Service.Options>> sharedMethods;

  private WorkflowBuilder(String name, HandlerDefinition<?, ?, Service.Options> workflowMethod) {
    this.name = name;
    this.workflowMethod = workflowMethod;
    this.sharedMethods = new HashMap<>();
  }

  public <REQ, RES> WorkflowBuilder withShared(
          String name,
          Serde<REQ> requestSerde, Serde<RES> responseSerde, BiFunction<WorkflowSharedContext, REQ, RES> runner) {
    this.sharedMethods.put(
name,
            HandlerDefinition.of(HandlerSpecification.of(
                    name,
                    HandlerType.EXCLUSIVE,
                    requestSerde,
                    responseSerde
            ),
                    Service.Handler.of(runner)));
    return this;
  }

  public BindableService<Service.Options> build(Service.Options options) {
    return new WorkflowImpl(name, options, workflowMethod, sharedMethods);
  }

  public static <REQ, RES> WorkflowBuilder named(
          String name,
          Serde<REQ> requestSerde, Serde<RES> responseSerde,
          BiFunction<WorkflowContext, REQ, RES> runner) {
    return new WorkflowBuilder(
            name,
            HandlerDefinition.of(HandlerSpecification.of(
                    "run",
                    HandlerType.EXCLUSIVE,
                    requestSerde,
                    responseSerde
            ),
                    Service.Handler.of(runner))
    );
  }
}
