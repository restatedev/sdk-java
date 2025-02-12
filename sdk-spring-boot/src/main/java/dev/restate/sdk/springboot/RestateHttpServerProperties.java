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

  @ConstructorBinding
  public RestateHttpServerProperties(@Name("port") @DefaultValue(value = "9080") int port) {
    this.port = port;
  }

  /** Port to expose the HTTP server. */
  public int getPort() {
    return port;
  }
}
