// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.dynrpc;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import dev.restate.sdk.dynrpc.template.generated.KeyedServiceGrpc;
import io.grpc.protobuf.ProtoFileDescriptorSupplier;
import io.grpc.protobuf.ProtoMethodDescriptorSupplier;
import io.grpc.protobuf.ProtoServiceDescriptorSupplier;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

class DescriptorUtils {

  private DescriptorUtils() {}

  static class AdapterServiceDescriptorSupplier
          implements ProtoFileDescriptorSupplier, ProtoServiceDescriptorSupplier {
    private final Descriptors.FileDescriptor desc;
    private final String serviceName;

    AdapterServiceDescriptorSupplier(Descriptors.FileDescriptor desc, String serviceName) {
      this.desc = desc;
      this.serviceName = serviceName;
    }

    @Override
    public Descriptors.FileDescriptor getFileDescriptor() {
      return desc;
    }

    @Override
    public Descriptors.ServiceDescriptor getServiceDescriptor() {
      return desc.findServiceByName(this.serviceName);
    }
  }

  static class AdapterMethodDescriptorSupplier extends AdapterServiceDescriptorSupplier
          implements ProtoMethodDescriptorSupplier {
    private final String methodName;

    AdapterMethodDescriptorSupplier(
            Descriptors.FileDescriptor desc, String serviceName, String methodName) {
      super(desc, serviceName);
      this.methodName = methodName;
    }

    @Override
    public Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  public static Descriptors.FileDescriptor mangle(String packageName, String simpleName, Set<String> methods, boolean isKeyed) {
    // This is the built-in workflow.proto descriptor
    var templateDescriptor =
        ((ProtoFileDescriptorSupplier)
                Objects.requireNonNull(KeyedServiceGrpc.getServiceDescriptor().getSchemaDescriptor()))
            .getFileDescriptor();
    var protoDescriptorBuilder =
        DescriptorProtos.FileDescriptorProto.newBuilder(templateDescriptor.toProto());

    // Set package name and file desc name
    if (packageName != null) {
      protoDescriptorBuilder.setName(
          packageName.replaceAll(Pattern.quote("."), "/") + "/dynrpc.proto");
      protoDescriptorBuilder.setPackage(packageName);
    } else {
      protoDescriptorBuilder.setName("dynrpc.proto");
      protoDescriptorBuilder.clearPackage();
    }

    // Mangle service descriptors
    DescriptorProtos.ServiceDescriptorProto templateServiceDescriptorProto = isKeyed ? protoDescriptorBuilder.getService(1) : protoDescriptorBuilder.getService(0);

        mangleServiceDescriptor(
                templateServiceDescriptorProto,
            protoDescriptorBuilder,
            simpleName,
            methods);

    Descriptors.FileDescriptor outputFileDescriptor;
    try {
      outputFileDescriptor =
          Descriptors.FileDescriptor.buildFrom(
              protoDescriptorBuilder.build(),
              templateDescriptor.getDependencies().toArray(new Descriptors.FileDescriptor[0]));
    } catch (Descriptors.DescriptorValidationException e) {
      throw new RuntimeException(e);
    }

    return outputFileDescriptor;
  }

  private static void mangleServiceDescriptor(
      DescriptorProtos.ServiceDescriptorProto serviceDescriptorProto,
      DescriptorProtos.FileDescriptorProto.Builder protoDescriptorBuilder,
      String prefix,
      Set<String> methods) {
    var serviceDescriptorBuilder = serviceDescriptorProto.toBuilder();

    serviceDescriptorBuilder.setName(prefix);

    // Unroll methods
    assert serviceDescriptorBuilder.getMethodCount() == 1;
    DescriptorProtos.MethodDescriptorProto invokeTemplateMethodDesc =
        serviceDescriptorBuilder.getMethod(0);
    serviceDescriptorBuilder.removeMethod(0);
    for (String method : methods) {
      serviceDescriptorBuilder.addMethod(invokeTemplateMethodDesc.toBuilder().setName(method));
    }

    // Update original descriptor builder
    protoDescriptorBuilder.clearService();
    protoDescriptorBuilder.setService(0, serviceDescriptorBuilder);
  }
}
