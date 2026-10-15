// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.javaapi;

import static dev.restate.sdk.core.AssertUtils.containsOnly;
import static dev.restate.sdk.core.AssertUtils.errorMessage;
import static dev.restate.sdk.core.javaapi.JavaAPITests.callGreeterGreetService;
import static dev.restate.sdk.core.legacy.ProtoUtils.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.MessageLite;
import dev.restate.common.Slice;
import dev.restate.common.function.ThrowingBiFunction;
import dev.restate.sdk.Context;
import dev.restate.sdk.DurableFuture;
import dev.restate.sdk.HandlerRunner;
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.core.*;
import dev.restate.sdk.core.TestDefinitions.TestDefinition;
import dev.restate.sdk.core.TestDefinitions.TestExecutor;
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import dev.restate.sdk.core.TestDefinitions.TestSuite;
import dev.restate.sdk.core.generated.protocol.Protocol;
import dev.restate.sdk.core.legacy.InvocationInput;
import dev.restate.sdk.endpoint.definition.HandlerDefinition;
import dev.restate.sdk.endpoint.definition.HandlerType;
import dev.restate.sdk.endpoint.definition.ServiceDefinition;
import dev.restate.sdk.endpoint.definition.ServiceType;
import dev.restate.serde.PayloadCodec;
import dev.restate.serde.Serde;
import dev.restate.serde.jackson.JacksonSerdeFactory;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.condition.EnabledIf;

/** Payload codecs are supported only by the native state machine. */
@EnabledIf("dev.restate.sdk.core.javaapi.PayloadCodecTest#isNativeStateMachineAvailable")
public class PayloadCodecTest extends TestRunner {

  public static boolean isNativeStateMachineAvailable() {
    return MockBidiStream.getExecutors().anyMatch(PayloadCodecTest::isNativeExecutor);
  }

  public static boolean isNativeExecutor(TestExecutor executor) {
    return executor.toString().endsWith("/FFM") && executor.toString().contains("Mock");
  }

  @Override
  protected Stream<TestExecutor> executors() {
    return Stream.concat(MockRequestResponse.getExecutors(), MockBidiStream.getExecutors())
        .filter(PayloadCodecTest::isNativeExecutor);
  }

  @Override
  protected Stream<TestSuite> definitions() {
    return Stream.of(new Suite());
  }

  /** Prepends a prefix when encoding, checks and strips it when decoding. */
  public static final class PrefixCodec implements PayloadCodec {
    private final byte[] prefix;
    private final boolean deterministic;

    public PrefixCodec(String prefix, boolean deterministic) {
      this.prefix = prefix.getBytes(StandardCharsets.UTF_8);
      this.deterministic = deterministic;
    }

    @Override
    public CompletableFuture<Slice> encode(Slice value) {
      // Complete on another thread, to check the SDK trampolines back correctly
      return CompletableFuture.supplyAsync(
          () -> Slice.wrap(addPrefix(prefix, value.toByteArray())));
    }

    @Override
    public CompletableFuture<Slice> decode(Slice value) {
      return CompletableFuture.supplyAsync(
          () -> Slice.wrap(removePrefix(prefix, value.toByteArray())));
    }

    @Override
    public boolean isEncodingDeterministic() {
      return deterministic;
    }
  }

  /** Decodes like {@link PrefixCodec}, but fails to encode. */
  public static final class FailingEncodeCodec implements PayloadCodec {
    public static final String MESSAGE = "Cannot encode";

    private final PayloadCodec delegate = new PrefixCodec(PREFIX, false);

    @Override
    public CompletableFuture<Slice> encode(Slice value) {
      return CompletableFuture.failedFuture(new IllegalStateException(MESSAGE));
    }

    @Override
    public CompletableFuture<Slice> decode(Slice value) {
      return delegate.decode(value);
    }
  }

  static byte[] addPrefix(byte[] prefix, byte[] value) {
    byte[] result = Arrays.copyOf(prefix, prefix.length + value.length);
    System.arraycopy(value, 0, result, prefix.length, value.length);
    return result;
  }

  static byte[] removePrefix(byte[] prefix, byte[] value) {
    if (value.length < prefix.length
        || !Arrays.equals(Arrays.copyOf(value, prefix.length), prefix)) {
      throw new IllegalStateException("Value is not encoded");
    }
    return Arrays.copyOfRange(value, prefix.length, value.length);
  }

  /** {@link TestSerdes#STRING} followed by the {@link PrefixCodec} encoding. */
  public static Serde<String> encodedString(String prefix) {
    byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
    return Serde.using(
        s -> addPrefix(prefixBytes, TestSerdes.STRING.serialize(s).toByteArray()),
        b -> TestSerdes.STRING.deserialize(Slice.wrap(removePrefix(prefixBytes, b))));
  }

  public static final String PREFIX = "enc:";
  public static final Serde<String> ENC = encodedString(PREFIX);

  static <T, R, CTX extends Context> TestInvocationBuilder testDefinition(
      ServiceType serviceType,
      HandlerType handlerType,
      Serde<T> reqSerde,
      Serde<R> resSerde,
      ThrowingBiFunction<CTX, T, R> runner) {
    return TestDefinitions.testInvocation(
        ServiceDefinition.of(
            "CodecService",
            serviceType,
            List.of(
                HandlerDefinition.of(
                    "run",
                    handlerType,
                    reqSerde,
                    resSerde,
                    HandlerRunner.of(runner, new JacksonSerdeFactory(), null)))),
        "run");
  }

  static <T, R> TestInvocationBuilder service(
      Serde<T> reqSerde, Serde<R> resSerde, ThrowingBiFunction<Context, T, R> runner) {
    return testDefinition(ServiceType.SERVICE, HandlerType.SHARED, reqSerde, resSerde, runner);
  }

  static <T, R> TestInvocationBuilder virtualObject(
      Serde<T> reqSerde, Serde<R> resSerde, ThrowingBiFunction<ObjectContext, T, R> runner) {
    return testDefinition(
        ServiceType.VIRTUAL_OBJECT, HandlerType.EXCLUSIVE, reqSerde, resSerde, runner);
  }

  /** Runs the given test definition with the codec configured on the endpoint. */
  record WithEndpointCodec(TestDefinition delegate, PayloadCodec codec) implements TestDefinition {
    @Override
    public ServiceDefinition getServiceDefinition() {
      return delegate.getServiceDefinition();
    }

    @Override
    public dev.restate.sdk.endpoint.definition.HandlerRunner.Options getServiceOptions() {
      return delegate.getServiceOptions();
    }

    @Override
    public String getMethod() {
      return delegate.getMethod();
    }

    @Override
    public boolean isOnlyUnbuffered() {
      return delegate.isOnlyUnbuffered();
    }

    @Override
    public boolean isEnablePreviewContext() {
      return delegate.isEnablePreviewContext();
    }

    @Override
    public PayloadCodec getPayloadCodec() {
      return codec;
    }

    @Override
    public List<InvocationInput> getInput() {
      return delegate.getInput();
    }

    @Override
    public Consumer<List<MessageLite>> getOutputAssert() {
      return delegate.getOutputAssert();
    }

    @Override
    public String getTestCaseName() {
      return delegate.getTestCaseName();
    }

    @Override
    public String getInvalidReason() {
      return delegate.getInvalidReason();
    }
  }

  public static TestDefinition withCodec(TestDefinition definition) {
    return withCodec(new PrefixCodec(PREFIX, false), definition);
  }

  public static TestDefinition withCodec(PayloadCodec codec, TestDefinition definition) {
    return new WithEndpointCodec(definition, codec);
  }

  static final StateKey<String> STATE = StateKey.of("STATE", String.class);

  static final class Suite implements TestSuite {
    @Override
    public Stream<TestDefinition> definitions() {
      return Stream.of(
          withCodec(
              service(TestSerdes.STRING, TestSerdes.STRING, (ctx, input) -> "Hello " + input)
                  .withInput(startMessage(1), inputCmd(ENC, "Till"))
                  .expectingOutput(outputCmd(ENC, "Hello Till"), END_MESSAGE)
                  .named("Input and output")),
          withCodec(
              service(TestSerdes.STRING, TestSerdes.STRING, (ctx, input) -> "Hello " + input)
                  .withInput(startMessage(1), inputCmd("Till"))
                  .assertingOutput(
                      containsOnly(
                          errorMessage(
                              msg ->
                                  assertThat(msg.getMessage()).contains("Value is not encoded"))))
                  .named("Input not encoded fails")),
          withCodec(
              PayloadCodec.NOOP,
              service(TestSerdes.STRING, TestSerdes.STRING, (ctx, input) -> "Hello " + input)
                  .withInput(startMessage(1), inputCmd("Till"))
                  .expectingOutput(outputCmd("Hello Till"), END_MESSAGE)
                  .named("Noop codec passes values through")),
          withCodec(
              virtualObject(
                      TestSerdes.STRING,
                      TestSerdes.STRING,
                      (ctx, input) -> {
                        String state = ctx.get(STATE).orElseThrow();
                        ctx.set(STATE, input);
                        return "Hello " + state;
                      })
                  .withInput(
                      startMessage(3, "my-key"),
                      inputCmd(ENC, "Till"),
                      getLazyStateCmd(1, "STATE"),
                      getLazyStateCompletion(1, ENC, "Francesco"))
                  .expectingOutput(
                      setStateCmd("STATE", ENC, "Till"),
                      outputCmd(ENC, "Hello Francesco"),
                      END_MESSAGE)
                  .named("Get and set state")),
          // A completion that can't be decoded fails the invocation (retryable), and the error
          // doesn't reach the handler code
          withCodec(
              virtualObject(
                      TestSerdes.STRING,
                      TestSerdes.STRING,
                      (ctx, input) -> "Hello " + ctx.get(STATE).orElseThrow())
                  .withInput(
                      startMessage(3, "my-key"),
                      inputCmd(ENC, "Till"),
                      getLazyStateCmd(1, "STATE"),
                      getLazyStateCompletion(1, "Francesco"))
                  .assertingOutput(
                      containsOnly(
                          errorMessage(
                              msg ->
                                  assertThat(msg.getMessage()).contains("Value is not encoded"))))
                  .named("State value not encoded fails")),
          withCodec(
              service(
                      Serde.VOID,
                      TestSerdes.STRING,
                      (ctx, unused) -> {
                        try {
                          return callGreeterGreetService(ctx, "Francesco").await();
                        } catch (RuntimeException e) {
                          // The decoding failure must not be catchable by user code
                          return "Caught " + e;
                        }
                      })
                  .withInput(
                      startMessage(3),
                      inputCmd(ENC, ""),
                      callCmd(1, 2, GREETER_SERVICE_TARGET, ENC, "Francesco"),
                      callCompletion(2, "Hello Francesco"))
                  .onlyBidiStream()
                  .assertingOutput(
                      containsOnly(
                          errorMessage(
                              msg ->
                                  assertThat(msg.getMessage()).contains("Value is not encoded"))))
                  .named("Call result not encoded fails")),
          // any(a1, all(a2, a3), a4) must be deterministic, regardless of how long decoding takes:
          // a2 and a3 are completed before a1, so all(a2, a3) wins
          withCodec(
              service(
                      Serde.VOID,
                      TestSerdes.STRING,
                      (ctx, unused) -> {
                        DurableFuture<String> a1 = ctx.awakeable(TestSerdes.STRING);
                        DurableFuture<String> a2 = ctx.awakeable(TestSerdes.STRING);
                        DurableFuture<String> a3 = ctx.awakeable(TestSerdes.STRING);
                        DurableFuture<String> a4 = ctx.awakeable(TestSerdes.STRING);
                        int index = DurableFuture.any(a1, DurableFuture.all(a2, a3), a4).await();
                        return index + ":" + a2.await() + a3.await();
                      })
                  .withInput(
                      startMessage(5),
                      inputCmd(ENC, ""),
                      signalNotification(19, ENC, "3"),
                      signalNotification(18, ENC, "2"),
                      signalNotification(17, ENC, "1"),
                      signalNotification(20, ENC, "4"))
                  .expectingOutput(outputCmd(ENC, "1:23"), END_MESSAGE)
                  .named("Await any is deterministic with async decoding")),
          // The same future awaited twice in the same combinator: the notification must be taken
          // and
          // decoded only once
          withCodec(
              service(
                      Serde.VOID,
                      TestSerdes.STRING,
                      (ctx, unused) -> {
                        DurableFuture<String> a1 = ctx.awakeable(TestSerdes.STRING);
                        int index = DurableFuture.any(a1, a1).await();
                        DurableFuture.all(a1, a1).await();
                        return index + ":" + a1.await();
                      })
                  .withInput(startMessage(2), inputCmd(ENC, ""), signalNotification(17, ENC, "1"))
                  .expectingOutput(outputCmd(ENC, "0:1"), END_MESSAGE)
                  .named("Same future awaited twice while decoding")),
          withCodec(
              service(
                      Serde.VOID,
                      TestSerdes.STRING,
                      (ctx, unused) -> callGreeterGreetService(ctx, "Francesco").await())
                  .withInput(
                      startMessage(1), inputCmd(ENC, ""), callCompletion(2, ENC, "Hello Francesco"))
                  .onlyBidiStream()
                  .expectingOutput(
                      callCmd(1, 2, GREETER_SERVICE_TARGET, ENC, "Francesco"),
                      outputCmd(ENC, "Hello Francesco"),
                      END_MESSAGE)
                  .named("Call")),
          withCodec(
              service(
                      Serde.VOID,
                      TestSerdes.STRING,
                      (ctx, unused) -> "Hello " + ctx.run(String.class, () -> "Francesco"))
                  .withInput(startMessage(1), inputCmd(ENC, ""))
                  .expectingOutput(
                      runCmd(1), proposeRunCompletion(1, ENC, "Francesco"), suspensionMessage(1))
                  .named("Run proposes encoded value")),
          withCodec(
              service(
                      Serde.VOID,
                      TestSerdes.STRING,
                      (ctx, unused) -> "Hello " + ctx.run(String.class, () -> "Francesco"))
                  .withInput(
                      startMessage(3),
                      inputCmd(ENC, ""),
                      runCmd(1),
                      runCompletion(1, ENC, "Francesco"))
                  .onlyBidiStream()
                  .expectingOutput(outputCmd(ENC, "Hello Francesco"), END_MESSAGE)
                  .named("Run replay decodes value")),
          // Encoding failures are not run failures: no run completion is proposed, and the
          // invocation fails (retryable)
          withCodec(
              new FailingEncodeCodec(),
              service(
                      Serde.VOID,
                      TestSerdes.STRING,
                      (ctx, unused) -> "Hello " + ctx.run(String.class, () -> "Francesco"))
                  .withInput(startMessage(1), inputCmd(ENC, ""))
                  .assertingOutput(
                      messages ->
                          assertThat(messages)
                              .satisfiesExactly(
                                  msg -> assertThat(msg).isEqualTo(runCmd(1)),
                                  errorMessage(
                                      msg ->
                                          assertThat(msg.getMessage())
                                              .contains(FailingEncodeCodec.MESSAGE))))
                  .named("Run result encoding failure fails the invocation")),
          withCodec(
              service(
                      TestSerdes.STRING,
                      TestSerdes.STRING,
                      (ctx, input) -> TestSerdes.STRING.deserialize(ctx.request().body()))
                  .withInput(startMessage(1), inputCmd(ENC, "Till"))
                  .expectingOutput(outputCmd(ENC, "Till"), END_MESSAGE)
                  .named("Request body is decoded")),
          // The journal contains a different encoded value: with a non-deterministic codec the
          // payload is not compared, so there's no journal mismatch
          withCodec(
              new PrefixCodec(PREFIX, false),
              setStateReplay()
                  .assertingOutput(
                      messages ->
                          assertThat(messages)
                              .containsExactly(outputCmd(ENC, "Hello Till"), END_MESSAGE))
                  .named("Non-deterministic codec skips payload checks on replay")),
          // Same as above, but the codec is deterministic, so payload checks are still performed
          withCodec(
              new PrefixCodec(PREFIX, true),
              setStateReplay()
                  .assertingOutput(
                      containsOnly(
                          errorMessage(
                              msg ->
                                  assertThat(msg)
                                      .extracting(Protocol.ErrorMessage::getCode)
                                      .isEqualTo(570))))
                  .named("Deterministic codec keeps payload checks on replay")));
    }

    private static TestDefinitions.WithInputBuilder setStateReplay() {
      return virtualObject(
              TestSerdes.STRING,
              TestSerdes.STRING,
              (ctx, input) -> {
                ctx.set(STATE, input);
                return "Hello " + input;
              })
          .withInput(
              startMessage(2, "my-key"),
              inputCmd(ENC, "Till"),
              setStateCmd("STATE", encodedString("other:"), "Till"));
    }
  }
}
