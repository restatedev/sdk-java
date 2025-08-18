// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.Name;

@ConfigurationProperties(prefix = "restate.sdk.http")
public class RestateHttpServerProperties {

  private final int port;
  private final boolean disableBidirectionalStreaming;

  @ConstructorBinding
  public RestateHttpServerProperties(
      @Name("port") @DefaultValue(value = "9080") int port,
      @Name("disableBidirectionalStreaming") @DefaultValue(value = "false")
          boolean disableBidirectionalStreaming) {
    this.port = port;
    this.disableBidirectionalStreaming = disableBidirectionalStreaming;
  }

  /** Port to expose the HTTP server. */
  public int getPort() {
    return port;
  }

  /**
   * If true, disable bidirectional streaming with HTTP/2 requests. Restate initiates for each
   * invocation a bidirectional streaming using HTTP/2 between restate-server and the SDK. In some
   * network setups, for example when using a load balancers that buffer request/response,
   * bidirectional streaming will not work correctly. Only in these scenarios, we suggest disabling
   * bidirectional streaming.
   */
  public boolean isDisableBidirectionalStreaming() {
    return disableBidirectionalStreaming;
  }
}
