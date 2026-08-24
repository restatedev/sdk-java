// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.integration;

import java.net.URI;
import org.jspecify.annotations.Nullable;

/** Where to reach the Restate ingestion gRPC endpoint, parsed from an http(s) URL. */
final class IngressEndpoint {

  final String host;
  final int port;
  final boolean tls;

  private IngressEndpoint(String host, int port, boolean tls) {
    this.host = host;
    this.port = port;
    this.tls = tls;
  }

  /**
   * Parse a single ingress URL. {@code https} selects TLS; the port defaults to 443 (TLS) or 80
   * otherwise.
   */
  static IngressEndpoint parse(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("ingress url must not be empty");
    }
    URI uri;
    try {
      uri = new URI(raw.trim());
    } catch (Exception e) {
      throw new IllegalArgumentException("ingress url is not a valid URL: '" + raw + "'", e);
    }
    boolean tls;
    @Nullable String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase();
    if ("https".equals(scheme)) {
      tls = true;
    } else if ("http".equals(scheme)) {
      tls = false;
    } else {
      throw new IllegalArgumentException(
          "ingress url must use http or https scheme, got '"
              + uri.getScheme()
              + "' in '"
              + raw
              + "'");
    }
    @Nullable String host = uri.getHost();
    if (host == null) {
      throw new IllegalArgumentException("ingress url has no host: '" + raw + "'");
    }
    int port = uri.getPort() != -1 ? uri.getPort() : (tls ? 443 : 80);
    return new IngressEndpoint(host, port, tls);
  }
}
