// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "restate.client")
public class RestateClientProperties {

  private final String baseUri;
  private final Map<String, String> headers;

  @ConstructorBinding
  public RestateClientProperties(
      @DefaultValue(value = "http://localhost:8080") String baseUri, Map<String, String> headers) {
    this.baseUri = baseUri;
    this.headers = headers;
  }

  /** Base uri of the Restate client, e.g. {@code http://localhost:8080}. */
  public String getBaseUri() {
    return baseUri;
  }

  /** Headers added to each request sent to Restate. */
  public Map<String, String> getHeaders() {
    return headers;
  }
}
