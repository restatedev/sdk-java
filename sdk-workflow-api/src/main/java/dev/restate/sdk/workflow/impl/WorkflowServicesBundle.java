// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.workflow.impl;

import static dev.restate.sdk.workflow.impl.DescriptorUtils.toMethodName;

import dev.restate.sdk.Context;
import dev.restate.sdk.common.BlockingService;
import dev.restate.sdk.common.Serde;
import dev.restate.sdk.common.ServicesBundle;
import dev.restate.sdk.workflow.WorkflowContext;
import dev.restate.sdk.workflow.WorkflowSharedContext;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

public class WorkflowServicesBundle implements ServicesBundle {
  private final String name;
  private final MethodSignature<?, ?> sig;
  private final BiFunction<WorkflowContext, ?, ?> runner;
  private final HashMap<String, Method<?, ?>> sharedMethods;

  public WorkflowServicesBundle(
      String name,
      MethodSignature<?, ?> sig,
      BiFunction<WorkflowContext, ?, ?> runner,
      HashMap<String, Method<?, ?>> sharedMethods) {
    this.name = name;
    this.sig = sig;
    this.runner = runner;
    this.sharedMethods = sharedMethods;
  }

  public MethodSignature<?, ?> getSig() {
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
  public List<BlockingService> services() {
    WorkflowMangledDescriptors workflowMangledDescriptors = WorkflowMangledDescriptors.mangle(this);

    return List.of(
        new WorkflowImpl(this, workflowMangledDescriptors),
        WorkflowManagerImpl.create(
            workflowMangledDescriptors.getOutputFileDescriptor(),
            workflowMangledDescriptors.getWorkflowManagerServiceSimpleName(),
            workflowMangledDescriptors.getWorkflowManagerServiceFqsn()));
  }

  public static <REQ, RES> Builder named(
      String name, MethodSignature<REQ, RES> sig, BiFunction<WorkflowContext, REQ, RES> runner) {
    return new Builder(name, sig, runner);
  }

  public static class Builder {
    private final String name;
    private final MethodSignature<?, ?> sig;
    private final BiFunction<WorkflowContext, ?, ?> runner;
    private final HashMap<String, Method<?, ?>> sharedMethods;

    <REQ, RES> Builder(
        String name, MethodSignature<REQ, RES> sig, BiFunction<WorkflowContext, REQ, RES> runner) {
      this.name = name;
      this.sig = sig;
      this.runner = runner;
      this.sharedMethods = new HashMap<>();
    }

    public <REQ, RES> Builder withShared(
        MethodSignature<REQ, RES> sig, BiFunction<WorkflowSharedContext, REQ, RES> runner) {
      this.sharedMethods.put(sig.getMethod(), new Method<>(sig, runner));
      return this;
    }

    public WorkflowServicesBundle build() {
      return new WorkflowServicesBundle(this.name, this.sig, this.runner, this.sharedMethods);
    }
  }

  @SuppressWarnings("unchecked")
  public static class Method<REQ, RES> {
    private final MethodSignature<REQ, RES> methodSignature;

    private final BiFunction<Context, REQ, RES> runner;

    Method(
        MethodSignature<REQ, RES> methodSignature, BiFunction<? extends Context, REQ, RES> runner) {
      this.methodSignature = methodSignature;
      this.runner = (BiFunction<Context, REQ, RES>) runner;
    }

    public MethodSignature<REQ, RES> getMethodSignature() {
      return methodSignature;
    }

    public RES run(Context ctx, REQ req) {
      return runner.apply(ctx, req);
    }
  }

  public static class MethodSignature<REQ, RES> {

    private final String method;
    private final Serde<REQ> requestSerde;
    private final Serde<RES> responseSerde;

    MethodSignature(String method, Serde<REQ> requestSerde, Serde<RES> responseSerde) {
      this.method = toMethodName(method);
      this.requestSerde = requestSerde;
      this.responseSerde = responseSerde;
    }

    public static <T, R> MethodSignature<T, R> of(
        String method, Serde<T> requestSerde, Serde<R> responseSerde) {
      return new MethodSignature<>(method, requestSerde, responseSerde);
    }

    public String getMethod() {
      return method;
    }

    public Serde<REQ> getRequestSerde() {
      return requestSerde;
    }

    public Serde<RES> getResponseSerde() {
      return responseSerde;
    }
  }
}
