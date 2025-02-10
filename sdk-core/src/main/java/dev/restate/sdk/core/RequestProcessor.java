// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.common.Slice;
import dev.restate.sdk.endpoint.Endpoint;
import java.util.concurrent.Flow;

/** Resolved handler for an invocation. See {@link Endpoint} for more details. */
public interface RequestProcessor extends Flow.Processor<Slice, Slice> {
  int statusCode();

  String responseContentType();
}
