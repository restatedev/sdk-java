// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.endpoint.definition;

import dev.restate.serde.Serde;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public final class HandlerDefinition<REQ, RES> {

  private final String name;
  private final HandlerType handlerType;
  private final @Nullable String acceptContentType;
  private final Serde<REQ> requestSerde;
  private final Serde<RES> responseSerde;
  private final @Nullable String documentation;
  private final Map<String, String> metadata;
  private final HandlerRunner<REQ, RES> runner;

  HandlerDefinition(
      String name,
      HandlerType handlerType,
      @Nullable String acceptContentType,
      Serde<REQ> requestSerde,
      Serde<RES> responseSerde,
      @Nullable String documentation,
      Map<String, String> metadata,
      HandlerRunner<REQ, RES> runner) {
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

  public HandlerRunner<REQ, RES> getRunner() {
    return runner;
  }

  public HandlerDefinition<REQ, RES> withAcceptContentType(String acceptContentType) {
    return new HandlerDefinition<>(
        name,
        handlerType,
        acceptContentType,
        requestSerde,
        responseSerde,
        documentation,
        metadata,
        runner);
  }

  public HandlerDefinition<REQ, RES> withDocumentation(@Nullable String documentation) {
    return new HandlerDefinition<>(
        name,
        handlerType,
        acceptContentType,
        requestSerde,
        responseSerde,
        documentation,
        metadata,
        runner);
  }

  public HandlerDefinition<REQ, RES> withMetadata(Map<String, String> metadata) {
    return new HandlerDefinition<>(
        name,
        handlerType,
        acceptContentType,
        requestSerde,
        responseSerde,
        documentation,
        metadata,
        runner);
  }

  public HandlerDefinition<REQ, RES> configure(
      Consumer<HandlerDefinition.Configurator> configurator) {
    HandlerDefinition.Configurator configuratorObj =
        new HandlerDefinition.Configurator(acceptContentType, documentation, metadata);
    configurator.accept(configuratorObj);

    return new HandlerDefinition<>(
        name,
        handlerType,
        configuratorObj.acceptContentType,
        requestSerde,
        responseSerde,
        configuratorObj.documentation,
        configuratorObj.metadata,
        runner);
  }

  public static final class Configurator {

    private @Nullable String acceptContentType;
    private @Nullable String documentation;
    private Map<String, String> metadata;

    public Configurator(
        @Nullable String acceptContentType,
        @Nullable String documentation,
        Map<String, String> metadata) {
      this.acceptContentType = acceptContentType;
      this.documentation = documentation;
      this.metadata = new HashMap<>(metadata);
    }

    public @Nullable String getAcceptContentType() {
      return acceptContentType;
    }

    public void setAcceptContentType(@Nullable String acceptContentType) {
      this.acceptContentType = acceptContentType;
    }

    public Configurator acceptContentType(@Nullable String acceptContentType) {
      this.setAcceptContentType(acceptContentType);
      return this;
    }

    public @Nullable String getDocumentation() {
      return documentation;
    }

    public void setDocumentation(@Nullable String documentation) {
      this.documentation = documentation;
    }

    public Configurator documentation(@Nullable String documentation) {
      this.setDocumentation(documentation);
      return this;
    }

    public Map<String, String> getMetadata() {
      return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
      this.metadata = metadata;
    }

    public Configurator addMetadata(String key, String value) {
      this.metadata.put(key, value);
      return this;
    }

    public Configurator metadata(Map<String, String> metadata) {
      this.setMetadata(metadata);
      return this;
    }
  }

  public static <REQ, RES> HandlerDefinition<REQ, RES> of(
      String handler,
      HandlerType handlerType,
      Serde<REQ> requestSerde,
      Serde<RES> responseSerde,
      HandlerRunner<REQ, RES> runner) {
    return new HandlerDefinition<>(
        handler,
        handlerType,
        null,
        requestSerde,
        responseSerde,
        null,
        Collections.emptyMap(),
        runner);
  }
}
