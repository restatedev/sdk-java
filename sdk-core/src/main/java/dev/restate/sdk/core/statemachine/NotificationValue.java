// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine;

import dev.restate.sdk.types.Slice;
import dev.restate.sdk.types.TerminalException;
import java.util.List;

public sealed interface NotificationValue {

  record Empty() implements NotificationValue {
    public static Empty INSTANCE = new Empty();
  }

  record Success(Slice slice) implements NotificationValue {}

  record Failure(TerminalException exception) implements NotificationValue {}

  record StateKeys(List<String> stateKeys) implements NotificationValue {}

  record InvocationId(String invocationId) implements NotificationValue {}
}
