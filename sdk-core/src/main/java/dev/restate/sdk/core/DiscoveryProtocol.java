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
import dev.restate.sdk.core.generated.manifest.EndpointManifestSchema;
import dev.restate.sdk.core.generated.manifest.Handler;
import dev.restate.sdk.core.generated.manifest.Service;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DiscoveryProtocol {
  public enum Version {
    V1("application/vnd.restate.endpointmanifest.v1+json"),
    V2("application/vnd.restate.endpointmanifest.v2+json"),
    V3("application/vnd.restate.endpointmanifest.v3+json"),
    V4("application/vnd.restate.endpointmanifest.v4+json");

    private final String header;

    Version(String header) {
      this.header = header;
    }

    public String getHeader() {
      return header;
    }

    public int getNumber() {
      return ordinal() + 1;
    }

    public boolean isSupported() {
      // We support all versions so far
      return true;
    }

    public static final Version MIN = Version.V1;
    public static final Version MAX = Version.V4;

    public static Optional<Version> fromHeader(String headerValue) {
      String trimmed = headerValue.trim();
      return Stream.of(values())
          .filter(version -> version.header.equalsIgnoreCase(trimmed))
          .findFirst();
    }
  }

  /**
   * Selects the highest supported service protocol version from a list of supported versions.
   *
   * @param acceptedVersionsString A comma-separated list of accepted service protocol versions.
   * @return The highest supported service protocol version, otherwise
   *     Protocol.ServiceProtocolVersion.SERVICE_PROTOCOL_VERSION_UNSPECIFIED
   */
  static Version selectSupportedServiceDiscoveryProtocolVersion(String acceptedVersionsString) {
    // assume V1 in case nothing was set
    if (acceptedVersionsString == null || acceptedVersionsString.isEmpty()) {
      return Version.V1;
    }

    final String[] supportedVersions = acceptedVersionsString.split(",");

    Version maxVersion = null;

    for (String versionString : supportedVersions) {
      final Optional<Version> optionalVersion = Version.fromHeader(versionString);

      if (optionalVersion.isPresent()) {
        final Version version = optionalVersion.get();
        if (version.isSupported()
            && (maxVersion == null || version.getNumber() > maxVersion.getNumber())) {
          maxVersion = version;
        }
      }
    }

    if (Objects.isNull(maxVersion)) {
      throw new ProtocolException(
          String.format(
              "Unsupported Discovery version in the Accept header '%s'", acceptedVersionsString),
          ProtocolException.UNSUPPORTED_MEDIA_TYPE_CODE);
    }

    return maxVersion;
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
  static final Set<String> DISCOVERY_FIELDS_ADDED_IN_V4 =
      Set.of(
          "retryPolicyInitialInterval",
          "retryPolicyMaxInterval",
          "retryPolicyMaxAttempts",
          "retryPolicyExponentiationFactor",
          "retryPolicyOnMaxAttempts");

  @JsonFilter("DiscoveryFieldsFilter")
  interface FieldsMixin {}

  static {
    // Mixin to add fields filter, used to filter v2 fields
    MANIFEST_OBJECT_MAPPER.addMixIn(Service.class, FieldsMixin.class);
    MANIFEST_OBJECT_MAPPER.addMixIn(Handler.class, FieldsMixin.class);
  }

  static byte[] serializeManifest(
      Version serviceDiscoveryProtocolVersion, EndpointManifestSchema response)
      throws ProtocolException {
    try {
      SimpleBeanPropertyFilter filter;
      if (serviceDiscoveryProtocolVersion == Version.V1) {
        filter =
            SimpleBeanPropertyFilter.serializeAllExcept(
                Stream.concat(
                        Stream.concat(
                            DISCOVERY_FIELDS_ADDED_IN_V2.stream(),
                            DISCOVERY_FIELDS_ADDED_IN_V3.stream()),
                        DISCOVERY_FIELDS_ADDED_IN_V4.stream())
                    .collect(Collectors.toSet()));
      } else if (serviceDiscoveryProtocolVersion == Version.V2) {
        filter =
            SimpleBeanPropertyFilter.serializeAllExcept(
                Stream.concat(
                        DISCOVERY_FIELDS_ADDED_IN_V3.stream(),
                        DISCOVERY_FIELDS_ADDED_IN_V4.stream())
                    .collect(Collectors.toSet()));
      } else if (serviceDiscoveryProtocolVersion == Version.V3) {
        filter = SimpleBeanPropertyFilter.serializeAllExcept(DISCOVERY_FIELDS_ADDED_IN_V4);
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
