package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.Util.toProtocolFailure;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import dev.restate.generated.core.CallbackIdentifier;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.serde.Serde;
import dev.restate.sdk.core.syscalls.DeferredResult;
import dev.restate.sdk.core.syscalls.ReadyResult;
import io.grpc.MethodDescriptor;
import io.opentelemetry.api.common.Attributes;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public final class SyscallsImpl implements SyscallsInternal {

  private final InvocationStateMachine stateMachine;
  private final Serde serde;

  public SyscallsImpl(InvocationStateMachine stateMachine, Serde serde) {
    this.stateMachine = stateMachine;
    this.serde = serde;
  }

  @Override
  public InvocationStateMachine getStateMachine() {
    return this.stateMachine;
  }

  @Override
  public <T extends MessageLite> void pollInput(
      Function<ByteString, T> mapper,
      SyscallDeferredResultCallback<T> deferredResultCallback,
      Consumer<Throwable> failureCallback) {
    this.stateMachine.processCompletableJournalEntry(
        Protocol.PollInputStreamEntryMessage.getDefaultInstance(),
        entry -> false,
        span -> span.addEvent("PollInputStream"),
        e -> null,
        entry -> deserializeWithProto(mapper, entry.getValue()),
        completionMessage -> deserializeWithProto(mapper, completionMessage.getValue()),
        (index, deferredResult) -> deferredResultCallback.accept(deferredResult),
        failureCallback);
  }

  @Override
  public <T extends MessageLite> void writeOutput(
      T value, Runnable okCallback, Consumer<Throwable> failureCallback) {
    Protocol.OutputStreamEntryMessage entry =
        Protocol.OutputStreamEntryMessage.newBuilder().setValue(value.toByteString()).build();
    this.stateMachine.processJournalEntryWithoutWaitingAck(
        entry, span -> span.addEvent("OutputStream"), e -> null, okCallback, failureCallback);
  }

  @Override
  public void writeOutput(
      Throwable throwable, Runnable okCallback, Consumer<Throwable> failureCallback) {
    this.stateMachine.processJournalEntryWithoutWaitingAck(
        Protocol.OutputStreamEntryMessage.newBuilder()
            .setFailure(toProtocolFailure(throwable))
            .build(),
        span -> span.addEvent("OutputStream"),
        e -> null,
        okCallback,
        failureCallback);
  }

  @Override
  public <T> void get(
      String name,
      TypeTag<T> ty,
      SyscallDeferredResultCallback<T> deferredCallback,
      Consumer<Throwable> failureCallback) {
    Protocol.GetStateEntryMessage expectedEntry =
        Protocol.GetStateEntryMessage.newBuilder().setKey(ByteString.copyFromUtf8(name)).build();
    this.stateMachine.processCompletableJournalEntry(
        expectedEntry,
        entry -> entry.getResultCase() == Protocol.GetStateEntryMessage.ResultCase.RESULT_NOT_SET,
        span -> span.addEvent("GetState", Attributes.of(Tracing.RESTATE_STATE_KEY, name)),
        actualEntry ->
            !expectedEntry.getKey().equals(actualEntry.getKey())
                ? ProtocolException.entryDoNotMatch(expectedEntry, actualEntry)
                : null,
        entry ->
            (entry.getResultCase() == Protocol.GetStateEntryMessage.ResultCase.EMPTY)
                ? ResultTreeNodes.empty()
                : deserializeWithSerde(ty, entry.getValue()),
        completionMessage ->
            (completionMessage.getResultCase() == Protocol.CompletionMessage.ResultCase.EMPTY)
                ? ResultTreeNodes.empty()
                : deserializeWithSerde(ty, completionMessage.getValue()),
        (index, deferredResult) -> deferredCallback.accept(deferredResult),
        failureCallback);
  }

  @Override
  public void clear(String name, Runnable okCallback, Consumer<Throwable> failureCallback) {
    Protocol.ClearStateEntryMessage expectedEntry =
        Protocol.ClearStateEntryMessage.newBuilder().setKey(ByteString.copyFromUtf8(name)).build();
    this.stateMachine.processJournalEntryWithoutWaitingAck(
        expectedEntry,
        span -> span.addEvent("ClearState", Attributes.of(Tracing.RESTATE_STATE_KEY, name)),
        checkEntryEquality(expectedEntry),
        okCallback,
        failureCallback);
  }

  @Override
  public <T> void set(
      String name, T value, Runnable okCallback, Consumer<Throwable> failureCallback) {
    ByteString serialized;
    try {
      serialized = serialize(value);
    } catch (Throwable e) {
      failureCallback.accept(e);
      return;
    }
    Protocol.SetStateEntryMessage expectedEntry =
        Protocol.SetStateEntryMessage.newBuilder()
            .setKey(ByteString.copyFromUtf8(name))
            .setValue(serialized)
            .build();
    this.stateMachine.processJournalEntryWithoutWaitingAck(
        expectedEntry,
        span -> span.addEvent("SetState", Attributes.of(Tracing.RESTATE_STATE_KEY, name)),
        checkEntryEquality(expectedEntry),
        okCallback,
        failureCallback);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public void sleep(
      Duration duration,
      SyscallDeferredResultCallback<Void> deferredResultCallback,
      Consumer<Throwable> failureCallback) {
    this.stateMachine.processCompletableJournalEntry(
        Protocol.SleepEntryMessage.getDefaultInstance(),
        actualEntry -> !actualEntry.hasResult(),
        span ->
            span.addEvent(
                "Sleep", Attributes.of(Tracing.RESTATE_SLEEP_DURATION, duration.toMillis())),
        e -> null,
        entry -> ResultTreeNodes.empty(),
        completionMessage -> ResultTreeNodes.empty(),
        (index, deferredResult) -> deferredResultCallback.accept((DeferredResult) deferredResult),
        failureCallback);
  }

  @Override
  public <T extends MessageLite, R extends MessageLite> void call(
      MethodDescriptor<T, R> methodDescriptor,
      T parameter,
      SyscallDeferredResultCallback<R> deferredResultCallback,
      Consumer<Throwable> failureCallback) {
    String serviceName = methodDescriptor.getServiceName();
    String methodName = methodDescriptor.getBareMethodName();

    Protocol.InvokeEntryMessage expectedEntry =
        Protocol.InvokeEntryMessage.newBuilder()
            .setServiceName(serviceName)
            .setMethodName(methodName)
            .setParameter(parameter.toByteString())
            .build();
    this.stateMachine.processCompletableJournalEntry(
        expectedEntry,
        invokeEntryMessage ->
            invokeEntryMessage.getResultCase()
                == Protocol.InvokeEntryMessage.ResultCase.RESULT_NOT_SET,
        span ->
            span.addEvent(
                "Call",
                Attributes.of(
                    Tracing.RESTATE_COORDINATION_CALL_SERVICE,
                    serviceName,
                    Tracing.RESTATE_COORDINATION_CALL_METHOD,
                    methodName)),
        actualEntry ->
            !(expectedEntry.getServiceName().equals(actualEntry.getServiceName())
                    && expectedEntry.getMethodName().equals(actualEntry.getMethodName())
                    && expectedEntry.getParameter().equals(actualEntry.getParameter()))
                ? ProtocolException.entryDoNotMatch(expectedEntry, actualEntry)
                : null,
        entry ->
            entry.hasValue()
                ? deserializeWithProto(
                    i -> methodDescriptor.parseResponse(i.newInput()), entry.getValue())
                : ResultTreeNodes.failure(
                    Util.toGrpcStatus(entry.getFailure()).asRuntimeException()),
        completionMessage ->
            completionMessage.hasValue()
                ? deserializeWithProto(
                    i -> methodDescriptor.parseResponse(i.newInput()), completionMessage.getValue())
                : ResultTreeNodes.failure(
                    Util.toGrpcStatus(completionMessage.getFailure()).asRuntimeException()),
        (index, deferredResult) -> deferredResultCallback.accept(deferredResult),
        failureCallback);
  }

  @Override
  public <T extends MessageLite> void backgroundCall(
      MethodDescriptor<T, ? extends MessageLite> methodDescriptor,
      T parameter,
      Runnable okCallback,
      Consumer<Throwable> failureCallback) {
    String serviceName = methodDescriptor.getServiceName();
    String methodName = methodDescriptor.getBareMethodName();

    Protocol.BackgroundInvokeEntryMessage expectedEntry =
        Protocol.BackgroundInvokeEntryMessage.newBuilder()
            .setServiceName(serviceName)
            .setMethodName(methodName)
            .setParameter(parameter.toByteString())
            .build();
    this.stateMachine.processJournalEntryWithoutWaitingAck(
        expectedEntry,
        span ->
            span.addEvent(
                "BackgroundCall",
                Attributes.of(
                    Tracing.RESTATE_COORDINATION_CALL_SERVICE,
                    serviceName,
                    Tracing.RESTATE_COORDINATION_CALL_METHOD,
                    methodName)),
        checkEntryEquality(expectedEntry),
        okCallback,
        failureCallback);
  }

  @Override
  public <T> void enterSideEffectBlock(
      TypeTag<T> typeTag,
      Runnable noStoredResultCallback,
      Consumer<ReadyResult<T>> storedResultCallback,
      Consumer<Throwable> failureCallback) {
    this.stateMachine.enterSideEffectJournalEntry(
        span -> span.addEvent("Enter SideEffect"),
        sideEffectEntryHandler(typeTag, storedResultCallback),
        noStoredResultCallback,
        failureCallback);
  }

  @Override
  public <T> void exitSideEffectBlock(
      TypeTag<T> typeTag,
      T toWrite,
      Consumer<ReadyResult<T>> storedResultCallback,
      Consumer<Throwable> failureCallback) {
    Protocol.SideEffectEntryMessage.Builder sideEffectToWrite =
        Protocol.SideEffectEntryMessage.newBuilder();
    try {
      sideEffectToWrite.setValue(serialize(toWrite));
    } catch (Throwable e) {
      // Record the serialization failure
      sideEffectToWrite.setFailure(toProtocolFailure(e));
    }

    this.stateMachine.exitSideEffectBlock(
        sideEffectToWrite.build(),
        span -> span.addEvent("Exit SideEffect"),
        sideEffectEntryHandler(typeTag, storedResultCallback),
        failureCallback);
  }

  private <T> Consumer<Protocol.SideEffectEntryMessage> sideEffectEntryHandler(
      TypeTag<T> typeTag, Consumer<ReadyResult<T>> storedResultCallback) {
    return sideEffectEntry -> {
      if (sideEffectEntry.hasValue()) {
        storedResultCallback.accept(deserializeWithSerde(typeTag, sideEffectEntry.getValue()));
      } else {
        storedResultCallback.accept(
            ResultTreeNodes.failure(
                Util.toGrpcStatus(sideEffectEntry.getFailure()).asRuntimeException()));
      }
    };
  }

  @Override
  public void exitSideEffectBlockWithException(
      Throwable toWrite,
      Consumer<Throwable> storedFailureCallback,
      Consumer<Throwable> failureCallback) {
    this.stateMachine.exitSideEffectBlock(
        Protocol.SideEffectEntryMessage.newBuilder().setFailure(toProtocolFailure(toWrite)).build(),
        span -> span.addEvent("Exit SideEffect"),
        sideEffectEntry ->
            storedFailureCallback.accept(
                Util.toGrpcStatus(sideEffectEntry.getFailure()).asRuntimeException()),
        failureCallback);
  }

  @Override
  public <T> void callback(
      TypeTag<T> typeTag,
      SyscallDeferredResultWithIdentifierCallback<T> deferredResultCallback,
      Consumer<Throwable> failureCallback) {
    this.stateMachine.processCompletableJournalEntry(
        Protocol.CallbackEntryMessage.getDefaultInstance(),
        entry -> entry.getResultCase() == Protocol.CallbackEntryMessage.ResultCase.RESULT_NOT_SET,
        span -> span.addEvent("Callback"),
        e -> null,
        entry ->
            entry.hasValue()
                ? deserializeWithSerde(typeTag, entry.getValue())
                : ResultTreeNodes.failure(
                    Util.toGrpcStatus(entry.getFailure()).asRuntimeException()),
        completionMessage ->
            completionMessage.hasValue()
                ? deserializeWithSerde(typeTag, completionMessage.getValue())
                : ResultTreeNodes.failure(
                    Util.toGrpcStatus(completionMessage.getFailure()).asRuntimeException()),
        (index, deferredResult) ->
            deferredResultCallback.accept(
                CallbackIdentifier.newBuilder()
                    .setServiceName(this.stateMachine.getServiceName())
                    .setInstanceKey(this.stateMachine.getInstanceKey())
                    .setInvocationId(this.stateMachine.getInvocationId())
                    .setEntryIndex(index)
                    .build(),
                deferredResult),
        failureCallback);
  }

  @Override
  public void completeCallback(
      CallbackIdentifier id,
      Object payload,
      Runnable okCallback,
      Consumer<Throwable> failureCallback) {
    ByteString serialized;
    try {
      serialized = serialize(payload);
    } catch (Throwable e) {
      failureCallback.accept(e);
      return;
    }

    Protocol.CompleteCallbackEntryMessage expectedEntry =
        Protocol.CompleteCallbackEntryMessage.newBuilder()
            .setServiceName(id.getServiceName())
            .setInstanceKey(id.getInstanceKey())
            .setInvocationId(id.getInvocationId())
            .setEntryIndex(id.getEntryIndex())
            .setPayload(serialized)
            .build();
    this.stateMachine.processJournalEntryWithoutWaitingAck(
        expectedEntry,
        span -> span.addEvent("CompleteCallback"),
        checkEntryEquality(expectedEntry),
        okCallback,
        failureCallback);
  }

  @Override
  public <T> void resolveDeferred(
      DeferredResult<T> deferredToResolve,
      Consumer<ReadyResult<T>> resultCallback,
      Consumer<Throwable> failureCallback) {
    this.stateMachine.resolveDeferred(deferredToResolve, resultCallback, failureCallback);
  }

  @Override
  public void close() {
    this.stateMachine.close();
  }

  @Override
  public void fail(ProtocolException cause) {
    this.stateMachine.fail(cause);
  }

  // --- Serde utils

  private <T extends MessageLite> ReadyResult<T> deserializeWithProto(
      Function<ByteString, T> mapper, ByteString value) {
    try {
      return ResultTreeNodes.success(mapper.apply(value));
    } catch (Throwable e) {
      return ResultTreeNodes.failure(e);
    }
  }

  private <T> ReadyResult<T> deserializeWithSerde(TypeTag<T> ty, ByteString value) {
    try {
      return ResultTreeNodes.success(deserialize(ty.get(), value));
    } catch (Throwable e) {
      return ResultTreeNodes.failure(e);
    }
  }

  private ByteString serialize(Object obj) {
    if (obj == null) {
      return ByteString.EMPTY;
    }
    if (obj instanceof ByteString) {
      return (ByteString) obj;
    } else if (obj instanceof byte[]) {
      return ByteString.copyFrom((byte[]) obj);
    }
    return ByteString.copyFrom(serde.serialize(obj));
  }

  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
  private <T> T deserialize(Object typeTag, ByteString bytes) {
    if (byte[].class.equals(typeTag)) {
      return (T) bytes.toByteArray();
    } else if (ByteString.class.equals(typeTag)) {
      return (T) bytes;
    } else if (Void.class.equals(typeTag)) {
      return null;
    }
    return serde.deserialize(typeTag, bytes.toByteArray());
  }

  // --- Other utils

  private static <T extends MessageLite> Function<T, ProtocolException> checkEntryEquality(
      T expectedEntry) {
    return actualEntry ->
        !Objects.equals(expectedEntry, actualEntry)
            ? ProtocolException.entryDoNotMatch(expectedEntry, actualEntry)
            : null;
  }
}
