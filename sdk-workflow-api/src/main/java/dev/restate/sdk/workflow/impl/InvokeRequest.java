// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.workflow.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;

public final class InvokeRequest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final String key;
  private final JsonNode payload;

  @JsonCreator
  public InvokeRequest(@JsonProperty("key") String key, @JsonProperty("payload") JsonNode payload) {
    this.key = key;
    this.payload = payload;
  }

  public String getKey() {
    return key;
  }

  public JsonNode getPayload() {
    return payload;
  }

  public static InvokeRequest fromAny(String key, Object value) {
    return new InvokeRequest(key, OBJECT_MAPPER.convertValue(value, JsonNode.class));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    InvokeRequest that = (InvokeRequest) o;
    return Objects.equals(key, that.key) && Objects.equals(payload, that.payload);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, payload);
  }

  @Override
  public String toString() {
    return "InvokeRequest{" + "key='" + key + '\'' + ", payload=" + payload + '}';
  }
}
