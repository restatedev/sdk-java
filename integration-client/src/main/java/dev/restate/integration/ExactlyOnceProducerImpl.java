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

/** Exactly-once {@link ExactlyOnceProducer}: caller-supplied, strictly-increasing offsets. */
final class ExactlyOnceProducerImpl extends AbstractProducer implements ExactlyOnceProducer {

  ExactlyOnceProducerImpl(
      IngestionSvcGrpc.IngestionSvcStub stub,
      String producerId,
      ProducerOptions options,
      String integration) {
    super(stub, producerId, DeduplicationMode.OFFSET_BASED, options, integration);
  }

  @Override
  public SendAttempt trySend(long offset, Invocation invocation) {
    acquire();
    try {
      checkOffset(offset);
      return doTrySend(offset, (InvocationImpl) invocation);
    } finally {
      release();
    }
  }

  @Override
  public CompletableFuture<SendResult> send(long offset, Invocation invocation)
      throws ProducerBufferExhaustedException {
    acquire();
    try {
      checkOffset(offset);
      return doSend(offset, (InvocationImpl) invocation);
    } finally {
      release();
    }
  }

  private void checkOffset(long offset) {
    if (offset <= lastSent) {
      throw new IllegalArgumentException(
          "offset must be strictly increasing; last sent " + lastSent + ", got " + offset);
    }
  }
}
