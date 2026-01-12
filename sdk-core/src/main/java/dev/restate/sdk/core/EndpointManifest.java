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
import dev.restate.sdk.core.generated.discovery.Discovery;
import dev.restate.sdk.core.generated.manifest.*;
import dev.restate.sdk.endpoint.definition.*;
import dev.restate.serde.Serde;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

final class EndpointManifest {

  private static final Input EMPTY_INPUT = new Input();
  private static final Output EMPTY_OUTPUT = new Output().withSetContentTypeIfEmpty(false);

  private final List<Service> services;

  EndpointManifest(Stream<ServiceDefinition> components, boolean experimentalContextEnabled) {
    this.services = components.map(EndpointManifest::convertService).collect(Collectors.toList());
  }

  EndpointManifestSchema manifest(
      Discovery.ServiceDiscoveryProtocolVersion version,
      EndpointManifestSchema.ProtocolMode protocolMode) {
    EndpointManifestSchema manifest =
        new EndpointManifestSchema()
            .withProtocolMode(protocolMode)
            .withMinProtocolVersion((long) MIN_SERVICE_PROTOCOL_VERSION.getNumber())
            .withMaxProtocolVersion((long) MAX_SERVICE_PROTOCOL_VERSION.getNumber())
            .withServices(this.services);
    // Verify that the user didn't set fields that we don't support in the discovery version we set
    for (var service : manifest.getServices()) {
      if (version.getNumber() < Discovery.ServiceDiscoveryProtocolVersion.V2.getNumber()) {
        verifyFieldNotSet(
            "metadata",
            service,
            s -> s.getMetadata() != null && !s.getMetadata().getAdditionalProperties().isEmpty());
      }
      if (version.getNumber() < Discovery.ServiceDiscoveryProtocolVersion.V3.getNumber()) {
        verifyFieldNull("idempotency retention", service.getIdempotencyRetention());
        verifyFieldNull("journal retention", service.getJournalRetention());
        verifyFieldNull("inactivity timeout", service.getInactivityTimeout());
        verifyFieldNull("abort timeout", service.getAbortTimeout());
        verifyFieldNull("enable lazy state", service.getEnableLazyState());
        verifyFieldNull("ingress private", service.getIngressPrivate());
      }
      if (version.getNumber() < Discovery.ServiceDiscoveryProtocolVersion.V4.getNumber()) {
        verifyFieldNull("retry policy initial interval", service.getRetryPolicyInitialInterval());
        verifyFieldNull("retry policy max interval", service.getRetryPolicyMaxInterval());
        verifyFieldNull("retry policy max attempts", service.getRetryPolicyMaxAttempts());
        verifyFieldNull("retry policy on max attempts", service.getRetryPolicyOnMaxAttempts());
        verifyFieldNull(
            "retry policy exponentiation factor", service.getRetryPolicyExponentiationFactor());
      }
      for (var handler : service.getHandlers()) {
        if (version.getNumber() < Discovery.ServiceDiscoveryProtocolVersion.V2.getNumber()) {
          verifyFieldNotSet(
              "metadata",
              handler,
              h -> h.getMetadata() != null && !h.getMetadata().getAdditionalProperties().isEmpty());
        }
        if (version.getNumber() < Discovery.ServiceDiscoveryProtocolVersion.V3.getNumber()) {
          verifyFieldNull("idempotency retention", handler.getIdempotencyRetention());
          verifyFieldNull("journal retention", handler.getJournalRetention());
          verifyFieldNull("inactivity timeout", handler.getInactivityTimeout());
          verifyFieldNull("abort timeout", handler.getAbortTimeout());
          verifyFieldNull("enable lazy state", handler.getEnableLazyState());
          verifyFieldNull("ingress private", handler.getIngressPrivate());
        }
        if (version.getNumber() < Discovery.ServiceDiscoveryProtocolVersion.V4.getNumber()) {
          verifyFieldNull("retry policy initial interval", handler.getRetryPolicyInitialInterval());
          verifyFieldNull("retry policy max interval", handler.getRetryPolicyMaxInterval());
          verifyFieldNull("retry policy max attempts", handler.getRetryPolicyMaxAttempts());
          verifyFieldNull("retry policy on max attempts", handler.getRetryPolicyOnMaxAttempts());
          verifyFieldNull(
              "retry policy exponentiation factor", handler.getRetryPolicyExponentiationFactor());
        }
      }
    }

    return manifest;
  }

  private static Service convertService(ServiceDefinition svc) {
    return new Service()
        .withName(svc.getServiceName())
        .withTy(convertServiceType(svc.getServiceType()))
        .withDocumentation(svc.getDocumentation())
        .withIdempotencyRetention(
            svc.getIdempotencyRetention() != null ? svc.getIdempotencyRetention().toMillis() : null)
        .withJournalRetention(
            svc.getJournalRetention() != null ? svc.getJournalRetention().toMillis() : null)
        .withInactivityTimeout(
            svc.getInactivityTimeout() != null ? svc.getInactivityTimeout().toMillis() : null)
        .withAbortTimeout(svc.getAbortTimeout() != null ? svc.getAbortTimeout().toMillis() : null)
        .withEnableLazyState(svc.getEnableLazyState())
        .withIngressPrivate(svc.getIngressPrivate())
        .withRetryPolicyInitialInterval(
            svc.getInvocationRetryPolicy() != null
                    && svc.getInvocationRetryPolicy().initialInterval() != null
                ? svc.getInvocationRetryPolicy().initialInterval().toMillis()
                : null)
        .withRetryPolicyMaxInterval(
            svc.getInvocationRetryPolicy() != null
                    && svc.getInvocationRetryPolicy().maxInterval() != null
                ? svc.getInvocationRetryPolicy().maxInterval().toMillis()
                : null)
        .withRetryPolicyExponentiationFactor(
            svc.getInvocationRetryPolicy() != null
                    && svc.getInvocationRetryPolicy().exponentiationFactor() != null
                ? svc.getInvocationRetryPolicy().exponentiationFactor()
                : null)
        .withRetryPolicyMaxAttempts(
            svc.getInvocationRetryPolicy() != null
                    && svc.getInvocationRetryPolicy().maxAttempts() != null
                ? svc.getInvocationRetryPolicy().maxAttempts().longValue()
                : null)
        .withRetryPolicyOnMaxAttempts(
            (svc.getInvocationRetryPolicy() == null)
                ? null
                : svc.getInvocationRetryPolicy().onMaxAttempts()
                        == InvocationRetryPolicy.OnMaxAttempts.PAUSE
                    ? Service.RetryPolicyOnMaxAttempts.PAUSE
                    : svc.getInvocationRetryPolicy().onMaxAttempts()
                            == InvocationRetryPolicy.OnMaxAttempts.KILL
                        ? Service.RetryPolicyOnMaxAttempts.KILL
                        : null)
        .withMetadata(
            svc.getMetadata().entrySet().stream()
                .reduce(
                    new Metadata__1(),
                    (meta, entry) -> meta.withAdditionalProperty(entry.getKey(), entry.getValue()),
                    (m1, m2) -> {
                      m2.getAdditionalProperties().forEach(m1::setAdditionalProperty);
                      return m1;
                    }))
        .withHandlers(
            svc.getHandlers().stream()
                .map(EndpointManifest::convertHandler)
                .collect(Collectors.toList()));
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
        .withIdempotencyRetention(
            handler.getIdempotencyRetention() != null
                ? handler.getIdempotencyRetention().toMillis()
                : null)
        .withWorkflowCompletionRetention(
            handler.getWorkflowRetention() != null
                ? handler.getWorkflowRetention().toMillis()
                : null)
        .withJournalRetention(
            handler.getJournalRetention() != null ? handler.getJournalRetention().toMillis() : null)
        .withInactivityTimeout(
            handler.getInactivityTimeout() != null
                ? handler.getInactivityTimeout().toMillis()
                : null)
        .withAbortTimeout(
            handler.getAbortTimeout() != null ? handler.getAbortTimeout().toMillis() : null)
        .withEnableLazyState(handler.getEnableLazyState())
        .withIngressPrivate(handler.getIngressPrivate())
        .withRetryPolicyInitialInterval(
            handler.getInvocationRetryPolicy() != null
                    && handler.getInvocationRetryPolicy().initialInterval() != null
                ? handler.getInvocationRetryPolicy().initialInterval().toMillis()
                : null)
        .withRetryPolicyMaxInterval(
            handler.getInvocationRetryPolicy() != null
                    && handler.getInvocationRetryPolicy().maxInterval() != null
                ? handler.getInvocationRetryPolicy().maxInterval().toMillis()
                : null)
        .withRetryPolicyExponentiationFactor(
            handler.getInvocationRetryPolicy() != null
                    && handler.getInvocationRetryPolicy().exponentiationFactor() != null
                ? handler.getInvocationRetryPolicy().exponentiationFactor()
                : null)
        .withRetryPolicyMaxAttempts(
            handler.getInvocationRetryPolicy() != null
                    && handler.getInvocationRetryPolicy().maxAttempts() != null
                ? handler.getInvocationRetryPolicy().maxAttempts().longValue()
                : null)
        .withRetryPolicyOnMaxAttempts(
            (handler.getInvocationRetryPolicy() == null)
                ? null
                : handler.getInvocationRetryPolicy().onMaxAttempts()
                        == InvocationRetryPolicy.OnMaxAttempts.PAUSE
                    ? Handler.RetryPolicyOnMaxAttempts.PAUSE
                    : handler.getInvocationRetryPolicy().onMaxAttempts()
                            == InvocationRetryPolicy.OnMaxAttempts.KILL
                        ? Handler.RetryPolicyOnMaxAttempts.KILL
                        : null)
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

  private static Handler.Ty convertHandlerType(@Nullable HandlerType handlerType) {
    if (handlerType == null) {
      return null;
    }
    return switch (handlerType) {
      case WORKFLOW -> Handler.Ty.WORKFLOW;
      case EXCLUSIVE -> Handler.Ty.EXCLUSIVE;
      case SHARED -> Handler.Ty.SHARED;
    };
  }

  private static <T> void verifyFieldNotSet(
      String featureName, T value, Predicate<T> isSetPredicate) {
    if (isSetPredicate.test(value)) {
      throw new ProtocolException(
          "The code uses the new discovery feature '"
              + featureName
              + "' but the runtime doesn't support it yet. Either remove the usage of this feature, or upgrade the runtime.",
          500);
    }
  }

  private static <T> void verifyFieldNull(String featureName, T value) {
    verifyFieldNotSet(featureName, value, Objects::nonNull);
  }
}
