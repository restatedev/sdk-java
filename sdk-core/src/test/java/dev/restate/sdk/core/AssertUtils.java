// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.google.protobuf.MessageLite;
import dev.restate.sdk.core.generated.manifest.EndpointManifestSchema;
import dev.restate.sdk.core.generated.manifest.Service;
import dev.restate.sdk.core.generated.manifest.Handler;
import dev.restate.sdk.core.generated.protocol.Protocol;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.types.TerminalException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractObjectAssert;
import org.assertj.core.api.ObjectAssert;

public class AssertUtils {

  public static Consumer<List<MessageLite>> containsOnly(Consumer<? super MessageLite> consumer) {
    return msgs -> assertThat(msgs).satisfiesExactly(consumer);
  }

  public static Consumer<List<MessageLite>> containsOnlyExactErrorMessage(Throwable e) {
    return containsOnly(exactErrorMessage(e));
  }

  public static Consumer<? super MessageLite> errorMessage(
      Consumer<? super Protocol.ErrorMessage> consumer) {
    return msg ->
        assertThat(msg).asInstanceOf(type(Protocol.ErrorMessage.class)).satisfies(consumer);
  }

  public static Consumer<? super MessageLite> exactErrorMessage(Throwable e) {
    return errorMessage(
        msg ->
            assertThat(msg)
                .returns(e.toString(), Protocol.ErrorMessage::getMessage)
                .returns(
                    TerminalException.INTERNAL_SERVER_ERROR_CODE, Protocol.ErrorMessage::getCode));
  }

  public static Consumer<? super MessageLite> errorMessageStartingWith(String str) {
    return errorMessage(
        msg ->
            assertThat(msg).extracting(Protocol.ErrorMessage::getMessage, STRING).startsWith(str));
  }

  public static Consumer<? super MessageLite> protocolExceptionErrorMessage(int code) {
    return errorMessage(
        msg ->
            assertThat(msg)
                .returns(code, Protocol.ErrorMessage::getCode)
                .extracting(Protocol.ErrorMessage::getMessage, STRING)
                .startsWith(ProtocolException.class.getCanonicalName()));
  }

  public static EndpointManifestSchemaAssert assertThatDiscovery(Object... services) {
    Endpoint.Builder builder = Endpoint.builder();
    for (var svc: services) {
      builder.bind(svc);
    }

    return new EndpointManifestSchemaAssert(
        new EndpointManifest(
                EndpointManifestSchema.ProtocolMode.BIDI_STREAM,
                builder.build().getServiceDefinitions(),
                true
        )
            .manifest(),
        EndpointManifestSchemaAssert.class);
  }

  public static class EndpointManifestSchemaAssert
      extends AbstractObjectAssert<EndpointManifestSchemaAssert, EndpointManifestSchema> {
    public EndpointManifestSchemaAssert(
        EndpointManifestSchema endpointManifestSchema, Class<?> selfType) {
      super(endpointManifestSchema, selfType);
    }

    public ServiceAssert extractingService(String service) {
      Optional<Service> svc =
          this.actual.getServices().stream().filter(s -> s.getName().equals(service)).findFirst();

      if (svc.isEmpty()) {
        fail(
            "Expecting deployment manifest to contain service {}. Available services: {}",
            service,
            this.actual.getServices().stream().map(Service::getName).collect(Collectors.toList()));
      }

      return new ServiceAssert(svc.get(), ServiceAssert.class);
    }
  }

  public static class ServiceAssert extends AbstractObjectAssert<ServiceAssert, Service> {
    public ServiceAssert(Service svc, Class<?> selfType) {
      super(svc, selfType);
    }

    public ObjectAssert<Handler> extractingHandler(String handlerName) {
      Optional<Handler> handler =
          this.actual.getHandlers().stream()
              .filter(s -> s.getName().equals(handlerName))
              .findFirst();

      if (handler.isEmpty()) {
        fail(
            "Expecting service {} manifest to contain handler {}. Available handler: {}",
            this.actual.getName(),
            handlerName,
            this.actual.getHandlers().stream().map(Handler::getName).collect(Collectors.toList()));
      }

      return assertThat(handler.get());
    }
  }
}
