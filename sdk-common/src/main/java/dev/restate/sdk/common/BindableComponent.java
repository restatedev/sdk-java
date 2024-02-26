// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.common;

import dev.restate.sdk.common.syscalls.ComponentDefinition;
import java.util.List;

/** Marker interface for a Restate component. */
public interface BindableComponent {
  default List<ComponentDefinition> definitions() {
    return null;
  }
}
