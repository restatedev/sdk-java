// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.integration;

import io.grpc.Channel;
import java.util.Objects;
import org.jetbrains.annotations.ApiStatus;

/**
 * Internal bridge for first-party integrations that supply their own gRPC {@link Channel}.
 *
 * <p>The supplied channel remains owned by the caller and is not shut down when the client is
 * closed.
 *
 * @hidden
 */
@ApiStatus.Internal
public final class GrpcIntegrationClient {

  private GrpcIntegrationClient() {}

  public static IntegrationClient.Builder builder(Channel channel) {
    Objects.requireNonNull(channel, "channel");
    return new IntegrationClient.Builder(
        (authToken, integration) -> IntegrationClientImpl.create(channel, authToken, integration));
  }
}
