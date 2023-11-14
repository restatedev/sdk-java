package dev.restate.sdk.testing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.restate.sdk.examples.generated.CounterGrpc;
import dev.restate.sdk.examples.generated.CounterRequest;
import dev.restate.sdk.examples.generated.GetResponse;
import io.grpc.ManagedChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CounterTest {

  @RegisterExtension
  private static final RestateRunner restateRunner =
      RestateRunnerBuilder.create().withService(new Counter()).buildRunner();

  @Test
  void testGreet(@RestateGrpcChannel ManagedChannel channel) {
    CounterGrpc.CounterBlockingStub client = CounterGrpc.newBlockingStub(channel);
    GetResponse response = client.get(CounterRequest.getDefaultInstance());

    assertThat(response.getValue()).isEqualTo(0);
  }
}
