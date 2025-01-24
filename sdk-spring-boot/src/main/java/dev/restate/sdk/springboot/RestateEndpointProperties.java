// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot;

import dev.restate.sdk.endpoint.RequestIdentityVerifier;
import dev.restate.sdk.http.vertx.RestateHttpEndpointBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "restate.sdk")
public class RestateEndpointProperties {

  private final boolean enablePreviewContext;
  private final String identityKey;

  @ConstructorBinding
  public RestateEndpointProperties(
      @DefaultValue(value = "false") boolean enablePreviewContext, String identityKey) {
    this.enablePreviewContext = enablePreviewContext;
    this.identityKey = identityKey;
  }

  /**
   * @see RestateHttpEndpointBuilder#enablePreviewContext()
   */
  public boolean isEnablePreviewContext() {
    return enablePreviewContext;
  }

  /**
   * @see RestateHttpEndpointBuilder#withRequestIdentityVerifier(RequestIdentityVerifier)
   */
  public String getIdentityKey() {
    return identityKey;
  }
}
