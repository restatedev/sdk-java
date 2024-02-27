// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.workflow.impl;

import dev.restate.sdk.Context;
import dev.restate.sdk.common.BlockingComponent;
import dev.restate.sdk.common.ComponentBundle;
import dev.restate.sdk.dynrpc.JavaComponent;
import dev.restate.sdk.workflow.WorkflowContext;
import dev.restate.sdk.workflow.WorkflowSharedContext;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

public class WorkflowComponentBundle implements ComponentBundle {
  private final String name;
  private final JavaComponent.HandlerSignature<?, ?> sig;
  private final BiFunction<WorkflowContext, ?, ?> runner;
  private final HashMap<String, Method<?, ?>> sharedMethods;

  public WorkflowComponentBundle(
      String name,
      JavaComponent.HandlerSignature<?, ?> sig,
      BiFunction<WorkflowContext, ?, ?> runner,
      HashMap<String, Method<?, ?>> sharedMethods) {
    this.name = name;
    this.sig = sig;
    this.runner = runner;
    this.sharedMethods = sharedMethods;
  }

  public JavaComponent.HandlerSignature<?, ?> getSig() {
    return sig;
  }

  public BiFunction<WorkflowContext, ?, ?> getRunner() {
    return runner;
  }

  public Method<?, ?> getSharedMethod(String name) {
    return sharedMethods.get(name);
  }

  public Set<String> getSharedMethods() {
    return sharedMethods.keySet();
  }

  public String getName() {
    return name;
  }

  public String getSimpleName() {
    return name.substring(name.lastIndexOf(".") + 1);
  }

  public @Nullable String getPackageName() {
    int i = name.lastIndexOf(".");
    if (i < 0) {
      return null;
    }
    return name.substring(0, i);
  }

  @Override
  public List<BlockingComponent> components() {
    WorkflowMangledDescriptors workflowMangledDescriptors = WorkflowMangledDescriptors.mangle(this);

    return List.of(
        new WorkflowImpl(this, workflowMangledDescriptors),
        WorkflowManagerImpl.create(
            workflowMangledDescriptors.getOutputFileDescriptor(),
            workflowMangledDescriptors.getWorkflowManagerServiceSimpleName(),
            workflowMangledDescriptors.getWorkflowManagerServiceFqsn()));
  }

  public static <REQ, RES> Builder named(
      String name,
      JavaComponent.HandlerSignature<REQ, RES> sig,
      BiFunction<WorkflowContext, REQ, RES> runner) {
    return new Builder(name, sig, runner);
  }

  public static class Builder {
    private final String name;
    private final JavaComponent.HandlerSignature<?, ?> sig;
    private final BiFunction<WorkflowContext, ?, ?> runner;
    private final HashMap<String, Method<?, ?>> sharedMethods;

    <REQ, RES> Builder(
        String name,
        JavaComponent.HandlerSignature<REQ, RES> sig,
        BiFunction<WorkflowContext, REQ, RES> runner) {
      this.name = name;
      this.sig = sig;
      this.runner = runner;
      this.sharedMethods = new HashMap<>();
    }

    public <REQ, RES> Builder withShared(
        JavaComponent.HandlerSignature<REQ, RES> sig,
        BiFunction<WorkflowSharedContext, REQ, RES> runner) {
      this.sharedMethods.put(sig.getMethod(), new Method<>(sig, runner));
      return this;
    }

    public WorkflowComponentBundle build() {
      return new WorkflowComponentBundle(this.name, this.sig, this.runner, this.sharedMethods);
    }
  }

  @SuppressWarnings("unchecked")
  public static class Method<REQ, RES> {
    private final JavaComponent.HandlerSignature<REQ, RES> handlerSignature;

    private final BiFunction<Context, REQ, RES> runner;

    Method(
        JavaComponent.HandlerSignature<REQ, RES> handlerSignature,
        BiFunction<? extends Context, REQ, RES> runner) {
      this.handlerSignature = handlerSignature;
      this.runner = (BiFunction<Context, REQ, RES>) runner;
    }

    public JavaComponent.HandlerSignature<REQ, RES> getMethodSignature() {
      return handlerSignature;
    }

    public RES run(Context ctx, REQ req) {
      return runner.apply(ctx, req);
    }
  }
}
