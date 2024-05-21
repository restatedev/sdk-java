// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static dev.restate.sdk.core.Util.nioBufferToProtobufBuffer;

import com.google.protobuf.ByteString;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.common.Request;
import dev.restate.sdk.common.Target;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.common.syscalls.*;
import dev.restate.sdk.core.DeferredResults.SingleDeferredInternal;
import dev.restate.sdk.core.Entries.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

public final class SyscallsImpl implements SyscallsInternal {

  private static final Logger LOG = LogManager.getLogger(SyscallsImpl.class);

  private final Request request;
  private final InvocationStateMachine stateMachine;

  SyscallsImpl(Request request, InvocationStateMachine stateMachine) {
    this.request = request;
    this.stateMachine = stateMachine;
  }

  @Override
  public String objectKey() {
    return this.stateMachine.objectKey();
  }

  @Override
  public Request request() {
    return this.request;
  }

  @Override
  public void writeOutput(ByteBuffer value, SyscallCallback<Void> callback) {
    wrapAndPropagateExceptions(
        () -> {
          LOG.trace("writeOutput success");
          this.writeOutput(
              Protocol.OutputEntryMessage.newBuilder()
                  .setValue(nioBufferToProtobufBuffer(value))
                  .build(),
              callback);
        },
        callback);
  }

  @Override
  public void writeOutput(TerminalException throwable, SyscallCallback<Void> callback) {
    wrapAndPropagateExceptions(
        () -> {
          LOG.trace("writeOutput failure");
          this.writeOutput(
              Protocol.OutputEntryMessage.newBuilder()
                  .setFailure(Util.toProtocolFailure(throwable))
                  .build(),
              callback);
        },
        callback);
  }

  private void writeOutput(Protocol.OutputEntryMessage entry, SyscallCallback<Void> callback) {
    wrapAndPropagateExceptions(
        () -> this.stateMachine.processJournalEntry(entry, OutputEntry.INSTANCE, callback),
        callback);
  }

  @Override
  public void get(String name, SyscallCallback<Deferred<ByteBuffer>> callback) {
    wrapAndPropagateExceptions(
        () -> {
          LOG.trace("get {}", name);
          this.stateMachine.processCompletableJournalEntry(
              Protocol.GetStateEntryMessage.newBuilder()
                  .setKey(ByteString.copyFromUtf8(name))
                  .build(),
              GetStateEntry.INSTANCE,
              callback);
        },
        callback);
  }

  @Override
  public void getKeys(SyscallCallback<Deferred<Collection<String>>> callback) {
    wrapAndPropagateExceptions(
        () -> {
          LOG.trace("get keys");
          this.stateMachine.processCompletableJournalEntry(
              Protocol.GetStateKeysEntryMessage.newBuilder().build(),
              GetStateKeysEntry.INSTANCE,
              callback);
        },
        callback);
  }

  @Override
  public void clear(String name, SyscallCallback<Void> callback) {
    wrapAndPropagateExceptions(
        () -> {
          LOG.trace("clear {}", name);
          this.stateMachine.processJournalEntry(
              Protocol.ClearStateEntryMessage.newBuilder()
                  .setKey(ByteString.copyFromUtf8(name))
                  .build(),
              ClearStateEntry.INSTANCE,
              callback);
        },
        callback);
  }

  @Override
  public void clearAll(SyscallCallback<Void> callback) {
    wrapAndPropagateExceptions(
        () -> {
          LOG.trace("clearAll");
          this.stateMachine.processJournalEntry(
              Protocol.ClearAllStateEntryMessage.newBuilder().build(),
              ClearAllStateEntry.INSTANCE,
              callback);
        },
        callback);
  }

  @Override
  public void set(String name, ByteBuffer value, SyscallCallback<Void> callback) {
    wrapAndPropagateExceptions(
        () -> {
          LOG.trace("set {}", name);
          this.stateMachine.processJournalEntry(
              Protocol.SetStateEntryMessage.newBuilder()
                  .setKey(ByteString.copyFromUtf8(name))
                  .setValue(nioBufferToProtobufBuffer(value))
                  .build(),
              SetStateEntry.INSTANCE,
              callback);
        },
        callback);
  }

  @Override
  public void sleep(Duration duration, SyscallCallback<Deferred<Void>> callback) {
    wrapAndPropagateExceptions(
        () -> {
          LOG.trace("sleep {}", duration);
          this.stateMachine.processCompletableJournalEntry(
              Protocol.SleepEntryMessage.newBuilder()
                  .setWakeUpTime(Instant.now().toEpochMilli() + duration.toMillis())
                  .build(),
              SleepEntry.INSTANCE,
              callback);
        },
        callback);
  }

  @Override
  public void call(
      Target target, ByteBuffer parameter, SyscallCallback<Deferred<ByteBuffer>> callback) {
    wrapAndPropagateExceptions(
        () -> {
          LOG.trace("call {}", target);

          Protocol.CallEntryMessage.Builder builder =
              Protocol.CallEntryMessage.newBuilder()
                  .setServiceName(target.getService())
                  .setHandlerName(target.getHandler())
                  .setParameter(nioBufferToProtobufBuffer(parameter));
          if (target.getKey() != null) {
            builder.setKey(target.getKey());
          }

          this.stateMachine.processCompletableJournalEntry(
              builder.build(), new InvokeEntry<>(Result::success), callback);
        },
        callback);
  }

  @Override
  public void send(
      Target target,
      ByteBuffer parameter,
      @Nullable Duration delay,
      SyscallCallback<Void> callback) {
    wrapAndPropagateExceptions(
        () -> {
          LOG.trace("backgroundCall {}", target);

          Protocol.OneWayCallEntryMessage.Builder builder =
              Protocol.OneWayCallEntryMessage.newBuilder()
                  .setServiceName(target.getService())
                  .setHandlerName(target.getHandler())
                  .setParameter(nioBufferToProtobufBuffer(parameter));
          if (target.getKey() != null) {
            builder.setKey(target.getKey());
          }
          if (delay != null && !delay.isZero()) {
            builder.setInvokeTime(Instant.now().toEpochMilli() + delay.toMillis());
          }

          this.stateMachine.processJournalEntry(
              builder.build(), BackgroundInvokeEntry.INSTANCE, callback);
        },
        callback);
  }

  @Override
  public void enterSideEffectBlock(String name, EnterSideEffectSyscallCallback callback) {
    wrapAndPropagateExceptions(
        () -> {
          LOG.trace("enterSideEffectBlock");
          this.stateMachine.enterSideEffectBlock(name, callback);
        },
        callback);
  }

  @Override
  public void exitSideEffectBlock(ByteBuffer toWrite, ExitSideEffectSyscallCallback callback) {
    wrapAndPropagateExceptions(
        () -> {
          LOG.trace("exitSideEffectBlock with success");
          this.stateMachine.exitSideEffectBlock(
              Protocol.RunEntryMessage.newBuilder()
                  .setValue(nioBufferToProtobufBuffer(toWrite))
                  .build(),
              callback);
        },
        callback);
  }

  @Override
  public void exitSideEffectBlockWithTerminalException(
      TerminalException toWrite, ExitSideEffectSyscallCallback callback) {
    wrapAndPropagateExceptions(
        () -> {
          LOG.trace("exitSideEffectBlock with failure");
          this.stateMachine.exitSideEffectBlock(
              Protocol.RunEntryMessage.newBuilder()
                  .setFailure(Util.toProtocolFailure(toWrite))
                  .build(),
              callback);
        },
        callback);
  }

  @Override
  public void awakeable(SyscallCallback<Map.Entry<String, Deferred<ByteBuffer>>> callback) {
    wrapAndPropagateExceptions(
        () -> {
          LOG.trace("awakeable");
          this.stateMachine.processCompletableJournalEntry(
              Protocol.AwakeableEntryMessage.getDefaultInstance(),
              AwakeableEntry.INSTANCE,
              SyscallCallback.mappingTo(
                  deferredResult -> {
                    // Encode awakeable id
                    ByteString awakeableId =
                        stateMachine
                            .id()
                            .concat(
                                ByteString.copyFrom(
                                    ByteBuffer.allocate(4)
                                        .putInt(
                                            ((SingleDeferredInternal<ByteBuffer>) deferredResult)
                                                .entryIndex())
                                        .rewind()));

                    return new AbstractMap.SimpleImmutableEntry<>(
                        Entries.AWAKEABLE_IDENTIFIER_PREFIX
                            + Base64.getUrlEncoder().encodeToString(awakeableId.toByteArray()),
                        deferredResult);
                  },
                  callback));
        },
        callback);
  }

  @Override
  public void resolveAwakeable(
      String serializedId, ByteBuffer payload, SyscallCallback<Void> callback) {
    wrapAndPropagateExceptions(
        () -> {
          LOG.trace("resolveAwakeable");
          completeAwakeable(
              serializedId,
              Protocol.CompleteAwakeableEntryMessage.newBuilder()
                  .setValue(nioBufferToProtobufBuffer(payload)),
              callback);
        },
        callback);
  }

  @Override
  public void rejectAwakeable(String serializedId, String reason, SyscallCallback<Void> callback) {
    wrapAndPropagateExceptions(
        () -> {
          LOG.trace("rejectAwakeable");
          completeAwakeable(
              serializedId,
              Protocol.CompleteAwakeableEntryMessage.newBuilder()
                  .setFailure(
                      Protocol.Failure.newBuilder()
                          .setCode(TerminalException.INTERNAL_SERVER_ERROR_CODE)
                          .setMessage(reason)),
              callback);
        },
        callback);
  }

  private void completeAwakeable(
      String serializedId,
      Protocol.CompleteAwakeableEntryMessage.Builder builder,
      SyscallCallback<Void> callback) {
    Protocol.CompleteAwakeableEntryMessage expectedEntry = builder.setId(serializedId).build();
    this.stateMachine.processJournalEntry(expectedEntry, CompleteAwakeableEntry.INSTANCE, callback);
  }

  @Override
  public void promise(String key, SyscallCallback<Deferred<ByteBuffer>> callback) {
    wrapAndPropagateExceptions(
        () -> {
          LOG.trace("promise");
          this.stateMachine.processCompletableJournalEntry(
              Protocol.GetPromiseEntryMessage.newBuilder().setKey(key).build(),
              GetPromiseEntry.INSTANCE,
              callback);
        },
        callback);
  }

  @Override
  public void peekPromise(String key, SyscallCallback<Deferred<ByteBuffer>> callback) {
    wrapAndPropagateExceptions(
        () -> {
          LOG.trace("peekPromise");
          this.stateMachine.processCompletableJournalEntry(
              Protocol.PeekPromiseEntryMessage.newBuilder().setKey(key).build(),
              PeekPromiseEntry.INSTANCE,
              callback);
        },
        callback);
  }

  @Override
  public void resolvePromise(
      String key, ByteBuffer payload, SyscallCallback<Deferred<Void>> callback) {
    wrapAndPropagateExceptions(
        () -> {
          LOG.trace("resolvePromise");
          this.stateMachine.processCompletableJournalEntry(
              Protocol.CompletePromiseEntryMessage.newBuilder()
                  .setKey(key)
                  .setCompletionValue(nioBufferToProtobufBuffer(payload))
                  .build(),
              CompletePromiseEntry.INSTANCE,
              callback);
        },
        callback);
  }

  @Override
  public void rejectPromise(String key, String reason, SyscallCallback<Deferred<Void>> callback) {
    wrapAndPropagateExceptions(
        () -> {
          LOG.trace("resolvePromise");
          this.stateMachine.processCompletableJournalEntry(
              Protocol.CompletePromiseEntryMessage.newBuilder()
                  .setKey(key)
                  .setCompletionFailure(
                      Protocol.Failure.newBuilder()
                          .setCode(TerminalException.INTERNAL_SERVER_ERROR_CODE)
                          .setMessage(reason))
                  .build(),
              CompletePromiseEntry.INSTANCE,
              callback);
        },
        callback);
  }

  @Override
  public <T> void resolveDeferred(Deferred<T> deferredToResolve, SyscallCallback<Void> callback) {
    wrapAndPropagateExceptions(
        () -> this.stateMachine.resolveDeferred(deferredToResolve, callback), callback);
  }

  @Override
  public String getFullyQualifiedMethodName() {
    return this.stateMachine.getFullyQualifiedHandlerName();
  }

  @Override
  public InvocationState getInvocationState() {
    return this.stateMachine.getInvocationState();
  }

  @Override
  public boolean isInsideSideEffect() {
    return this.stateMachine.isInsideSideEffect();
  }

  @Override
  public void close() {
    this.stateMachine.end();
  }

  @Override
  public void fail(Throwable cause) {
    this.stateMachine.fail(cause);
  }

  // -- Wrapper for failure propagation

  private void wrapAndPropagateExceptions(Runnable r, SyscallCallback<?> handler) {
    try {
      r.run();
    } catch (Throwable e) {
      this.fail(e);
      handler.onCancel(e);
    }
  }
}
