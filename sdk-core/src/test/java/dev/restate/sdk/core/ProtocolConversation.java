// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLiteOrBuilder;
import com.google.protobuf.Parser;
import dev.restate.common.Slice;
import dev.restate.sdk.core.generated.protocol.Protocol;
import dev.restate.sdk.core.generated.protocolv7.ProtocolV7;
import dev.restate.sdk.core.legacy.MessageType;
import dev.restate.sdk.core.legacy.ProtoUtils;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.endpoint.HeadersAccessor;
import dev.restate.sdk.endpoint.definition.ServiceDefinition;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Drives one invocation over the raw service protocol step by step, like the runtime would: the
 * test decides what to send next after looking at what the SDK wrote. Unlike {@link
 * MockBidiStream}, which feeds a fixed input list and asserts on the final output, this lets a test
 * withhold input until a message arrives, so a message the SDK buffers instead of flushing makes
 * the test time out.
 *
 * <p>Besides the V6 messages known to {@link MessageType}, it encodes and decodes the V7 control
 * messages defined in {@code src/test/proto/dev/restate/service/protocol_v7_test.proto}.
 */
public final class ProtocolConversation implements AutoCloseable {

  static final String V6 = "application/vnd.restate.invocation.v6";
  static final String V7 = "application/vnd.restate.invocation.v7";

  private static final short AWAITING_ON_MESSAGE_TYPE = 0x0006;
  private static final short PROPOSE_RUN_COMPLETION_ACK_MESSAGE_TYPE = 0x0007;
  private static final long TIMEOUT_SECONDS = 10;
  private static final Object OUTPUT_CLOSED = new Object();

  private final ExecutorService coreExecutor = Executors.newSingleThreadExecutor();
  // Decoded messages, then OUTPUT_CLOSED or a Throwable. Written on the core thread.
  private final BlockingQueue<Object> output = new LinkedBlockingQueue<>();
  private final boolean v7;
  private final RequestProcessor processor;
  // Bytes of an output frame split across chunks. Only accessed on the core thread.
  private ByteBuffer partialFrame = ByteBuffer.allocate(0);

  /**
   * Starts a V7 conversation with the native (FFM) state machine, the only one implementing V7.
   * Aborts the calling test if it isn't available (JDK < 23, or no native library for this
   * platform).
   */
  public static ProtocolConversation v7(ServiceDefinition service, String handler)
      throws Exception {
    assumeTrue(
        StateMachineFactory.Loader.FFM_FACTORY != null,
        "the native state machine is unavailable (needs JDK 23+ and the native library)");
    return new ProtocolConversation(service, handler, StateMachineFactory.Loader.FFM_FACTORY, V7);
  }

  ProtocolConversation(
      ServiceDefinition service,
      String handler,
      StateMachineFactory stateMachineFactory,
      String contentType)
      throws Exception {
    this.v7 = V7.equals(contentType);
    EndpointRequestHandler server =
        new EndpointRequestHandler(
            null, Endpoint.builder().bind(service).build(), stateMachineFactory);
    // Same wiring as MockBidiStream: create the processor and attach the output before any input,
    // all on the single core thread.
    this.processor =
        coreExecutor
            .submit(
                () -> {
                  RequestProcessor p =
                      server.processorForRequest(
                          "/" + service.getServiceName() + "/" + handler,
                          HeadersAccessor.wrap(Map.of("content-type", contentType)),
                          EndpointRequestHandler.LoggingContextSetter.THREAD_LOCAL_INSTANCE,
                          coreExecutor,
                          true);
                  p.subscribe(new OutputSubscriber());
                  p.onSubscribe(
                      new Flow.Subscription() {
                        @Override
                        public void request(long n) {}

                        @Override
                        public void cancel() {}
                      });
                  return p;
                })
            .get();
  }

  // --- Input

  /** Sends the given messages and waits until the core thread has consumed them. */
  public void send(MessageLiteOrBuilder... messages) throws Exception {
    List<Slice> frames = new ArrayList<>();
    for (MessageLiteOrBuilder message : messages) {
      frames.add(Slice.wrap(encode(ProtoUtils.build(message))));
    }
    coreExecutor.submit(() -> frames.forEach(processor::onNext)).get();
  }

  /** Closes the input stream, as the runtime does to make the SDK suspend. */
  public void closeInput() throws Exception {
    coreExecutor.submit(processor::onComplete).get();
  }

  // --- Output

  /** Returns the next output message; fails if none arrives in time or the output was closed. */
  public MessageLite next() throws InterruptedException {
    Object o = output.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    if (o == null) {
      return fail("No output within %ss (was it buffered and never flushed?)", TIMEOUT_SECONDS);
    }
    if (o == OUTPUT_CLOSED) {
      return fail("Output stream closed, expected another message");
    }
    if (o instanceof Throwable t) {
      return fail("Output stream failed", t);
    }
    return (MessageLite) o;
  }

  /** Returns the next output message, asserting it is of the given type. */
  public <T extends MessageLite> T expect(Class<T> type) throws InterruptedException {
    MessageLite message = next();
    assertThat(message).isInstanceOf(type);
    return type.cast(message);
  }

  /**
   * Waits until the core thread has run everything queued so far, then asserts the SDK wrote
   * nothing more. The SDK writes output only from the core thread.
   */
  public void expectNoOutputUntilIdle() throws Exception {
    coreExecutor.submit(() -> {}).get();
    assertThat(output).isEmpty();
  }

  public void expectOutputClosed() throws InterruptedException {
    assertThat(output.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        .as("output stream completion")
        .isSameAs(OUTPUT_CLOSED);
  }

  @Override
  public void close() throws Exception {
    coreExecutor.shutdown();
    assertThat(coreExecutor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
  }

  // --- Message builders and AwaitingOn tree accessors

  public static Protocol.SleepCompletionNotificationMessage sleepCompletion(int completionId) {
    return Protocol.SleepCompletionNotificationMessage.newBuilder()
        .setCompletionId(completionId)
        .setVoid(Protocol.Void.getDefaultInstance())
        .build();
  }

  public static ProtocolV7.ProposeRunCompletionAckMessage proposeRunCompletionAck(
      int completionId) {
    return ProtocolV7.ProposeRunCompletionAckMessage.newBuilder()
        .setCompletionId(completionId)
        .build();
  }

  /** Every completion id in the tree. */
  public static List<Integer> completionIds(ProtocolV7.Future future) {
    List<Integer> ids = new ArrayList<>(future.getWaitingCompletionsList());
    future.getNestedFuturesList().forEach(nested -> ids.addAll(completionIds(nested)));
    return ids;
  }

  /** Every combinator type in the tree, root first. */
  public static List<ProtocolV7.CombinatorType> combinatorTypes(ProtocolV7.Future future) {
    List<ProtocolV7.CombinatorType> types = new ArrayList<>(List.of(future.getCombinatorType()));
    future.getNestedFuturesList().forEach(nested -> types.addAll(combinatorTypes(nested)));
    return types;
  }

  // --- Framing: 8-byte header (u16 type, u16 flags, u32 length), then the message

  private static ByteBuffer encode(MessageLite message) {
    short type =
        message instanceof ProtocolV7.ProposeRunCompletionAckMessage
            ? PROPOSE_RUN_COMPLETION_ACK_MESSAGE_TYPE
            : MessageType.fromMessage(message).encode();
    byte[] body = message.toByteArray();
    ByteBuffer frame = ByteBuffer.allocate(8 + body.length).order(ByteOrder.BIG_ENDIAN);
    frame.putLong(((long) type << 48) | body.length);
    frame.put(body);
    return frame.flip();
  }

  private Parser<? extends MessageLite> parserFor(short type) {
    if (type == AWAITING_ON_MESSAGE_TYPE) {
      return ProtocolV7.AwaitingOnMessage.parser();
    }
    if (type == MessageType.SuspensionMessage_TYPE && v7) {
      return ProtocolV7.SuspensionMessage.parser();
    }
    return MessageType.decode(type).messageParser();
  }

  private final class OutputSubscriber implements Flow.Subscriber<Slice> {
    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(Slice chunk) {
      ByteBuffer in = chunk.asReadOnlyByteBuffer();
      ByteBuffer buf = ByteBuffer.allocate(partialFrame.remaining() + in.remaining());
      buf.put(partialFrame).put(in).flip();
      try {
        while (buf.remaining() >= 8) {
          long header = buf.getLong(buf.position());
          int length = (int) header;
          if (buf.remaining() < 8 + length) {
            break;
          }
          buf.position(buf.position() + 8);
          byte[] body = new byte[length];
          buf.get(body);
          output.add(parserFor((short) (header >>> 48)).parseFrom(body));
        }
      } catch (InvalidProtocolBufferException | RuntimeException e) {
        output.add(e);
      }
      partialFrame = buf.slice();
    }

    @Override
    public void onError(Throwable throwable) {
      output.add(throwable);
    }

    @Override
    public void onComplete() {
      output.add(OUTPUT_CLOSED);
    }
  }
}
