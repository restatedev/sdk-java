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

import dev.restate.sdk.core.impl.EndpointManifest;
import dev.restate.sdk.core.manifest.EndpointManifestSchema;
import dev.restate.sdk.core.manifest.Service;
import dev.restate.sdk.definition.HandlerDefinition;
import dev.restate.sdk.definition.HandlerSpecification;
import dev.restate.sdk.definition.HandlerType;
import dev.restate.sdk.definition.ServiceDefinition;
import dev.restate.sdk.definition.ServiceType;
import dev.restate.sdk.serde.Serde;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ComponentDiscoveryHandlerTest {

  @Test
  void handleWithMultipleServices() {
    EndpointManifest deploymentManifest =
        new EndpointManifest(
            EndpointManifestSchema.ProtocolMode.REQUEST_RESPONSE,
            Stream.of(
                ServiceDefinition.of(
                    "MyGreeter",
                    ServiceType.SERVICE,
                    List.of(
                        HandlerDefinition.of(
                            HandlerSpecification.of(
                                "greet", HandlerType.EXCLUSIVE, Serde.VOID, Serde.VOID),
                            null)))),
            false);

    EndpointManifestSchema manifest = deploymentManifest.manifest();

    assertThat(manifest.getServices()).extracting(Service::getName).containsOnly("MyGreeter");
    assertThat(manifest.getProtocolMode())
        .isEqualTo(EndpointManifestSchema.ProtocolMode.REQUEST_RESPONSE);
  }
}
