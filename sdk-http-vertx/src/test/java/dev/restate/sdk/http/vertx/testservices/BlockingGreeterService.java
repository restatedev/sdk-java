// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.http.vertx.testservices;

import dev.restate.sdk.RestateBlockingService;
import dev.restate.sdk.common.Serde;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.core.testservices.GreeterGrpc;
import dev.restate.sdk.core.testservices.GreetingRequest;
import dev.restate.sdk.core.testservices.GreetingResponse;
import io.grpc.stub.StreamObserver;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BlockingGreeterService extends GreeterGrpc.GreeterImplBase
    implements RestateBlockingService {

  private static final Logger LOG = LogManager.getLogger(BlockingGreeterService.class);
  public static final StateKey<Long> COUNTER =
      StateKey.of(
          "counter",
          Serde.using(
              l -> l.toString().getBytes(StandardCharsets.UTF_8),
              v -> Long.parseLong(new String(v, StandardCharsets.UTF_8))));

  @Override
  public void greet(GreetingRequest request, StreamObserver<GreetingResponse> responseObserver) {
    // restateContext() is invoked everytime to make sure context propagation works!

    LOG.info("Greet invoked!");

    var count = restateContext().get(COUNTER).orElse(0L) + 1;
    restateContext().set(COUNTER, count);

    restateContext().sleep(Duration.ofSeconds(1));

    responseObserver.onNext(
        GreetingResponse.newBuilder()
            .setMessage("Hello " + request.getName() + ". Count: " + count)
            .build());
    responseObserver.onCompleted();
  }
}
