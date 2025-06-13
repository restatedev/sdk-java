// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import dev.restate.sdk.core.generated.discovery.Discovery;
import dev.restate.sdk.core.generated.manifest.EndpointManifestSchema;
import dev.restate.sdk.core.generated.manifest.Handler;
import dev.restate.sdk.core.generated.manifest.Service;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class DiscoveryProtocol {
  static final Discovery.ServiceDiscoveryProtocolVersion MIN_SERVICE_DISCOVERY_PROTOCOL_VERSION =
      Discovery.ServiceDiscoveryProtocolVersion.V1;
  static final Discovery.ServiceDiscoveryProtocolVersion MAX_SERVICE_DISCOVERY_PROTOCOL_VERSION =
      Discovery.ServiceDiscoveryProtocolVersion.V3;

  static boolean isSupported(
      Discovery.ServiceDiscoveryProtocolVersion serviceDiscoveryProtocolVersion) {
    return MIN_SERVICE_DISCOVERY_PROTOCOL_VERSION.getNumber()
            <= serviceDiscoveryProtocolVersion.getNumber()
        && serviceDiscoveryProtocolVersion.getNumber()
            <= MAX_SERVICE_DISCOVERY_PROTOCOL_VERSION.getNumber();
  }

  /**
   * Selects the highest supported service protocol version from a list of supported versions.
   *
   * @param acceptedVersionsString A comma-separated list of accepted service protocol versions.
   * @return The highest supported service protocol version, otherwise
   *     Protocol.ServiceProtocolVersion.SERVICE_PROTOCOL_VERSION_UNSPECIFIED
   */
  static Discovery.ServiceDiscoveryProtocolVersion selectSupportedServiceDiscoveryProtocolVersion(
      String acceptedVersionsString) {
    // assume V1 in case nothing was set
    if (acceptedVersionsString == null || acceptedVersionsString.isEmpty()) {
      return Discovery.ServiceDiscoveryProtocolVersion.V1;
    }

    final String[] supportedVersions = acceptedVersionsString.split(",");

    Discovery.ServiceDiscoveryProtocolVersion maxVersion =
        Discovery.ServiceDiscoveryProtocolVersion.SERVICE_DISCOVERY_PROTOCOL_VERSION_UNSPECIFIED;

    for (String versionString : supportedVersions) {
      final Optional<Discovery.ServiceDiscoveryProtocolVersion> optionalVersion =
          parseServiceDiscoveryProtocolVersion(versionString);

      if (optionalVersion.isPresent()) {
        final Discovery.ServiceDiscoveryProtocolVersion version = optionalVersion.get();
        if (isSupported(version) && version.getNumber() > maxVersion.getNumber()) {
          maxVersion = version;
        }
      }
    }

    return maxVersion;
  }

  static Optional<Discovery.ServiceDiscoveryProtocolVersion> parseServiceDiscoveryProtocolVersion(
      String versionString) {
    versionString = versionString.trim();

    if (versionString.equals("application/vnd.restate.endpointmanifest.v1+json")) {
      return Optional.of(Discovery.ServiceDiscoveryProtocolVersion.V1);
    }
    if (versionString.equals("application/vnd.restate.endpointmanifest.v2+json")) {
      return Optional.of(Discovery.ServiceDiscoveryProtocolVersion.V2);
    }
    if (versionString.equals("application/vnd.restate.endpointmanifest.v3+json")) {
      return Optional.of(Discovery.ServiceDiscoveryProtocolVersion.V3);
    }
    return Optional.empty();
  }

  static String serviceDiscoveryProtocolVersionToHeaderValue(
      Discovery.ServiceDiscoveryProtocolVersion version) {
    if (Objects.requireNonNull(version) == Discovery.ServiceDiscoveryProtocolVersion.V1) {
      return "application/vnd.restate.endpointmanifest.v1+json";
    }
    if (Objects.requireNonNull(version) == Discovery.ServiceDiscoveryProtocolVersion.V2) {
      return "application/vnd.restate.endpointmanifest.v2+json";
    }
    if (Objects.requireNonNull(version) == Discovery.ServiceDiscoveryProtocolVersion.V3) {
      return "application/vnd.restate.endpointmanifest.v3+json";
    }
    throw new IllegalArgumentException(
        String.format(
            "Service discovery protocol version '%s' has no header value", version.getNumber()));
  }

  static final ObjectMapper MANIFEST_OBJECT_MAPPER = new ObjectMapper();

  static final Set<String> DISCOVERY_FIELDS_ADDED_IN_V2 = Set.of("documentation", "metadata");
  static final Set<String> DISCOVERY_FIELDS_ADDED_IN_V3 =
      Set.of(
          "inactivityTimeout",
          "abortTimeout",
          "journalRetention",
          "idempotencyRetention",
          "workflowCompletionRetention",
          "enableLazyState",
          "ingressPrivate");

  @JsonFilter("DiscoveryFieldsFilter")
  interface FieldsMixin {}

  static {
    // Mixin to add fields filter, used to filter v2 fields
    MANIFEST_OBJECT_MAPPER.addMixIn(Service.class, FieldsMixin.class);
    MANIFEST_OBJECT_MAPPER.addMixIn(Handler.class, FieldsMixin.class);
  }

  static byte[] serializeManifest(
      Discovery.ServiceDiscoveryProtocolVersion serviceDiscoveryProtocolVersion,
      EndpointManifestSchema response)
      throws ProtocolException {
    try {
      SimpleBeanPropertyFilter filter;
      if (serviceDiscoveryProtocolVersion == Discovery.ServiceDiscoveryProtocolVersion.V1) {
        filter =
            SimpleBeanPropertyFilter.serializeAllExcept(
                Stream.concat(
                        DISCOVERY_FIELDS_ADDED_IN_V2.stream(),
                        DISCOVERY_FIELDS_ADDED_IN_V3.stream())
                    .collect(Collectors.toSet()));
      } else if (serviceDiscoveryProtocolVersion == Discovery.ServiceDiscoveryProtocolVersion.V2) {
        filter = SimpleBeanPropertyFilter.serializeAllExcept(DISCOVERY_FIELDS_ADDED_IN_V3);
      } else {
        filter = SimpleBeanPropertyFilter.serializeAll();
      }

      return MANIFEST_OBJECT_MAPPER
          .writer(new SimpleFilterProvider().addFilter("DiscoveryFieldsFilter", filter))
          .writeValueAsBytes(response);
    } catch (JsonProcessingException e) {
      throw new ProtocolException(
          "Error when serializing the manifest", ProtocolException.INTERNAL_CODE, e);
    }
  }
}
