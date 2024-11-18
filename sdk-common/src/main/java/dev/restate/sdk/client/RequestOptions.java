// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class RequestOptions {
  public static final RequestOptions DEFAULT = new RequestOptions();

  final Map<String, String> additionalHeaders;

  public RequestOptions() {
    this(new HashMap<>());
  }

  public RequestOptions(Map<String, String> additionalHeaders) {
    this.additionalHeaders = additionalHeaders;
  }

  public RequestOptions withHeader(String name, String value) {
    RequestOptions newOptions = this.copy();
    newOptions.additionalHeaders.put(name, value);
    return newOptions;
  }

  public RequestOptions withHeaders(Map<? extends String, ? extends String> additionalHeaders) {
    RequestOptions newOptions = this.copy();
    newOptions.additionalHeaders.putAll(additionalHeaders);
    return newOptions;
  }

  public Map<String, String> getAdditionalHeaders() {
    return additionalHeaders;
  }

  public RequestOptions copy() {
    return new RequestOptions(new HashMap<>(this.additionalHeaders));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RequestOptions that)) return false;
    return Objects.equals(additionalHeaders, that.additionalHeaders);
  }

  @Override
  public int hashCode() {
    return additionalHeaders.hashCode();
  }

  @Override
  public String toString() {
    return "RequestOptions{" + "additionalHeaders=" + additionalHeaders + '}';
  }
}
