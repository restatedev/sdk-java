// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.integration;

import dev.restate.ingestion.v1.DeduplicationMode;
import dev.restate.ingestion.v1.IngestionSvcGrpc;
import java.util.concurrent.CompletableFuture;

/** At-least-once {@link Producer}: dedup disabled, client-assigned monotonic offsets. */
final class ProducerImpl extends AbstractProducer implements Producer {

  ProducerImpl(
      IngestionSvcGrpc.IngestionSvcStub stub, ProducerOptions options, String integration) {
    super(stub, "", DeduplicationMode.DISABLED, options, integration);
  }

  @Override
  public CompletableFuture<SendResult> send(Invocation invocation)
      throws ProducerNotReadyException {
    acquire();
    try {
      return doSend(lastSent + 1, (InvocationImpl) invocation);
    } finally {
      release();
    }
  }

  @Override
  public SendAttempt trySend(Invocation invocation) {
    acquire();
    try {
      return doTrySend(lastSent + 1, (InvocationImpl) invocation);
    } finally {
      release();
    }
  }
}
