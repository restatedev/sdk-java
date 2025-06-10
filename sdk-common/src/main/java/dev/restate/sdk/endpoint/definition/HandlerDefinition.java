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
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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
  private final @Nullable Duration inactivityTimeout;
  private final @Nullable Duration abortTimeout;
  private final @Nullable Duration idempotencyRetention;
  private final @Nullable Duration workflowRetention;
  private final @Nullable Duration journalRetention;
  private final @Nullable Boolean ingressPrivate;
  private final @Nullable Boolean enableLazyState;

  HandlerDefinition(
      String name,
      HandlerType handlerType,
      @Nullable String acceptContentType,
      Serde<REQ> requestSerde,
      Serde<RES> responseSerde,
      @Nullable String documentation,
      Map<String, String> metadata,
      HandlerRunner<REQ, RES> runner,
      @Nullable Duration inactivityTimeout,
      @Nullable Duration abortTimeout,
      @Nullable Duration idempotencyRetention,
      @Nullable Duration workflowRetention,
      @Nullable Duration journalRetention,
      @Nullable Boolean ingressPrivate,
      @Nullable Boolean enableLazyState) {
    this.name = name;
    this.handlerType = handlerType;
    this.acceptContentType = acceptContentType;
    this.requestSerde = requestSerde;
    this.responseSerde = responseSerde;
    this.documentation = documentation;
    this.metadata = metadata;
    this.runner = runner;
    this.inactivityTimeout = inactivityTimeout;
    this.abortTimeout = abortTimeout;
    this.idempotencyRetention = idempotencyRetention;
    this.workflowRetention = workflowRetention;
    this.journalRetention = journalRetention;
    this.ingressPrivate = ingressPrivate;
    this.enableLazyState = enableLazyState;
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

  public @Nullable Duration getInactivityTimeout() {
    return inactivityTimeout;
  }

  public @Nullable Duration getAbortTimeout() {
    return abortTimeout;
  }

  public @Nullable Duration getIdempotencyRetention() {
    return idempotencyRetention;
  }

  public @Nullable Duration getWorkflowRetention() {
    return workflowRetention;
  }

  public @Nullable Duration getJournalRetention() {
    return journalRetention;
  }

  public @Nullable Boolean getIngressPrivate() {
    return ingressPrivate;
  }

  public @Nullable Boolean getEnableLazyState() {
    return enableLazyState;
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
        runner,
        inactivityTimeout,
        abortTimeout,
        idempotencyRetention,
        workflowRetention,
        journalRetention,
        ingressPrivate,
        enableLazyState);
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
        runner,
        inactivityTimeout,
        abortTimeout,
        idempotencyRetention,
        journalRetention,
        workflowRetention,
        ingressPrivate,
        enableLazyState);
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
        runner,
        inactivityTimeout,
        abortTimeout,
        idempotencyRetention,
        workflowRetention,
        journalRetention,
        ingressPrivate,
        enableLazyState);
  }

  public HandlerDefinition<REQ, RES> configure(
      Consumer<HandlerDefinition.Configurator> configurator) {
    HandlerDefinition.Configurator configuratorObj =
        new HandlerDefinition.Configurator(
            acceptContentType,
            documentation,
            metadata,
            inactivityTimeout,
            abortTimeout,
            idempotencyRetention,
            workflowRetention,
            journalRetention,
            ingressPrivate,
            enableLazyState);
    configurator.accept(configuratorObj);

    return new HandlerDefinition<>(
        name,
        handlerType,
        configuratorObj.acceptContentType,
        requestSerde,
        responseSerde,
        configuratorObj.documentation,
        configuratorObj.metadata,
        runner,
        configuratorObj.inactivityTimeout,
        configuratorObj.abortTimeout,
        configuratorObj.idempotencyRetention,
        configuratorObj.workflowRetention,
        configuratorObj.journalRetention,
        configuratorObj.ingressPrivate,
        configuratorObj.enableLazyState);
  }

  public static final class Configurator {

    private @Nullable String acceptContentType;
    private @Nullable String documentation;
    private Map<String, String> metadata;
    private @Nullable Duration inactivityTimeout;
    private @Nullable Duration abortTimeout;
    private @Nullable Duration idempotencyRetention;
    private @Nullable Duration workflowRetention;
    private @Nullable Duration journalRetention;
    private @Nullable Boolean ingressPrivate;
    private @Nullable Boolean enableLazyState;

    public Configurator(
        @Nullable String acceptContentType,
        @Nullable String documentation,
        Map<String, String> metadata,
        @Nullable Duration inactivityTimeout,
        @Nullable Duration abortTimeout,
        @Nullable Duration idempotencyRetention,
        @Nullable Duration workflowRetention,
        @Nullable Duration journalRetention,
        @Nullable Boolean ingressPrivate,
        @Nullable Boolean enableLazyState) {
      this.acceptContentType = acceptContentType;
      this.documentation = documentation;
      this.metadata = new HashMap<>(metadata);
      this.inactivityTimeout = inactivityTimeout;
      this.abortTimeout = abortTimeout;
      this.idempotencyRetention = idempotencyRetention;
      this.workflowRetention = workflowRetention;
      this.journalRetention = journalRetention;
      this.ingressPrivate = ingressPrivate;
      this.enableLazyState = enableLazyState;
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

    public @Nullable Duration getInactivityTimeout() {
      return inactivityTimeout;
    }

    public void setInactivityTimeout(@Nullable Duration inactivityTimeout) {
      this.inactivityTimeout = inactivityTimeout;
    }

    public Configurator inactivityTimeout(@Nullable Duration inactivityTimeout) {
      setInactivityTimeout(inactivityTimeout);
      return this;
    }

    public @Nullable Duration getAbortTimeout() {
      return abortTimeout;
    }

    public void setAbortTimeout(@Nullable Duration abortTimeout) {
      this.abortTimeout = abortTimeout;
    }

    public Configurator abortTimeout(@Nullable Duration abortTimeout) {
      setAbortTimeout(abortTimeout);
      return this;
    }

    public @Nullable Duration getIdempotencyRetention() {
      return idempotencyRetention;
    }

    public void setIdempotencyRetention(@Nullable Duration idempotencyRetention) {
      this.idempotencyRetention = idempotencyRetention;
    }

    public Configurator idempotencyRetention(@Nullable Duration idempotencyRetention) {
      setIdempotencyRetention(idempotencyRetention);
      return this;
    }

    public @Nullable Duration getWorkflowRetention() {
      return workflowRetention;
    }

    public void setWorkflowRetention(@Nullable Duration workflowRetention) {
      this.workflowRetention = workflowRetention;
    }

    public Configurator workflowRetention(@Nullable Duration workflowRetention) {
      setWorkflowRetention(workflowRetention);
      return this;
    }

    public @Nullable Duration getJournalRetention() {
      return journalRetention;
    }

    public void setJournalRetention(@Nullable Duration journalRetention) {
      this.journalRetention = journalRetention;
    }

    public Configurator journalRetention(@Nullable Duration journalRetention) {
      setJournalRetention(journalRetention);
      return this;
    }

    public @Nullable Boolean getIngressPrivate() {
      return ingressPrivate;
    }

    public void setIngressPrivate(@Nullable Boolean ingressPrivate) {
      this.ingressPrivate = ingressPrivate;
    }

    public Configurator ingressPrivate(@Nullable Boolean ingressPrivate) {
      setIngressPrivate(ingressPrivate);
      return this;
    }

    public @Nullable Boolean getEnableLazyState() {
      return enableLazyState;
    }

    public void setEnableLazyState(@Nullable Boolean enableLazyState) {
      this.enableLazyState = enableLazyState;
    }

    public Configurator enableLazyState(@Nullable Boolean enableLazyState) {
      setEnableLazyState(enableLazyState);
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
        runner,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof HandlerDefinition<?, ?> that)) return false;
    return Objects.equals(getName(), that.getName())
        && getHandlerType() == that.getHandlerType()
        && Objects.equals(getAcceptContentType(), that.getAcceptContentType())
        && Objects.equals(getRequestSerde(), that.getRequestSerde())
        && Objects.equals(getResponseSerde(), that.getResponseSerde())
        && Objects.equals(getDocumentation(), that.getDocumentation())
        && Objects.equals(getMetadata(), that.getMetadata())
        && Objects.equals(getRunner(), that.getRunner())
        && Objects.equals(getInactivityTimeout(), that.getInactivityTimeout())
        && Objects.equals(getAbortTimeout(), that.getAbortTimeout())
        && Objects.equals(getIdempotencyRetention(), that.getIdempotencyRetention())
        && Objects.equals(getWorkflowRetention(), that.getWorkflowRetention())
        && Objects.equals(getJournalRetention(), that.getJournalRetention())
        && Objects.equals(getIngressPrivate(), that.getIngressPrivate())
        && Objects.equals(getEnableLazyState(), that.getEnableLazyState());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        getName(),
        getHandlerType(),
        getAcceptContentType(),
        getRequestSerde(),
        getResponseSerde(),
        getDocumentation(),
        getMetadata(),
        getRunner(),
        getInactivityTimeout(),
        getAbortTimeout(),
        getIdempotencyRetention(),
        getWorkflowRetention(),
        getJournalRetention(),
        getIngressPrivate(),
        getEnableLazyState());
  }
}
