// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.upcasting;

import dev.restate.sdk.endpoint.definition.ServiceType;
import java.util.Map;

/**
 * Factory for producing {@link Upcaster} instances for a specific service. Implementations may
 * inspect the service name, service type, and optional metadata to decide which upcaster to return.
 *
 * @author Milan Savic
 */
public interface UpcasterFactory {

  /**
   * Creates a new {@link Upcaster} for the given service.
   *
   * @param serviceName the logical name of the service
   * @param serviceType the type of the service (SERVICE, VIRTUAL_OBJECT, WORKFLOW)
   * @param metadata optional metadata that can be used when selecting/configuring the upcaster
   * @return a non-null {@link Upcaster}
   */
  Upcaster newUpcaster(String serviceName, ServiceType serviceType, Map<String, String> metadata);
}
