// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine;

import dev.restate.sdk.core.generated.protocol.Protocol;
import java.util.Objects;

public class ServiceProtocol {
  public static final Protocol.ServiceProtocolVersion MIN_SERVICE_PROTOCOL_VERSION =
      Protocol.ServiceProtocolVersion.V4;
  public static final Protocol.ServiceProtocolVersion MAX_SERVICE_PROTOCOL_VERSION =
      Protocol.ServiceProtocolVersion.V4;

  static final String CONTENT_TYPE = "content-type";

  static Protocol.ServiceProtocolVersion parseServiceProtocolVersion(String version) {
    if (version == null) {
      return Protocol.ServiceProtocolVersion.SERVICE_PROTOCOL_VERSION_UNSPECIFIED;
    }
    version = version.trim();

    if (version.equals("application/vnd.restate.invocation.v1")) {
      return Protocol.ServiceProtocolVersion.V1;
    }
    if (version.equals("application/vnd.restate.invocation.v2")) {
      return Protocol.ServiceProtocolVersion.V2;
    }
    if (version.equals("application/vnd.restate.invocation.v3")) {
      return Protocol.ServiceProtocolVersion.V3;
    }
    if (version.equals("application/vnd.restate.invocation.v4")) {
      return Protocol.ServiceProtocolVersion.V4;
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
    if (Objects.requireNonNull(version) == Protocol.ServiceProtocolVersion.V3) {
      return "application/vnd.restate.invocation.v3";
    }
    if (Objects.requireNonNull(version) == Protocol.ServiceProtocolVersion.V4) {
      return "application/vnd.restate.invocation.v4";
    }
    throw new IllegalArgumentException(
        String.format("Service protocol version '%s' has no header value", version.getNumber()));
  }

  static boolean isSupported(Protocol.ServiceProtocolVersion serviceProtocolVersion) {
    return MIN_SERVICE_PROTOCOL_VERSION.getNumber() <= serviceProtocolVersion.getNumber()
        && serviceProtocolVersion.getNumber() <= MAX_SERVICE_PROTOCOL_VERSION.getNumber();
  }
}
