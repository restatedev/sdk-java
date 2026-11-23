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
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class DiscoveryProtocol {
  public enum Version {
    V1("application/vnd.restate.endpointmanifest.v1+json"),
    V2("application/vnd.restate.endpointmanifest.v2+json"),
    V3("application/vnd.restate.endpointmanifest.v3+json"),
    V4("application/vnd.restate.endpointmanifest.v4+json"),
    // V5 adds eagerStateKeysWhitelist, which this SDK doesn't implement. It's not supported, so
    // runtimes offering at most V5 keep negotiating V4. Declared because getNumber() is ordinal.
    V5("application/vnd.restate.endpointmanifest.v5+json"),
    V6("application/vnd.restate.endpointmanifest.v6+json");

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
      return this != V5;
    }

    public static final Version MIN = Version.V1;
    public static final Version MAX = Version.V6;

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
  // Runtimes older than V6 never preempt invocations, so dropping this field for them is safe.
  static final Set<String> DISCOVERY_FIELDS_ADDED_IN_V6 = Set.of("preemptable");

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
      int version = serviceDiscoveryProtocolVersion.getNumber();
      Set<String> excludedFields = new HashSet<>();
      if (version < Version.V2.getNumber()) {
        excludedFields.addAll(DISCOVERY_FIELDS_ADDED_IN_V2);
      }
      if (version < Version.V3.getNumber()) {
        excludedFields.addAll(DISCOVERY_FIELDS_ADDED_IN_V3);
      }
      if (version < Version.V4.getNumber()) {
        excludedFields.addAll(DISCOVERY_FIELDS_ADDED_IN_V4);
      }
      if (version < Version.V6.getNumber()) {
        excludedFields.addAll(DISCOVERY_FIELDS_ADDED_IN_V6);
      }
      SimpleBeanPropertyFilter filter =
          excludedFields.isEmpty()
              ? SimpleBeanPropertyFilter.serializeAll()
              : SimpleBeanPropertyFilter.serializeAllExcept(excludedFields);

      return MANIFEST_OBJECT_MAPPER
          .writer(new SimpleFilterProvider().addFilter("DiscoveryFieldsFilter", filter))
          .writeValueAsBytes(response);
    } catch (JsonProcessingException e) {
      throw new ProtocolException(
          "Error when serializing the manifest", ProtocolException.INTERNAL_CODE, e);
    }
  }
}
