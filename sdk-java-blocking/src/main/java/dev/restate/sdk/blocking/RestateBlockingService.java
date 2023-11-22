// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.blocking;

import dev.restate.sdk.core.BindableBlockingService;
import dev.restate.sdk.core.syscalls.Syscalls;

/**
 * Marker interface for Restate blocking services.
 *
 * <p>
 *
 * <h2>Error handling</h2>
 *
 * The error handling of Restate services works as follows:
 *
 * <ul>
 *   <li>When throwing {@link dev.restate.sdk.core.TerminalException}, the failure will be used as
 *       invocation response error value
 *   <li>When throwing any other type of exception, the failure is considered "non-terminal" and the
 *       runtime will retry it, according to its configuration
 * </ul>
 */
public interface RestateBlockingService extends BindableBlockingService {

  /**
   * @return an instance of the {@link RestateContext}.
   */
  default RestateContext restateContext() {
    return RestateContext.fromSyscalls(Syscalls.current());
  }
}
