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

import com.fasterxml.jackson.databind.JsonNode;
import dev.restate.sdk.core.generated.manifest.EndpointManifestSchema;
import dev.restate.sdk.core.generated.manifest.Service;
import dev.restate.sdk.endpoint.definition.HandlerDefinition;
import dev.restate.sdk.endpoint.definition.HandlerType;
import dev.restate.sdk.endpoint.definition.ServiceDefinition;
import dev.restate.sdk.endpoint.definition.ServiceType;
import dev.restate.serde.Serde;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class ComponentDiscoveryHandlerTest {

  @Test
  void handleWithMultipleServices() {
    EndpointManifest deploymentManifest =
        new EndpointManifest(
            Stream.of(
                ServiceDefinition.of(
                    "MyGreeter",
                    ServiceType.SERVICE,
                    List.of(
                        HandlerDefinition.of(
                            "greet", HandlerType.EXCLUSIVE, Serde.VOID, Serde.VOID, null)))),
            false);

    EndpointManifestSchema manifest =
        deploymentManifest.manifest(
            DiscoveryProtocol.Version.MAX, EndpointManifestSchema.ProtocolMode.REQUEST_RESPONSE);

    assertThat(manifest.getServices()).extracting(Service::getName).containsOnly("MyGreeter");
    assertThat(manifest.getProtocolMode())
        .isEqualTo(EndpointManifestSchema.ProtocolMode.REQUEST_RESPONSE);
  }

  @Test
  void negotiateDiscoveryVersion() {
    // Runtimes send all the versions they support, comma separated
    assertThat(
            DiscoveryProtocol.selectSupportedServiceDiscoveryProtocolVersion(
                "application/vnd.restate.endpointmanifest.v4+json, "
                    + "application/vnd.restate.endpointmanifest.v5+json, "
                    + "application/vnd.restate.endpointmanifest.v6+json"))
        .isEqualTo(DiscoveryProtocol.Version.V6)
        .isEqualTo(DiscoveryProtocol.Version.MAX)
        .extracting(DiscoveryProtocol.Version::getHeader)
        .isEqualTo("application/vnd.restate.endpointmanifest.v6+json");
    assertThat(
            DiscoveryProtocol.selectSupportedServiceDiscoveryProtocolVersion(
                "application/vnd.restate.endpointmanifest.v4+json, "
                    + "application/vnd.restate.endpointmanifest.v5+json"))
        .isEqualTo(DiscoveryProtocol.Version.V4);
  }

  @Test
  void preemptableIsEmittedOnlyWhenSetAndSupported() throws Exception {
    EndpointManifest endpointManifest =
        new EndpointManifest(
            Stream.of(
                ServiceDefinition.of(
                        "NonPreemptable",
                        ServiceType.SERVICE,
                        List.of(
                            HandlerDefinition.of(
                                "overridden", HandlerType.EXCLUSIVE, Serde.VOID, Serde.VOID, null),
                            HandlerDefinition.of(
                                "inherited", HandlerType.EXCLUSIVE, Serde.VOID, Serde.VOID, null)))
                    .configure(
                        c ->
                            c.preemptable(false)
                                .configureHandler("overridden", h -> h.preemptable(true))),
                ServiceDefinition.of(
                    "Default",
                    ServiceType.SERVICE,
                    List.of(
                        HandlerDefinition.of(
                            "greet", HandlerType.EXCLUSIVE, Serde.VOID, Serde.VOID, null)))),
            false);

    // V6: emitted only where explicitly set
    JsonNode v6 = serialize(endpointManifest, DiscoveryProtocol.Version.V6);
    JsonNode nonPreemptable = findByName(v6.get("services"), "NonPreemptable");
    assertThat(nonPreemptable.get("preemptable").asBoolean(true)).isFalse();
    assertThat(
            findByName(nonPreemptable.get("handlers"), "overridden").get("preemptable").asBoolean())
        .isTrue();
    assertThat(findByName(nonPreemptable.get("handlers"), "inherited").has("preemptable"))
        .isFalse();
    JsonNode defaultService = findByName(v6.get("services"), "Default");
    assertThat(defaultService.has("preemptable")).isFalse();
    assertThat(findByName(defaultService.get("handlers"), "greet").has("preemptable")).isFalse();

    // Below V6: silently dropped
    for (DiscoveryProtocol.Version version :
        List.of(DiscoveryProtocol.Version.V3, DiscoveryProtocol.Version.V4)) {
      JsonNode manifest = serialize(endpointManifest, version);
      assertThat(manifest.findValues("preemptable")).as("version %s", version).isEmpty();
      assertThat(findByName(manifest.get("services"), "NonPreemptable").get("handlers"))
          .as("version %s", version)
          .hasSize(2);
    }
  }

  private static JsonNode serialize(
      EndpointManifest endpointManifest, DiscoveryProtocol.Version version) throws Exception {
    return DiscoveryProtocol.MANIFEST_OBJECT_MAPPER.readTree(
        DiscoveryProtocol.serializeManifest(
            version,
            endpointManifest.manifest(
                version, EndpointManifestSchema.ProtocolMode.REQUEST_RESPONSE)));
  }

  private static JsonNode findByName(JsonNode array, String name) {
    return StreamSupport.stream(array.spliterator(), false)
        .filter(n -> name.equals(n.get("name").asText()))
        .findFirst()
        .orElseThrow();
  }
}
