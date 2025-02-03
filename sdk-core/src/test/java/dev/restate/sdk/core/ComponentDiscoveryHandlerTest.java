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

import dev.restate.sdk.core.generated.manifest.EndpointManifestSchema;
import dev.restate.sdk.core.generated.manifest.Service;
import dev.restate.sdk.endpoint.definition.HandlerDefinition;
import dev.restate.sdk.endpoint.definition.HandlerType;
import dev.restate.sdk.endpoint.definition.ServiceDefinition;
import dev.restate.sdk.endpoint.definition.ServiceType;
import java.util.List;
import java.util.stream.Stream;

import dev.restate.serde.Serde;
import org.junit.jupiter.api.Test;

class ComponentDiscoveryHandlerTest {

  @Test
  void handleWithMultipleServices() {
    EndpointManifestSchema deploymentManifest =
        new EndpointManifest(
            EndpointManifestSchema.ProtocolMode.REQUEST_RESPONSE,
            Stream.of(
                ServiceDefinition.of(
                    "MyGreeter",
                    ServiceType.SERVICE,
                    List.of(
                        HandlerDefinition.of(
                                "greet", HandlerType.EXCLUSIVE, Serde.VOID, Serde.VOID,
                            null)))),
            false);

    EndpointManifestSchema manifest = deploymentManifest.manifest();

    assertThat(manifest.getServices()).extracting(Service::getName).containsOnly("MyGreeter");
    assertThat(manifest.getProtocolMode())
        .isEqualTo(EndpointManifestSchema.ProtocolMode.REQUEST_RESPONSE);
  }
}
