// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine;

import org.jspecify.annotations.Nullable;

/** Used in `hitError` to specify which command this error relates to. */
sealed interface CommandRelationship {
  /** The error is related to the last command. */
  record Last() implements CommandRelationship {
    public static final Last INSTANCE = new Last();
  }

  /** The error is related to the next command of the specified type. */
  record Next(CommandType type, @Nullable String name) implements CommandRelationship {}

  /** The error is related to a specific command. */
  record Specific(int commandIndex, CommandType type, @Nullable String name)
      implements CommandRelationship {}
}
