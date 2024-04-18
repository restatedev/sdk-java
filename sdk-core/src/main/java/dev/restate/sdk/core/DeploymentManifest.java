// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.sdk.common.ServiceType;
import dev.restate.sdk.common.syscalls.ServiceDefinition;
import dev.restate.sdk.core.manifest.Component;
import dev.restate.sdk.core.manifest.DeploymentManifestSchema;
import dev.restate.sdk.core.manifest.Handler;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class DeploymentManifest {

  private final DeploymentManifestSchema manifest;

  public DeploymentManifest(
      DeploymentManifestSchema.ProtocolMode protocolMode, Stream<ServiceDefinition<?>> components) {
    this.manifest =
        new DeploymentManifestSchema()
            .withMinProtocolVersion(1)
            .withMaxProtocolVersion(1)
            .withProtocolMode(protocolMode)
            .withComponents(
                components
                    .map(
                        svc ->
                            new Component()
                                .withFullyQualifiedComponentName(svc.getServiceName())
                                .withComponentType(convertServiceType(svc.getServiceType()))
                                .withHandlers(
                                    svc.getHandlers().stream()
                                        .map(method -> new Handler().withName(method.getName()))
                                        .collect(Collectors.toList())))
                    .collect(Collectors.toList()));
  }

  public DeploymentManifestSchema manifest() {
    return this.manifest;
  }

  private static Component.ComponentType convertServiceType(ServiceType serviceType) {
    switch (serviceType) {
      case WORKFLOW:
      case SERVICE:
        return Component.ComponentType.SERVICE;
      case VIRTUAL_OBJECT:
        return Component.ComponentType.VIRTUAL_OBJECT;
    }
    throw new IllegalStateException();
  }
}
