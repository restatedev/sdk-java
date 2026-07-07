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
  private final int eventLoops;

  @ConstructorBinding
  public RestateHttpServerProperties(
      @Name("port") @DefaultValue(value = "9080") int port,
      @Name("disableBidirectionalStreaming") @DefaultValue(value = "false")
          boolean disableBidirectionalStreaming,
      @Name("eventLoops") @DefaultValue(value = "0") int eventLoops) {
    this.port = port;
    this.disableBidirectionalStreaming = disableBidirectionalStreaming;
    this.eventLoops = eventLoops;
  }

  /** Port to expose the HTTP server. */
  public int getPort() {
    return port;
  }

  /**
   * Number of event loops to run, spreading incoming connections across that many CPU cores. When
   * {@code 0}, the default {@code 2 * availableProcessors} is used.
   */
  public int getEventLoops() {
    return eventLoops;
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
