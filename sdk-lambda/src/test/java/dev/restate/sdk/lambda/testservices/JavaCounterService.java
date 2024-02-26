// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.lambda.testservices;

import dev.restate.sdk.Component;
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.common.Serde;
import dev.restate.sdk.common.StateKey;
import io.grpc.stub.StreamObserver;
import java.nio.charset.StandardCharsets;

public class JavaCounterService extends JavaCounterGrpc.JavaCounterImplBase implements Component {

  public static final StateKey<Long> COUNTER =
      StateKey.of(
          "counter",
          Serde.using(
              l -> l.toString().getBytes(StandardCharsets.UTF_8),
              v -> Long.parseLong(new String(v, StandardCharsets.UTF_8))));

  @Override
  public void get(CounterRequest request, StreamObserver<GetResponse> responseObserver) {
    ObjectContext.current().get(COUNTER);

    throw new IllegalStateException("We shouldn't reach this point");
  }
}
