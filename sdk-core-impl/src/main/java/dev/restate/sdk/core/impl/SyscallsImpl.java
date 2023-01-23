package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.Util.toProtocolFailure;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import dev.restate.generated.core.CallbackIdentifier;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.serde.CustomSerdeFunctionsTypeTag;
import dev.restate.sdk.core.serde.Serde;
import dev.restate.sdk.core.syscalls.*;
import io.grpc.MethodDescriptor;
import io.opentelemetry.api.common.Attributes;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class SyscallsImpl implements SyscallsInternal {

  private static final Logger LOG = LogManager.getLogger(SyscallsImpl.class);

  private final InvocationStateMachine stateMachine;
  private final Serde serde;

  public SyscallsImpl(InvocationStateMachine stateMachine, Serde serde) {
    this.stateMachine = stateMachine;
    this.serde = serde;
  }

  @Override
  public <T extends MessageLite> void pollInput(
      Function<ByteString, T> mapper, SyscallCallback<DeferredResult<T>> callback) {
    LOG.trace("pollInput");
    this.stateMachine.processCompletableJournalEntry(
        Protocol.PollInputStreamEntryMessage.getDefaultInstance(),
        entry -> false,
        span -> span.addEvent("PollInputStream"),
        e -> null,
        entry -> deserializeWithProto(mapper, entry.getValue()),
        completionMessage -> deserializeWithProto(mapper, completionMessage.getValue()),
        callback);
  }

  @Override
  public <T extends MessageLite> void writeOutput(T value, SyscallCallback<Void> callback) {
    LOG.trace("writeOutput success");
    Protocol.OutputStreamEntryMessage entry =
        Protocol.OutputStreamEntryMessage.newBuilder().setValue(value.toByteString()).build();
    this.stateMachine.processJournalEntryWithoutWaitingAck(
        entry, span -> span.addEvent("OutputStream"), e -> null, callback);
  }

  @Override
  public void writeOutput(Throwable throwable, SyscallCallback<Void> callback) {
    LOG.trace("writeOutput failure");
    this.stateMachine.processJournalEntryWithoutWaitingAck(
        Protocol.OutputStreamEntryMessage.newBuilder()
            .setFailure(toProtocolFailure(throwable))
            .build(),
        span -> span.addEvent("OutputStream"),
        e -> null,
        callback);
  }

  @Override
  public <T> void get(String name, TypeTag<T> ty, SyscallCallback<DeferredResult<T>> callback) {
    LOG.trace("get {}", name);
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
                ? ReadyResults.empty()
                : deserializeWithSerde(ty, entry.getValue()),
        completionMessage ->
            (completionMessage.getResultCase() == Protocol.CompletionMessage.ResultCase.EMPTY)
                ? ReadyResults.empty()
                : deserializeWithSerde(ty, completionMessage.getValue()),
        callback);
  }

  @Override
  public void clear(String name, SyscallCallback<Void> callback) {
    LOG.trace("clear {}", name);
    Protocol.ClearStateEntryMessage expectedEntry =
        Protocol.ClearStateEntryMessage.newBuilder().setKey(ByteString.copyFromUtf8(name)).build();
    this.stateMachine.processJournalEntryWithoutWaitingAck(
        expectedEntry,
        span -> span.addEvent("ClearState", Attributes.of(Tracing.RESTATE_STATE_KEY, name)),
        checkEntryEquality(expectedEntry),
        callback);
  }

  @Override
  public <T> void set(String name, TypeTag<T> ty, T value, SyscallCallback<Void> callback) {
    LOG.trace("set {}", name);
    ByteString serialized;
    try {
      serialized = serialize(ty, value);
    } catch (Throwable e) {
      callback.onCancel(e);
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
        callback);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public void sleep(Duration duration, SyscallCallback<DeferredResult<Void>> callback) {
    LOG.trace("sleep {}", duration);
    this.stateMachine.processCompletableJournalEntry(
        Protocol.SleepEntryMessage.getDefaultInstance(),
        actualEntry -> !actualEntry.hasResult(),
        span ->
            span.addEvent(
                "Sleep", Attributes.of(Tracing.RESTATE_SLEEP_DURATION, duration.toMillis())),
        e -> null,
        entry -> ReadyResults.empty(),
        completionMessage -> ReadyResults.empty(),
        callback);
  }

  @Override
  public <T extends MessageLite, R extends MessageLite> void call(
      MethodDescriptor<T, R> methodDescriptor,
      T parameter,
      SyscallCallback<DeferredResult<R>> callback) {
    String serviceName = methodDescriptor.getServiceName();
    String methodName = methodDescriptor.getBareMethodName();
    LOG.trace("call {}/{}", serviceName, methodName);

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
                : ReadyResults.failure(Util.toGrpcStatus(entry.getFailure()).asRuntimeException()),
        completionMessage ->
            completionMessage.hasValue()
                ? deserializeWithProto(
                    i -> methodDescriptor.parseResponse(i.newInput()), completionMessage.getValue())
                : ReadyResults.failure(
                    Util.toGrpcStatus(completionMessage.getFailure()).asRuntimeException()),
        callback);
  }

  @Override
  public <T extends MessageLite> void backgroundCall(
      MethodDescriptor<T, ? extends MessageLite> methodDescriptor,
      T parameter,
      SyscallCallback<Void> callback) {
    String serviceName = methodDescriptor.getServiceName();
    String methodName = methodDescriptor.getBareMethodName();
    LOG.trace("backgroundCall {}/{}", serviceName, methodName);

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
        callback);
  }

  @Override
  public <T> void enterSideEffectBlock(
      TypeTag<T> typeTag, EnterSideEffectSyscallCallback<T> callback) {
    LOG.trace("enterSideEffectBlock");
    this.stateMachine.enterSideEffectJournalEntry(
        span -> span.addEvent("Enter SideEffect"),
        sideEffectEntryHandler(typeTag, callback),
        callback::onNotExecuted,
        callback::onCancel);
  }

  @Override
  public <T> void exitSideEffectBlock(
      TypeTag<T> typeTag, T toWrite, ExitSideEffectSyscallCallback<T> callback) {
    LOG.trace("exitSideEffectBlock with success");
    Protocol.SideEffectEntryMessage.Builder sideEffectToWrite =
        Protocol.SideEffectEntryMessage.newBuilder();
    try {
      sideEffectToWrite.setValue(serialize(typeTag, toWrite));
    } catch (Throwable e) {
      // Record the serialization failure
      sideEffectToWrite.setFailure(toProtocolFailure(e));
    }

    this.stateMachine.exitSideEffectBlock(
        sideEffectToWrite.build(),
        span -> span.addEvent("Exit SideEffect"),
        sideEffectEntryHandler(typeTag, callback),
        callback::onCancel);
  }

  private <T> Consumer<Protocol.SideEffectEntryMessage> sideEffectEntryHandler(
      TypeTag<T> typeTag, ExitSideEffectSyscallCallback<T> callback) {
    return sideEffectEntry -> {
      if (sideEffectEntry.hasFailure()) {
        callback.onFailure(Util.toGrpcStatus(sideEffectEntry.getFailure()).asRuntimeException());
        return;
      }

      T value;
      try {
        value = deserialize(typeTag, sideEffectEntry.getValue());
      } catch (Exception e) {
        callback.onFailure(Util.toGrpcStatusErasingCause(e).asRuntimeException());
        return;
      }

      callback.onResult(value);
    };
  }

  @Override
  public void exitSideEffectBlockWithException(
      Throwable toWrite, ExitSideEffectSyscallCallback<?> callback) {
    LOG.trace("exitSideEffectBlock with failure");
    this.stateMachine.exitSideEffectBlock(
        Protocol.SideEffectEntryMessage.newBuilder().setFailure(toProtocolFailure(toWrite)).build(),
        span -> span.addEvent("Exit SideEffect"),
        sideEffectEntry ->
            callback.onFailure(
                Util.toGrpcStatus(sideEffectEntry.getFailure()).asRuntimeException()),
        callback::onCancel);
  }

  @Override
  public <T> void callback(
      TypeTag<T> typeTag,
      SyscallCallback<Map.Entry<CallbackIdentifier, DeferredResult<T>>> callback) {
    LOG.trace("callback");
    this.stateMachine.processCompletableJournalEntry(
        Protocol.CallbackEntryMessage.getDefaultInstance(),
        entry -> entry.getResultCase() == Protocol.CallbackEntryMessage.ResultCase.RESULT_NOT_SET,
        span -> span.addEvent("Callback"),
        e -> null,
        entry ->
            entry.hasValue()
                ? deserializeWithSerde(typeTag, entry.getValue())
                : ReadyResults.failure(Util.toGrpcStatus(entry.getFailure()).asRuntimeException()),
        completionMessage ->
            completionMessage.hasValue()
                ? deserializeWithSerde(typeTag, completionMessage.getValue())
                : ReadyResults.failure(
                    Util.toGrpcStatus(completionMessage.getFailure()).asRuntimeException()),
        SyscallCallback.mapping(
            callback,
            deferredResult ->
                new AbstractMap.SimpleImmutableEntry<>(
                    CallbackIdentifier.newBuilder()
                        .setServiceName(stateMachine.getServiceName())
                        .setInstanceKey(stateMachine.getInstanceKey())
                        .setInvocationId(stateMachine.getInvocationId())
                        .setEntryIndex(((DeferredResultInternal<T>) deferredResult).entryIndex())
                        .build(),
                    deferredResult)));
  }

  @Override
  public <T> void completeCallback(
      CallbackIdentifier id, TypeTag<T> ty, T payload, SyscallCallback<Void> callback) {
    LOG.trace("completeCallback");
    ByteString serialized;
    try {
      serialized = serialize(ty, payload);
    } catch (Throwable e) {
      callback.onCancel(e);
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
        callback);
  }

  @Override
  public <T> void resolveDeferred(
      DeferredResult<T> deferredToResolve, SyscallCallback<Void> callback) {
    this.stateMachine.resolveDeferred(deferredToResolve, callback);
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

  private <T extends MessageLite> ReadyResultInternal<T> deserializeWithProto(
      Function<ByteString, T> mapper, ByteString value) {
    try {
      return ReadyResults.success(mapper.apply(value));
    } catch (Throwable e) {
      return ReadyResults.failure(Util.toGrpcStatusErasingCause(e).asRuntimeException());
    }
  }

  private <T> ReadyResultInternal<T> deserializeWithSerde(TypeTag<T> ty, ByteString value) {
    try {
      return ReadyResults.success(deserialize(ty, value));
    } catch (Throwable e) {
      return ReadyResults.failure(Util.toGrpcStatusErasingCause(e).asRuntimeException());
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private ByteString serialize(TypeTag ty, Object obj) {
    if (ty instanceof CustomSerdeFunctionsTypeTag) {
      return ByteString.copyFrom(
          ((CustomSerdeFunctionsTypeTag<Object>) ty).getSerializer().apply(obj));
    }

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
  private <T> T deserialize(TypeTag<T> ty, ByteString bytes) {
    if (ty instanceof CustomSerdeFunctionsTypeTag) {
      return ((CustomSerdeFunctionsTypeTag<T>) ty).getDeserializer().apply(bytes.toByteArray());
    }

    Object typeTag = ty.get();
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
