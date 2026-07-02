// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package my.restate.sdk.examples;

import dev.restate.sdk.DurableFuture;
import dev.restate.sdk.Restate;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Service;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.http.vertx.RestateHttpServer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Spawns N concurrent {@code ctx.run} steps, each producing a large random payload (100KB–2MB),
 * with ~1-in-4 retryable failures sprinkled in. Returns the concatenation of all the payloads.
 *
 * <p>Useful to exercise cooperative suspension and AwaitingOnMessage with a non-trivial {@code
 * AllSucceededOrFirstFailed} combinator that the runtime can observe while runs are in flight.
 */
@Service
public class ConcurrentRuns {

  private static final Logger LOG = LogManager.getLogger(ConcurrentRuns.class);

  private static final int NUM_RUNS = 6;
  private static final int MIN_PAYLOAD_BYTES = 100 * 1024;
  private static final int MAX_PAYLOAD_BYTES = 2 * 1024 * 1024;
  private static final int FAILURE_DENOMINATOR = 4;

  private static final String ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

  @Handler
  public String run() {
    List<DurableFuture<String>> futures = new ArrayList<>(NUM_RUNS);
    for (int i = 0; i < NUM_RUNS; i++) {
      final int idx = i;
      futures.add(
          Restate.runAsync(
              "payload-" + idx,
              String.class,
              () -> {
                if (ThreadLocalRandom.current().nextInt(FAILURE_DENOMINATOR) == 0) {
                  LOG.info("Run {} simulating retryable failure", idx);
                  throw new RuntimeException("simulated retryable failure on run " + idx);
                }
                int size =
                    ThreadLocalRandom.current().nextInt(MIN_PAYLOAD_BYTES, MAX_PAYLOAD_BYTES + 1);
                LOG.info("Run {} generating {} bytes", idx, size);
                return randomString(size);
              }));
    }

    DurableFuture.all((List) futures).await();

    StringBuilder sb = new StringBuilder();
    for (DurableFuture<String> f : futures) {
      sb.append(f.await());
    }
    return sb.toString();
  }

  private static String randomString(int size) {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    char[] buf = new char[size];
    for (int i = 0; i < size; i++) {
      buf[i] = ALPHABET.charAt(rnd.nextInt(ALPHABET.length()));
    }
    return new String(buf);
  }

  public static void main(String[] args) {
    RestateHttpServer.listen(Endpoint.bind(new ConcurrentRuns()).build());
  }
}
