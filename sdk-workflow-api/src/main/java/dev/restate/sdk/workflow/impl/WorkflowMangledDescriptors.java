// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.workflow.impl;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import dev.restate.sdk.workflow.template.generated.WorkflowGrpc;
import io.grpc.protobuf.ProtoFileDescriptorSupplier;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

class WorkflowMangledDescriptors {

  static final String MANAGER_SERVICE_SUFFIX = "InternalManager";

  private final Descriptors.FileDescriptor outputFileDescriptor;
  private final String workflowServiceFqsn;
  private final String workflowServiceSimpleName;
  private final String workflowManagerServiceFqsn;
  private final String workflowManagerServiceSimpleName;

  private WorkflowMangledDescriptors(
      Descriptors.FileDescriptor outputFileDescriptor,
      String workflowServiceFqsn,
      String workflowServiceSimpleName,
      String workflowManagerServiceFqsn,
      String workflowManagerServiceSimpleName) {
    this.outputFileDescriptor = outputFileDescriptor;
    this.workflowServiceFqsn = workflowServiceFqsn;
    this.workflowServiceSimpleName = workflowServiceSimpleName;
    this.workflowManagerServiceFqsn = workflowManagerServiceFqsn;
    this.workflowManagerServiceSimpleName = workflowManagerServiceSimpleName;
  }

  public Descriptors.FileDescriptor getOutputFileDescriptor() {
    return outputFileDescriptor;
  }

  public String getWorkflowServiceFqsn() {
    return workflowServiceFqsn;
  }

  public String getWorkflowServiceSimpleName() {
    return workflowServiceSimpleName;
  }

  public String getWorkflowManagerServiceFqsn() {
    return workflowManagerServiceFqsn;
  }

  public String getWorkflowManagerServiceSimpleName() {
    return workflowManagerServiceSimpleName;
  }

  public static WorkflowMangledDescriptors mangle(WorkflowComponentBundle workflowServicesBundle) {
    // This is the built-in workflow.proto descriptor
    var templateDescriptor =
        ((ProtoFileDescriptorSupplier)
                Objects.requireNonNull(WorkflowGrpc.getServiceDescriptor().getSchemaDescriptor()))
            .getFileDescriptor();
    var protoDescriptorBuilder =
        DescriptorProtos.FileDescriptorProto.newBuilder(templateDescriptor.toProto());

    // Set package name and file desc name
    String packageName = workflowServicesBundle.getPackageName();
    if (packageName != null) {
      protoDescriptorBuilder.setName(
          packageName.replaceAll(Pattern.quote("."), "/") + "/workflow.proto");
      protoDescriptorBuilder.setPackage(packageName);
    } else {
      protoDescriptorBuilder.setName("workflow.proto");
      protoDescriptorBuilder.clearPackage();
    }

    // Mangle service descriptors
    String workflowServiceSimpleName =
        mangleWorkflowDescriptor(
            protoDescriptorBuilder,
            workflowServicesBundle.getSimpleName(),
            workflowServicesBundle.getSharedMethods());
    String workflowManagerServiceSimpleName =
        mangleWorkflowManagerDescriptor(
            protoDescriptorBuilder, workflowServicesBundle.getSimpleName());

    String workflowServiceFqcn =
        packageName == null
            ? workflowServiceSimpleName
            : packageName + "." + workflowServiceSimpleName;
    String workflowManagerServiceFqcn =
        packageName == null
            ? workflowManagerServiceSimpleName
            : packageName + "." + workflowManagerServiceSimpleName;

    Descriptors.FileDescriptor outputFileDescriptor;
    try {
      outputFileDescriptor =
          Descriptors.FileDescriptor.buildFrom(
              protoDescriptorBuilder.build(),
              templateDescriptor.getDependencies().toArray(new Descriptors.FileDescriptor[0]));
    } catch (Descriptors.DescriptorValidationException e) {
      throw new RuntimeException(e);
    }

    return new WorkflowMangledDescriptors(
        outputFileDescriptor,
        workflowServiceFqcn,
        workflowServiceSimpleName,
        workflowManagerServiceFqcn,
        workflowManagerServiceSimpleName);
  }

  private static String mangleWorkflowDescriptor(
      DescriptorProtos.FileDescriptorProto.Builder protoDescriptorBuilder,
      String newSimpleName,
      Set<String> methods) {
    var serviceDescriptorBuilder = protoDescriptorBuilder.getServiceBuilder(0);

    // Prefix service name
    serviceDescriptorBuilder.setName(newSimpleName);

    // Unroll methods
    assert serviceDescriptorBuilder.getMethodCount() == 3;
    DescriptorProtos.MethodDescriptorProto invokeTemplateMethodDesc =
        serviceDescriptorBuilder.getMethod(2);
    serviceDescriptorBuilder.removeMethod(2);
    for (String method : methods) {
      serviceDescriptorBuilder.addMethod(invokeTemplateMethodDesc.toBuilder().setName(method));
    }

    // Update original descriptor builder
    protoDescriptorBuilder.setService(0, serviceDescriptorBuilder);

    return newSimpleName;
  }

  private static String mangleWorkflowManagerDescriptor(
      DescriptorProtos.FileDescriptorProto.Builder protoDescriptorBuilder, String prefix) {
    // Prefix service name
    String newServiceName = prefix + MANAGER_SERVICE_SUFFIX;
    protoDescriptorBuilder.setService(
        1, protoDescriptorBuilder.getServiceBuilder(1).setName(newServiceName));

    return newServiceName;
  }
}
