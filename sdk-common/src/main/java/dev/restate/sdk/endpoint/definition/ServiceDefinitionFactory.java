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
}
