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
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.util.ContextDataProvider;

/**
 * Log4j2 ContextDataProvider inferring context from the Grpc context.
 *
 * <p>This is used to propagate the context to the user code, such that log statements from the user
 * will contain the restate logging context variables.
 */
public class GrpcContextDataProvider implements ContextDataProvider {
  @Override
  public Map<String, String> supplyContextData() {
    InvocationId invocationId = InvocationId.INVOCATION_ID_KEY.get();
    String serviceMethod = RestateGrpcServer.SERVICE_METHOD.get();

    var context = new HashMap<>(ThreadContext.getContext());

    if (invocationId != null) {
      context.put(
          RestateGrpcServer.LoggingContextSetter.INVOCATION_ID_KEY, invocationId.toString());
    }
    if (serviceMethod != null) {
      context.put(RestateGrpcServer.LoggingContextSetter.SERVICE_METHOD_KEY, serviceMethod);
    }

    return context;
  }
}
