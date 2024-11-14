// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static dev.restate.sdk.core.ServiceProtocol.*;

import dev.restate.sdk.common.HandlerType;
import dev.restate.sdk.common.RichSerde;
import dev.restate.sdk.common.ServiceType;
import dev.restate.sdk.common.syscalls.HandlerDefinition;
import dev.restate.sdk.common.syscalls.HandlerSpecification;
import dev.restate.sdk.common.syscalls.ServiceDefinition;
import dev.restate.sdk.core.manifest.*;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class EndpointManifest {

  private static final Input EMPTY_INPUT = new Input();
  private static final Output EMPTY_OUTPUT = new Output().withSetContentTypeIfEmpty(false);

  private final EndpointManifestSchema manifest;

  public EndpointManifest(
      EndpointManifestSchema.ProtocolMode protocolMode,
      Stream<ServiceDefinition<?>> components,
      boolean experimentalContextEnabled) {
    this.manifest =
        new EndpointManifestSchema()
            .withMinProtocolVersion(MIN_SERVICE_PROTOCOL_VERSION.getNumber())
            .withMaxProtocolVersion(
                maxServiceProtocolVersion(experimentalContextEnabled).getNumber())
            .withProtocolMode(protocolMode)
            .withServices(
                components
                    .map(
                        svc ->
                            new Service()
                                .withName(svc.getServiceName())
                                .withTy(convertServiceType(svc.getServiceType()))
                                .withDocumentation(svc.getDocumentation())
                                .withMetadata(
                                    svc.getMetadata().entrySet().stream()
                                        .reduce(
                                            new Metadata__1(),
                                            (meta, entry) ->
                                                meta.withAdditionalProperty(
                                                    entry.getKey(), entry.getValue()),
                                            (m1, m2) -> {
                                              m2.getAdditionalProperties()
                                                  .forEach(m1::setAdditionalProperty);
                                              return m1;
                                            }))
                                .withHandlers(
                                    svc.getHandlers().stream()
                                        .map(EndpointManifest::convertHandler)
                                        .collect(Collectors.toList())))
                    .collect(Collectors.toList()));
  }

  public EndpointManifestSchema manifest() {
    return this.manifest;
  }

  private static Service.Ty convertServiceType(ServiceType serviceType) {
    switch (serviceType) {
      case WORKFLOW:
        return Service.Ty.WORKFLOW;
      case SERVICE:
        return Service.Ty.SERVICE;
      case VIRTUAL_OBJECT:
        return Service.Ty.VIRTUAL_OBJECT;
    }
    throw new IllegalStateException();
  }

  private static Handler convertHandler(HandlerDefinition<?, ?, ?> handler) {
    HandlerSpecification<?, ?> spec = handler.getSpec();
    return new Handler()
        .withName(spec.getName())
        .withTy(convertHandlerType(spec.getHandlerType()))
        .withInput(convertHandlerInput(spec))
        .withOutput(convertHandlerOutput(spec))
        .withDocumentation(spec.getDocumentation())
        .withMetadata(
            spec.getMetadata().entrySet().stream()
                .reduce(
                    new Metadata(),
                    (meta, entry) -> meta.withAdditionalProperty(entry.getKey(), entry.getValue()),
                    (m1, m2) -> {
                      m2.getAdditionalProperties().forEach(m1::setAdditionalProperty);
                      return m1;
                    }));
  }

  private static Input convertHandlerInput(HandlerSpecification<?, ?> spec) {
    String acceptContentType =
        spec.getAcceptContentType() != null
            ? spec.getAcceptContentType()
            : spec.getRequestSerde().contentType();

    Input input =
        acceptContentType == null
            ? EMPTY_INPUT
            : new Input().withRequired(true).withContentType(acceptContentType);

    if (spec.getRequestSerde() instanceof RichSerde) {
      input.setJsonSchema(
          Objects.requireNonNull(((RichSerde<?>) spec.getRequestSerde()).jsonSchema()));
    }
    return input;
  }

  private static Output convertHandlerOutput(HandlerSpecification<?, ?> spec) {
    Output output =
        spec.getResponseSerde().contentType() == null
            ? EMPTY_OUTPUT
            : new Output()
                .withContentType(spec.getResponseSerde().contentType())
                .withSetContentTypeIfEmpty(false);

    if (spec.getResponseSerde() instanceof RichSerde) {
      output.setJsonSchema(
          Objects.requireNonNull(((RichSerde<?>) spec.getResponseSerde()).jsonSchema()));
    }

    return output;
  }

  private static Handler.Ty convertHandlerType(HandlerType handlerType) {
    switch (handlerType) {
      case WORKFLOW:
        return Handler.Ty.WORKFLOW;
      case EXCLUSIVE:
        return Handler.Ty.EXCLUSIVE;
      case SHARED:
        return Handler.Ty.SHARED;
    }
    throw new IllegalStateException();
  }
}
