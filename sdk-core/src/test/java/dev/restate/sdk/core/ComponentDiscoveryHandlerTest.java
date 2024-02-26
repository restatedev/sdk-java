// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.restate.sdk.common.ComponentType;
import dev.restate.sdk.common.syscalls.ComponentDefinition;
import dev.restate.sdk.common.syscalls.ExecutorType;
import dev.restate.sdk.common.syscalls.HandlerDefinition;
import dev.restate.sdk.core.manifest.Component;
import dev.restate.sdk.core.manifest.DeploymentManifestSchema;
import dev.restate.sdk.core.manifest.DeploymentManifestSchema.ProtocolMode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ComponentDiscoveryHandlerTest {

  @Test
  void handleWithMultipleServices() {
    DeploymentManifest deploymentManifest =
        new DeploymentManifest(
            ProtocolMode.REQUEST_RESPONSE,
            Map.of(
                "MyGreeter",
                new ComponentDefinition(
                    "MyGreeter",
                    ExecutorType.BLOCKING,
                    ComponentType.SERVICE,
                    List.of(new HandlerDefinition("greet", null, null, null)))));

    DeploymentManifestSchema manifest = deploymentManifest.manifest();

    assertThat(manifest.getComponents())
        .extracting(Component::getFullyQualifiedComponentName)
        .containsOnly("MyGreeter");
    assertThat(manifest.getProtocolMode()).isEqualTo(ProtocolMode.REQUEST_RESPONSE);
  }
}
