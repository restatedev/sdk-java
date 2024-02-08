// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.workflow.impl;

import com.google.protobuf.Descriptors;
import io.grpc.protobuf.ProtoFileDescriptorSupplier;
import io.grpc.protobuf.ProtoMethodDescriptorSupplier;
import io.grpc.protobuf.ProtoServiceDescriptorSupplier;

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

  static String toMethodName(String methodName) {
    return Character.toString(Character.toUpperCase(methodName.codePointAt(0)))
        + methodName.substring(1);
  }
}
