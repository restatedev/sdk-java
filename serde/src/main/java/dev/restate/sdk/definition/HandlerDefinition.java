// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.definition;

import dev.restate.sdk.serde.Serde;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

public final class HandlerDefinition<REQ, RES, OPT> {

  private final String name;
  private final HandlerType handlerType;
  private final @Nullable String acceptContentType;
  private final Serde<REQ> requestSerde;
  private final Serde<RES> responseSerde;
  private final @Nullable String documentation;
  private final Map<String, String> metadata;
  private final HandlerRunner<REQ, RES, OPT> runner;

  HandlerDefinition( String name,
                     HandlerType handlerType,
                     @Nullable String acceptContentType,
                     Serde<REQ> requestSerde,
                     Serde<RES> responseSerde,
                     @Nullable String documentation,
                     Map<String, String> metadata, HandlerRunner<REQ, RES, OPT> runner) {
    this.name = name;
    this.handlerType = handlerType;
    this.acceptContentType = acceptContentType;
    this.requestSerde = requestSerde;
    this.responseSerde = responseSerde;
    this.documentation = documentation;
    this.metadata = metadata;
    this.runner = runner;
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

  public HandlerRunner<REQ, RES, OPT> getRunner() {
    return runner;
  }

  public HandlerDefinition<REQ, RES, OPT> withAcceptContentType(String acceptContentType) {
    return new HandlerDefinition<>(
            name, handlerType, acceptContentType, requestSerde, responseSerde, documentation, metadata, runner);
  }

  public HandlerDefinition<REQ, RES, OPT> withDocumentation(@Nullable String documentation) {
    return new HandlerDefinition<>(
            name, handlerType, acceptContentType, requestSerde, responseSerde, documentation, metadata, runner);
  }

  public HandlerDefinition<REQ, RES, OPT> withMetadata(Map<String, String> metadata) {
    return new HandlerDefinition<>(
            name, handlerType, acceptContentType, requestSerde, responseSerde, documentation, metadata, runner);
  }

  public static <REQ, RES, OPT> HandlerDefinition<REQ, RES, OPT> of(
          String handler, HandlerType handlerType, Serde<REQ> requestSerde, Serde<RES> responseSerde, HandlerRunner<REQ, RES, OPT> runner) {
    return new HandlerDefinition<>(
            handler, handlerType, null, requestSerde, responseSerde, null, Collections.emptyMap(), runner);
  }
}
