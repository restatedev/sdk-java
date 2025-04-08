// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.client;

import java.util.*;
import org.jspecify.annotations.Nullable;

public final class RequestOptions {

  public static final RequestOptions DEFAULT = new RequestOptions(null);

  @Nullable private final Map<String, String> headers;

  private RequestOptions(@Nullable Map<String, String> headers) {
    this.headers = headers;
  }

  public Map<String, String> headers() {
    if (headers == null) {
      return Collections.emptyMap();
    }
    return headers;
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * @param headers Headers to attach in the request.
   */
  public static Builder withHeaders(Map<String, String> headers) {
    return builder().headers(headers);
  }

  public static final class Builder {
    @Nullable private Map<String, String> headers;

    private Builder() {}

    /**
     * @param key header key
     * @param value header value
     * @return this instance, so the builder can be used fluently.
     */
    public Builder header(String key, String value) {
      if (this.headers == null) {
        this.headers = new HashMap<>();
      }
      this.headers.put(key, value);
      return this;
    }

    /**
     * @param newHeaders headers to send together with the request.
     * @return this instance, so the builder can be used fluently.
     */
    public Builder headers(Map<String, String> newHeaders) {
      if (this.headers == null) {
        this.headers = new HashMap<>();
      }
      this.headers.putAll(newHeaders);
      return this;
    }

    public @Nullable Map<String, String> getHeaders() {
      return headers;
    }

    public Builder setHeaders(Map<String, String> newHeaders) {
      return headers(newHeaders);
    }

    public RequestOptions build() {
      return new RequestOptions(this.headers);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof RequestOptions that)) return false;
    return Objects.equals(headers, that.headers);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(headers);
  }

  @Override
  public String toString() {
    return "ClientRequestOptions{" + "headers=" + headers + '}';
  }
}
