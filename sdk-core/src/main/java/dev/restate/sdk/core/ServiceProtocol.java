// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.restate.generated.service.discovery.Discovery;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.manifest.EndpointManifestSchema;
import java.util.Objects;
import java.util.Optional;

class ServiceProtocol {
  static final Protocol.ServiceProtocolVersion MIN_SERVICE_PROTOCOL_VERSION =
      Protocol.ServiceProtocolVersion.V1;
  private static final Protocol.ServiceProtocolVersion MAX_SERVICE_PROTOCOL_VERSION =
      Protocol.ServiceProtocolVersion.V1;

  static final Discovery.ServiceDiscoveryProtocolVersion MIN_SERVICE_DISCOVERY_PROTOCOL_VERSION =
      Discovery.ServiceDiscoveryProtocolVersion.V1;
  static final Discovery.ServiceDiscoveryProtocolVersion MAX_SERVICE_DISCOVERY_PROTOCOL_VERSION =
      Discovery.ServiceDiscoveryProtocolVersion.V1;

  static Protocol.ServiceProtocolVersion parseServiceProtocolVersion(String version) {
    version = version.trim();

    if (version.equals("application/vnd.restate.invocation.v1")) {
      return Protocol.ServiceProtocolVersion.V1;
    }
    if (version.equals("application/vnd.restate.invocation.v2")) {
      return Protocol.ServiceProtocolVersion.V2;
    }
    return Protocol.ServiceProtocolVersion.SERVICE_PROTOCOL_VERSION_UNSPECIFIED;
  }

  static String serviceProtocolVersionToHeaderValue(Protocol.ServiceProtocolVersion version) {
    if (Objects.requireNonNull(version) == Protocol.ServiceProtocolVersion.V1) {
      return "application/vnd.restate.invocation.v1";
    }
    if (Objects.requireNonNull(version) == Protocol.ServiceProtocolVersion.V2) {
      return "application/vnd.restate.invocation.v2";
    }
    throw new IllegalArgumentException(
        String.format("Service protocol version '%s' has no header value", version.getNumber()));
  }

  static Protocol.ServiceProtocolVersion maxServiceProtocolVersion(
      boolean experimentalContextEnabled) {
    return experimentalContextEnabled
        ? Protocol.ServiceProtocolVersion.V2
        : Protocol.ServiceProtocolVersion.V1;
  }

  static boolean isSupported(
      Protocol.ServiceProtocolVersion serviceProtocolVersion, boolean experimentalContextEnabled) {
    return MIN_SERVICE_PROTOCOL_VERSION.getNumber() <= serviceProtocolVersion.getNumber()
        && serviceProtocolVersion.getNumber()
            <= maxServiceProtocolVersion(experimentalContextEnabled).getNumber();
  }

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
    return Optional.empty();
  }

  static String serviceDiscoveryProtocolVersionToHeaderValue(
      Discovery.ServiceDiscoveryProtocolVersion version) {
    if (Objects.requireNonNull(version) == Discovery.ServiceDiscoveryProtocolVersion.V1) {
      return "application/vnd.restate.endpointmanifest.v1+json";
    }
    throw new IllegalArgumentException(
        String.format(
            "Service discovery protocol version '%s' has no header value", version.getNumber()));
  }

  private static final ObjectMapper MANIFEST_OBJECT_MAPPER = new ObjectMapper();

  static byte[] serializeManifest(
      Discovery.ServiceDiscoveryProtocolVersion serviceDiscoveryProtocolVersion,
      EndpointManifestSchema response)
      throws ProtocolException {
    if (serviceDiscoveryProtocolVersion == Discovery.ServiceDiscoveryProtocolVersion.V1) {
      try {
        return MANIFEST_OBJECT_MAPPER.writeValueAsBytes(response);
      } catch (JsonProcessingException e) {
        throw new ProtocolException(
            "Error when serializing the manifest", ProtocolException.INTERNAL_CODE, e);
      }
    }

    throw new ProtocolException(
        String.format(
            "DiscoveryResponseSerializer does not support service discovery protocol '%s'",
            serviceDiscoveryProtocolVersion.getNumber()),
        ProtocolException.UNSUPPORTED_MEDIA_TYPE_CODE);
  }
}
