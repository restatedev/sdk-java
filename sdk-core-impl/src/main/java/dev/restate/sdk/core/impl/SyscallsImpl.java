// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.Util.toProtocolFailure;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import com.google.rpc.Code;
import dev.restate.generated.sdk.java.Java;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.generated.service.protocol.Protocol.PollInputStreamEntryMessage;
import dev.restate.sdk.core.TerminalException;
import dev.restate.sdk.core.impl.DeferredResults.SingleDeferredResultInternal;
import dev.restate.sdk.core.impl.Entries.*;
import dev.restate.sdk.core.impl.ReadyResults.ReadyResultInternal;
import dev.restate.sdk.core.syscalls.DeferredResult;
import dev.restate.sdk.core.syscalls.EnterSideEffectSyscallCallback;
import dev.restate.sdk.core.syscalls.ExitSideEffectSyscallCallback;
import dev.restate.sdk.core.syscalls.SyscallCallback;
import io.grpc.MethodDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.Base64;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class SyscallsImpl implements SyscallsInternal {

  private static final Logger LOG = LogManager.getLogger(SyscallsImpl.class);

  private final InvocationStateMachine stateMachine;

  SyscallsImpl(InvocationStateMachine stateMachine) {
    this.stateMachine = stateMachine;
  }

  @Override
  public <T extends MessageLite> void pollInput(
      Function<ByteString, T> mapper, SyscallCallback<DeferredResult<T>> callback) {
    wrapAndPropagateExceptions(
        () -> {
          LOG.trace("pollInput");
          this.stateMachine.processCompletableJournalEntry(
              PollInputStreamEntryMessage.getDefaultInstance(),
              new PollInputEntry<>(protoDeserializer(mapper)),
              callback);
        },
        callback);
  }

  @Override
  public <T extends MessageLite> void writeOutput(T value, SyscallCallback<Void> callback) {
    wrapAndPropagateExceptions(
        () -> {
          LOG.trace("writeOutput success");
          this.writeOutput(
              Protocol.OutputStreamEntryMessage.newBuilder().setValue(value.toByteString()).build(),
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
              Protocol.OutputStreamEntryMessage.newBuilder()
                  .setFailure(toProtocolFailure(throwable))
                  .build(),
              callback);
        },
        callback);
  }

  private void writeOutput(
      Protocol.OutputStreamEntryMessage entry, SyscallCallback<Void> callback) {
    wrapAndPropagateExceptions(
        () -> this.stateMachine.processJournalEntry(entry, OutputStreamEntry.INSTANCE, callback),
        callback);
  }

  @Override
  public void get(String name, SyscallCallback<DeferredResult<ByteString>> callback) {
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
  public void set(String name, ByteString value, SyscallCallback<Void> callback) {
    wrapAndPropagateExceptions(
        () -> {
          LOG.trace("set {}", name);
          this.stateMachine.processJournalEntry(
              Protocol.SetStateEntryMessage.newBuilder()
                  .setKey(ByteString.copyFromUtf8(name))
                  .setValue(value)
                  .build(),
              SetStateEntry.INSTANCE,
              callback);
        },
        callback);
  }

  @Override
  public void sleep(Duration duration, SyscallCallback<DeferredResult<Void>> callback) {
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
  public <T, R> void call(
      MethodDescriptor<T, R> methodDescriptor,
      T parameter,
      SyscallCallback<DeferredResult<R>> callback) {
    wrapAndPropagateExceptions(
        () -> {
          String serviceName = methodDescriptor.getServiceName();
          String methodName = methodDescriptor.getBareMethodName();
          LOG.trace("call {}/{}", serviceName, methodName);

          this.stateMachine.processCompletableJournalEntry(
              Protocol.InvokeEntryMessage.newBuilder()
                  .setServiceName(serviceName)
                  .setMethodName(methodName)
                  .setParameter(serializeUsingMethodDescriptor(methodDescriptor, parameter))
                  .build(),
              new InvokeEntry<>(
                  protoDeserializer(i -> methodDescriptor.parseResponse(i.newInput()))),
              callback);
        },
        callback);
  }

  @Override
  public <T> void backgroundCall(
      MethodDescriptor<T, ?> methodDescriptor,
      T parameter,
      @Nullable Duration delay,
      SyscallCallback<Void> callback) {
    wrapAndPropagateExceptions(
        () -> {
          String serviceName = methodDescriptor.getServiceName();
          String methodName = methodDescriptor.getBareMethodName();
          LOG.trace("backgroundCall {}/{}", serviceName, methodName);

          var builder =
              Protocol.BackgroundInvokeEntryMessage.newBuilder()
                  .setServiceName(serviceName)
                  .setMethodName(methodName)
                  .setParameter(serializeUsingMethodDescriptor(methodDescriptor, parameter));

          if (delay != null) {
            builder.setInvokeTime(Instant.now().toEpochMilli() + delay.toMillis());
          }

          this.stateMachine.processJournalEntry(
              builder.build(), BackgroundInvokeEntry.INSTANCE, callback);
        },
        callback);
  }

  @Override
  public void enterSideEffectBlock(EnterSideEffectSyscallCallback callback) {
    wrapAndPropagateExceptions(
        () -> {
          LOG.trace("enterSideEffectBlock");
          this.stateMachine.enterSideEffectBlock(callback);
        },
        callback);
  }

  @Override
  public void exitSideEffectBlock(ByteString toWrite, ExitSideEffectSyscallCallback callback) {
    wrapAndPropagateExceptions(
        () -> {
          LOG.trace("exitSideEffectBlock with success");
          this.stateMachine.exitSideEffectBlock(
              Java.SideEffectEntryMessage.newBuilder().setValue(toWrite).build(), callback);
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
              Java.SideEffectEntryMessage.newBuilder()
                  .setFailure(toProtocolFailure(toWrite))
                  .build(),
              callback);
        },
        callback);
  }

  @Override
  public void awakeable(SyscallCallback<Map.Entry<String, DeferredResult<ByteString>>> callback) {
    wrapAndPropagateExceptions(
        () -> {
          LOG.trace("callback");
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
                                            ((SingleDeferredResultInternal<ByteString>)
                                                    deferredResult)
                                                .entryIndex())
                                        .rewind()));

                    return new AbstractMap.SimpleImmutableEntry<>(
                        Base64.getUrlEncoder().encodeToString(awakeableId.toByteArray()),
                        deferredResult);
                  },
                  callback));
        },
        callback);
  }

  @Override
  public void resolveAwakeable(
      String serializedId, ByteString payload, SyscallCallback<Void> callback) {
    wrapAndPropagateExceptions(
        () -> {
          LOG.trace("resolveAwakeable");
          completeAwakeable(
              serializedId,
              Protocol.CompleteAwakeableEntryMessage.newBuilder().setValue(payload),
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
                      Protocol.Failure.newBuilder().setCode(Code.UNKNOWN_VALUE).setMessage(reason)),
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
  public <T> void resolveDeferred(
      DeferredResult<T> deferredToResolve, SyscallCallback<Void> callback) {
    wrapAndPropagateExceptions(
        () -> {
          this.stateMachine.resolveDeferred(deferredToResolve, callback);
        },
        callback);
  }

  @Override
  public void close() {
    this.stateMachine.close();
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

  // --- Serde utils

  private <T> ByteString serializeUsingMethodDescriptor(
      MethodDescriptor<T, ?> methodDescriptor, T parameter) {
    try {
      return ByteString.readFrom(methodDescriptor.getRequestMarshaller().stream(parameter));
    } catch (IOException e) {
      throw new RuntimeException("Cannot serialize the input parameter of the call", e);
    }
  }

  private <T> Function<ByteString, ReadyResultInternal<T>> protoDeserializer(
      Function<ByteString, T> mapper) {
    return value -> ReadyResults.success(mapper.apply(value));
  }
}
