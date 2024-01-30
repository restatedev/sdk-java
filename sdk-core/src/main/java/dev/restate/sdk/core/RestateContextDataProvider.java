// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.sdk.common.InvocationId;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.core.util.ContextDataProvider;

/**
 * Log4j2 ContextDataProvider inferring context from the Grpc context.
 *
 * <p>This is used to propagate the context to the user code, such that log statements from the user
 * will contain the restate logging context variables.
 *
 * <p>This is based on grpc Context due to the fact that it's the only guaranteed thread
 * local/context we can rely on that is always available in the user code.
 */
public class RestateContextDataProvider implements ContextDataProvider {
  @Override
  public Map<String, String> supplyContextData() {
    InvocationId invocationId = InvocationId.INVOCATION_ID_KEY.get();
    SyscallsInternal syscalls = (SyscallsInternal) SyscallsInternal.SYSCALLS_KEY.get();

    if (invocationId == null) {
      return Collections.emptyMap();
    }

    // We can't use the immutable MapN implementation from Map.of because of
    // https://github.com/apache/logging-log4j2/issues/2098
    HashMap<String, String> m = new HashMap<>();
    m.put(RestateEndpoint.LoggingContextSetter.INVOCATION_ID_KEY, invocationId.toString());
    m.put(
        RestateEndpoint.LoggingContextSetter.SERVICE_METHOD_KEY,
        syscalls.getFullyQualifiedMethodName());
    m.put(
        RestateEndpoint.LoggingContextSetter.SERVICE_INVOCATION_STATUS_KEY,
        syscalls.getInvocationState().toString());
    return m;
  }
}
