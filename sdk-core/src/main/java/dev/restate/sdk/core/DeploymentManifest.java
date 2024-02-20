// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.sdk.annotation.ServiceType;
import dev.restate.sdk.common.syscalls.ServiceDefinition;
import dev.restate.sdk.core.manifest.DeploymentManifestSchema;
import dev.restate.sdk.core.manifest.Method;
import dev.restate.sdk.core.manifest.Service;
import java.util.*;
import java.util.stream.Collectors;

final class DeploymentManifest {

  private final DeploymentManifestSchema manifest;

  public DeploymentManifest(
      DeploymentManifestSchema.ProtocolMode protocolMode, Map<String, ServiceDefinition> services) {
    this.manifest =
        new DeploymentManifestSchema()
            .withMinProtocolVersion(1)
            .withMaxProtocolVersion(1)
            .withProtocolMode(protocolMode)
            .withServices(
                services.values().stream()
                    .map(
                        svc ->
                            new Service()
                                .withFullyQualifiedServiceName(svc.getFullyQualifiedServiceName())
                                .withServiceType(convertServiceType(svc.getServiceType()))
                                .withMethods(
                                    svc.getMethods().stream()
                                        .map(
                                            method ->
                                                new Method()
                                                    .withName(method.getName())
                                                    .withInputSchema(method.getInputSchema())
                                                    .withOutputSchema(method.getOutputSchema()))
                                        .collect(Collectors.toList())))
                    .collect(Collectors.toList()));
  }

  public DeploymentManifestSchema manifest() {
    return this.manifest;
  }

  private static Service.ServiceType convertServiceType(ServiceType serviceType) {
    switch (serviceType) {
      case WORKFLOW:
        return Service.ServiceType.WORKFLOW;
      case OBJECT:
        return Service.ServiceType.KEYED;
      case STATELESS:
        return Service.ServiceType.UNKEYED;
    }
    throw new IllegalStateException();
  }
}
