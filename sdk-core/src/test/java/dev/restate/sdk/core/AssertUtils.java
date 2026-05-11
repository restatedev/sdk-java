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

import dev.restate.sdk.core.generated.manifest.EndpointManifestSchema;
import dev.restate.sdk.core.generated.manifest.Handler;
import dev.restate.sdk.core.generated.manifest.Service;
import dev.restate.sdk.endpoint.Endpoint;
import java.util.Optional;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractObjectAssert;
import org.assertj.core.api.ObjectAssert;

public class AssertUtils {

  public static EndpointManifestSchemaAssert assertThatDiscovery(Object... services) {
    Endpoint.Builder builder = Endpoint.builder();
    for (var svc : services) {
      builder.bind(svc);
    }

    return assertThatDiscovery(builder);
  }

  public static EndpointManifestSchemaAssert assertThatDiscovery(Endpoint.Builder builder) {
    return assertThatDiscovery(builder.build());
  }

  public static EndpointManifestSchemaAssert assertThatDiscovery(Endpoint endpoint) {
    return new EndpointManifestSchemaAssert(
        new EndpointManifest(endpoint.getServiceDefinitions(), true)
            .manifest(
                DiscoveryProtocol.Version.MAX, EndpointManifestSchema.ProtocolMode.BIDI_STREAM),
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
            "Expecting deployment manifest to contain service %s. Available services: %s",
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
            "Expecting service %s manifest to contain handler %s. Available handler: %s",
            this.actual.getName(),
            handlerName,
            this.actual.getHandlers().stream().map(Handler::getName).collect(Collectors.toList()));
      }

      return assertThat(handler.get());
    }
  }
}
