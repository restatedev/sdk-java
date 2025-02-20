// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static dev.restate.sdk.core.DiscoveryProtocol.MANIFEST_OBJECT_MAPPER;
import static dev.restate.sdk.core.statemachine.ServiceProtocol.MAX_SERVICE_PROTOCOL_VERSION;
import static dev.restate.sdk.core.statemachine.ServiceProtocol.MIN_SERVICE_PROTOCOL_VERSION;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.restate.sdk.core.generated.manifest.*;
import dev.restate.sdk.endpoint.definition.HandlerDefinition;
import dev.restate.sdk.endpoint.definition.HandlerType;
import dev.restate.sdk.endpoint.definition.ServiceDefinition;
import dev.restate.sdk.endpoint.definition.ServiceType;
import dev.restate.serde.Serde;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class EndpointManifest {

  private static final Input EMPTY_INPUT = new Input();
  private static final Output EMPTY_OUTPUT = new Output().withSetContentTypeIfEmpty(false);

  private final EndpointManifestSchema manifest;

  EndpointManifest(
      EndpointManifestSchema.ProtocolMode protocolMode,
      Stream<ServiceDefinition> components,
      boolean experimentalContextEnabled) {
    this.manifest =
        new EndpointManifestSchema()
            .withMinProtocolVersion(MIN_SERVICE_PROTOCOL_VERSION.getNumber())
            .withMaxProtocolVersion(MAX_SERVICE_PROTOCOL_VERSION.getNumber())
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

  EndpointManifestSchema manifest() {
    return this.manifest;
  }

  private static Service.Ty convertServiceType(ServiceType serviceType) {
    return switch (serviceType) {
      case WORKFLOW -> Service.Ty.WORKFLOW;
      case SERVICE -> Service.Ty.SERVICE;
      case VIRTUAL_OBJECT -> Service.Ty.VIRTUAL_OBJECT;
    };
  }

  private static Handler convertHandler(HandlerDefinition<?, ?> handler) {
    return new Handler()
        .withName(handler.getName())
        .withTy(convertHandlerType(handler.getHandlerType()))
        .withInput(convertHandlerInput(handler))
        .withOutput(convertHandlerOutput(handler))
        .withDocumentation(handler.getDocumentation())
        .withMetadata(
            handler.getMetadata().entrySet().stream()
                .reduce(
                    new Metadata(),
                    (meta, entry) -> meta.withAdditionalProperty(entry.getKey(), entry.getValue()),
                    (m1, m2) -> {
                      m2.getAdditionalProperties().forEach(m1::setAdditionalProperty);
                      return m1;
                    }));
  }

  private static Input convertHandlerInput(HandlerDefinition<?, ?> def) {
    String acceptContentType =
        def.getAcceptContentType() != null
            ? def.getAcceptContentType()
            : def.getRequestSerde().contentType();

    Input input =
        acceptContentType == null
            ? EMPTY_INPUT
            : new Input().withRequired(true).withContentType(acceptContentType);

    Serde.Schema jsonSchema = def.getRequestSerde().jsonSchema();
    if (jsonSchema instanceof Serde.JsonSchema schema) {
      input.setJsonSchema(schema.schema());
    } else if (jsonSchema instanceof Serde.StringifiedJsonSchema schema) {
      // We need to convert it to databind JSON value
      try {
        input.setJsonSchema(MANIFEST_OBJECT_MAPPER.readTree(schema.schema()));
      } catch (JsonProcessingException e) {
        throw new RuntimeException(
            "The schema generated by " + def.getRequestSerde() + " is not a valid JSON", e);
      }
    }
    return input;
  }

  private static Output convertHandlerOutput(HandlerDefinition<?, ?> def) {
    Output output =
        def.getResponseSerde().contentType() == null
            ? EMPTY_OUTPUT
            : new Output()
                .withContentType(def.getResponseSerde().contentType())
                .withSetContentTypeIfEmpty(false);

    Serde.Schema jsonSchema = def.getResponseSerde().jsonSchema();
    if (jsonSchema instanceof Serde.JsonSchema schema) {
      output.setJsonSchema(schema.schema());
    } else if (jsonSchema instanceof Serde.StringifiedJsonSchema schema) {
      // We need to convert it to databind JSON value
      try {
        output.setJsonSchema(MANIFEST_OBJECT_MAPPER.readTree(schema.schema()));
      } catch (JsonProcessingException e) {
        throw new RuntimeException(
            "The schema generated by " + def.getResponseSerde() + " is not a valid JSON", e);
      }
    }
    return output;
  }

  private static Handler.Ty convertHandlerType(HandlerType handlerType) {
    return switch (handlerType) {
      case WORKFLOW -> Handler.Ty.WORKFLOW;
      case EXCLUSIVE -> Handler.Ty.EXCLUSIVE;
      case SHARED -> Handler.Ty.SHARED;
    };
  }
}
