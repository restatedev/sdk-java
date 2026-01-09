// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.endpoint.definition;

import org.jspecify.annotations.Nullable;

public interface ServiceDefinitionFactory<T> {

  ServiceDefinition create(T serviceObject, HandlerRunner.@Nullable Options overrideHandlerOptions);

  boolean supports(Object serviceObject);

  /**
   * Get the priority of this factory. Lower values are tried first. The default priority is
   * HIGHEST_PRIORITY.
   *
   * <p>Code-generated factories should use the default priority so they are tried first.
   *
   * @return the priority value
   */
  default int priority() {
    return HIGHEST_PRIORITY;
  }

  int HIGHEST_PRIORITY = Integer.MIN_VALUE;

  int LOWEST_PRIORITY = Integer.MAX_VALUE;
}
