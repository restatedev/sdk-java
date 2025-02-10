// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.endpoint.definition;

import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("rawtypes")
public final class ServiceDefinitionFactories {

  private static class ServiceDefinitionFactorySingleton {
    private static final ServiceDefinitionFactories INSTANCE = new ServiceDefinitionFactories();
  }

  private static final Logger LOG = LogManager.getLogger(ServiceDefinitionFactories.class);

  private final List<ServiceDefinitionFactory> factories;

  public ServiceDefinitionFactories() {
    this.factories = new ArrayList<>();

    var serviceLoaderIterator = ServiceLoader.load(ServiceDefinitionFactory.class).iterator();
    while (serviceLoaderIterator.hasNext()) {
      try {
        this.factories.add(serviceLoaderIterator.next());
      } catch (ServiceConfigurationError | Exception e) {
        LOG.debug(
            "Found service that cannot be loaded using service provider. "
                + "You can ignore this message during development.\n"
                + "This might be the result of using a compiler with incremental builds (e.g. IntelliJ IDEA) "
                + "that updated a dirty META-INF file after removing/renaming an annotated service.",
            e);
      }
    }
  }

  /** Resolve the code generated {@link ServiceDefinitionFactory} */
  @SuppressWarnings("unchecked")
  public static ServiceDefinitionFactory<Object, Object> discover(Object service) {
    if (service instanceof ServiceDefinitionFactory<?, ?>) {
      // We got this already
      return (ServiceDefinitionFactory<Object, Object>) service;
    }
    if (service instanceof ServiceDefinition<?>) {
      // We got this already
      return new ServiceDefinitionFactory<>() {
        @Override
        public ServiceDefinition<Object> create(Object serviceObject) {
          return (ServiceDefinition<Object>) serviceObject;
        }

        @Override
        public boolean supports(Object serviceObject) {
          return serviceObject == service;
        }
      };
    }
    return Objects.requireNonNull(
        ServiceDefinitionFactorySingleton.INSTANCE.discoverFactory(service),
        () ->
            "ServiceDefinitionFactory class not found for service "
                + service.getClass().getCanonicalName()
                + ". "
                + "Make sure the annotation processor is correctly configured to generate the ServiceDefinitionFactory, "
                + "and it generates the META-INF/services/"
                + ServiceDefinitionFactory.class.getCanonicalName()
                + " file containing the generated class. "
                + "If you're using fat jars, make sure the jar plugin correctly squashes all the META-INF/services files. "
                + "Found ServiceAdapter: "
                + ServiceDefinitionFactorySingleton.INSTANCE.factories);
  }

  private @Nullable ServiceDefinitionFactory discoverFactory(Object service) {
    return this.factories.stream().filter(sa -> sa.supports(service)).findFirst().orElse(null);
  }
}
