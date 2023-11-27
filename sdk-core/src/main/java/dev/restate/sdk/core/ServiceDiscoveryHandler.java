// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import dev.restate.generated.service.discovery.Discovery;
import io.grpc.ServerServiceDefinition;
import io.grpc.protobuf.ProtoFileDescriptorSupplier;
import java.util.*;
import java.util.stream.Collectors;

final class ServiceDiscoveryHandler {

  private final Discovery.ServiceDiscoveryResponse response;

  public ServiceDiscoveryHandler(
      Discovery.ProtocolMode protocolMode, Map<String, ServerServiceDefinition> services) {
    // Collect set of files
    Set<DescriptorProtos.FileDescriptorProto> fileDescriptors =
        resolveDescriptors(
            services.values().stream()
                .map(
                    serverServiceDefinition -> {
                      if (serverServiceDefinition.getServiceDescriptor().getSchemaDescriptor()
                          instanceof ProtoFileDescriptorSupplier) {
                        return (ProtoFileDescriptorSupplier)
                            serverServiceDefinition.getServiceDescriptor().getSchemaDescriptor();
                      } else {
                        throw new IllegalStateException(
                            "Cannot retrieve file descriptor for service "
                                + serverServiceDefinition.getServiceDescriptor().getName()
                                + ". Make sure you're not using protobuf lite when compiling your schemas.");
                      }
                    })
                .map(ProtoFileDescriptorSupplier::getFileDescriptor)
                .collect(Collectors.toList()));

    this.response =
        Discovery.ServiceDiscoveryResponse.newBuilder()
            .setFiles(DescriptorProtos.FileDescriptorSet.newBuilder().addAllFile(fileDescriptors))
            .addAllServices(services.keySet())
            .setProtocolMode(protocolMode)
            .build();
  }

  public Discovery.ServiceDiscoveryResponse handle(Discovery.ServiceDiscoveryRequest request) {
    return this.response;
  }

  private static Set<DescriptorProtos.FileDescriptorProto> resolveDescriptors(
      Collection<Descriptors.FileDescriptor> initialDescriptors) {
    // Note: there shouldn't be any circular dependencies, but better to shield ourselves from such
    //  a case using an algorithm that doesn't stack overflow in this case.
    //  https://groups.google.com/g/protobuf/c/cJPigORiZmc?pli=1
    Queue<Descriptors.FileDescriptor> queue = new ArrayDeque<>(initialDescriptors);
    Set<DescriptorProtos.FileDescriptorProto> results = new HashSet<>();

    while (!queue.isEmpty()) {
      final Descriptors.FileDescriptor newFileDescriptor = queue.poll();

      if (results.add(newFileDescriptor.toProto())) {
        newFileDescriptor.getDependencies().forEach(queue::offer);
      }
    }

    return results;
  }
}
