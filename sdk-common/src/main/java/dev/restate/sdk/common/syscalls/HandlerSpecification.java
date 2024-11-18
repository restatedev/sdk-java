// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.common.syscalls;

import dev.restate.sdk.common.HandlerType;
import dev.restate.sdk.common.Serde;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class HandlerSpecification<REQ, RES> {

  private final String name;
  private final HandlerType handlerType;
  private final @Nullable String acceptContentType;
  private final Serde<REQ> requestSerde;
  private final Serde<RES> responseSerde;
  private final @Nullable String documentation;
  private final Map<String, String> metadata;

  HandlerSpecification(
      String name,
      HandlerType handlerType,
      @Nullable String acceptContentType,
      Serde<REQ> requestSerde,
      Serde<RES> responseSerde,
      @Nullable String documentation,
      Map<String, String> metadata) {
    this.name = name;
    this.handlerType = handlerType;
    this.acceptContentType = acceptContentType;
    this.requestSerde = requestSerde;
    this.responseSerde = responseSerde;
    this.documentation = documentation;
    this.metadata = metadata;
  }

  public static <T, R> HandlerSpecification<T, R> of(
      String method, HandlerType handlerType, Serde<T> requestSerde, Serde<R> responseSerde) {
    return new HandlerSpecification<>(
        method, handlerType, null, requestSerde, responseSerde, null, Collections.emptyMap());
  }

  public String getName() {
    return name;
  }

  public HandlerType getHandlerType() {
    return handlerType;
  }

  public @Nullable String getAcceptContentType() {
    return acceptContentType;
  }

  public Serde<REQ> getRequestSerde() {
    return requestSerde;
  }

  public Serde<RES> getResponseSerde() {
    return responseSerde;
  }

  public @Nullable String getDocumentation() {
    return documentation;
  }

  public Map<String, String> getMetadata() {
    return metadata;
  }

  public HandlerSpecification<REQ, RES> withAcceptContentType(String acceptContentType) {
    return new HandlerSpecification<>(
        name, handlerType, acceptContentType, requestSerde, responseSerde, documentation, metadata);
  }

  public HandlerSpecification<REQ, RES> withDocumentation(@Nullable String documentation) {
    return new HandlerSpecification<>(
        name, handlerType, acceptContentType, requestSerde, responseSerde, documentation, metadata);
  }

  public HandlerSpecification<REQ, RES> withMetadata(Map<String, String> metadata) {
    return new HandlerSpecification<>(
        name, handlerType, acceptContentType, requestSerde, responseSerde, documentation, metadata);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof HandlerSpecification<?, ?> that)) return false;
    return Objects.equals(name, that.name)
        && handlerType == that.handlerType
        && Objects.equals(acceptContentType, that.acceptContentType)
        && Objects.equals(requestSerde, that.requestSerde)
        && Objects.equals(responseSerde, that.responseSerde)
        && Objects.equals(documentation, that.documentation)
        && Objects.equals(metadata, that.metadata);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        name, handlerType, acceptContentType, requestSerde, responseSerde, documentation, metadata);
  }

  @Override
  public String toString() {
    return "HandlerSpecification{"
        + "name='"
        + name
        + '\''
        + ", handlerType="
        + handlerType
        + ", acceptContentType='"
        + acceptContentType
        + '\''
        + ", requestContentType="
        + requestSerde.contentType()
        + ", responseContentType="
        + responseSerde.contentType()
        + ", documentation="
        + documentation
        + ", metadata="
        + metadata
        + '}';
  }
}
