// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.sdk.common.syscalls.HandlerRunner;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.core.util.ContextDataProvider;

/**
 * Log4j2 {@link ContextDataProvider} inferring context from {@link
 * HandlerRunner#SYSCALLS_THREAD_LOCAL}.
 *
 * <p>This is used to propagate the context to the user code, such that log statements from the user
 * will contain the restate logging context variables.
 */
public class RestateContextDataProvider implements ContextDataProvider {
  @Override
  public Map<String, String> supplyContextData() {
    SyscallsInternal syscalls = (SyscallsInternal) HandlerRunner.SYSCALLS_THREAD_LOCAL.get();
    if (syscalls == null) {
      return Collections.emptyMap();
    }

    // We can't use the immutable MapN implementation from Map.of because of
    // https://github.com/apache/logging-log4j2/issues/2098
    HashMap<String, String> m = new HashMap<>(3);
    m.put(
        RestateEndpoint.LoggingContextSetter.INVOCATION_ID_KEY,
        syscalls.request().invocationId().toString());
    m.put(
        RestateEndpoint.LoggingContextSetter.INVOCATION_TARGET_KEY,
        syscalls.getFullyQualifiedMethodName());
    m.put(
        RestateEndpoint.LoggingContextSetter.INVOCATION_STATUS_KEY,
        syscalls.getInvocationState().toString());
    return m;
  }
}
