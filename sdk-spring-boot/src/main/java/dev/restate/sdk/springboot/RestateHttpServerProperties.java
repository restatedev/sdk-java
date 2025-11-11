// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.Name;

@ConfigurationProperties(prefix = "restate.sdk.http")
public class RestateHttpServerProperties {

  private final @Nullable Integer port;
  private final String path;
  private final boolean disableBidirectionalStreaming;

  @ConstructorBinding
  public RestateHttpServerProperties(
      @Name("port") @Nullable Integer port,
      @Name("path") @DefaultValue(value = "/restate") String path,
      @Name("disableBidirectionalStreaming") @DefaultValue(value = "false")
          boolean disableBidirectionalStreaming) {
    this.port = port;
    this.path = path;
    this.disableBidirectionalStreaming = disableBidirectionalStreaming;
  }

  /**
   * Port to expose a separate HTTP server for the Restate endpoint. If not configured, the endpoint
   * will be integrated with Spring Boot's embedded server.
   */
  public @Nullable Integer getPort() {
    return port;
  }

  /**
   * Path to mount the Restate endpoint when using Spring Boot's embedded server. Only used when
   * port is not configured. Default is "/restate".
   */
  public String getPath() {
    return path;
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
