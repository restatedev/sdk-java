package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.Util.toProtocolFailure;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import dev.restate.generated.core.AwakeableIdentifier;
import dev.restate.generated.sdk.java.Java;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.generated.service.protocol.Protocol.PollInputStreamEntryMessage;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.impl.DeferredResults.SingleDeferredResultInternal;
import dev.restate.sdk.core.impl.Entries.*;
import dev.restate.sdk.core.impl.ReadyResults.ReadyResultInternal;
import dev.restate.sdk.core.serde.CustomSerdeFunctionsTypeTag;
import dev.restate.sdk.core.serde.Serde;
import dev.restate.sdk.core.syscalls.*;
import io.grpc.MethodDescriptor;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nonnull;
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
        PollInputStreamEntryMessage.getDefaultInstance(),
        new PollInputEntry<>(protoDeserializer(mapper)),
        callback);
  }

  @Override
  public <T extends MessageLite> void writeOutput(T value, SyscallCallback<Void> callback) {
    LOG.trace("writeOutput success");
    this.writeOutput(
        Protocol.OutputStreamEntryMessage.newBuilder().setValue(value.toByteString()).build(),
        callback);
  }

  @Override
  public void writeOutput(Throwable throwable, SyscallCallback<Void> callback) {
    LOG.trace("writeOutput failure");
    this.writeOutput(
        Protocol.OutputStreamEntryMessage.newBuilder()
            .setFailure(toProtocolFailure(throwable))
            .build(),
        callback);
  }

  private void writeOutput(
      Protocol.OutputStreamEntryMessage entry, SyscallCallback<Void> callback) {
    this.stateMachine.processJournalEntryWithoutWaitingAck(
        entry, OutputStreamEntry.INSTANCE, callback);
  }

  @Override
  public <T> void get(String name, TypeTag<T> ty, SyscallCallback<DeferredResult<T>> callback) {
    LOG.trace("get {}", name);
    this.stateMachine.processCompletableJournalEntry(
        Protocol.GetStateEntryMessage.newBuilder().setKey(ByteString.copyFromUtf8(name)).build(),
        new GetStateEntry<>(serdeDeserializer(ty)),
        callback);
  }

  @Override
  public void clear(String name, SyscallCallback<Void> callback) {
    LOG.trace("clear {}", name);
    this.stateMachine.processJournalEntryWithoutWaitingAck(
        Protocol.ClearStateEntryMessage.newBuilder().setKey(ByteString.copyFromUtf8(name)).build(),
        ClearStateEntry.INSTANCE,
        callback);
  }

  @Override
  public <T> void set(
      String name, TypeTag<T> ty, @Nonnull T value, SyscallCallback<Void> callback) {
    LOG.trace("set {}", name);
    Objects.requireNonNull(value);
    ByteString serialized = serialize(ty, value);
    this.stateMachine.processJournalEntryWithoutWaitingAck(
        Protocol.SetStateEntryMessage.newBuilder()
            .setKey(ByteString.copyFromUtf8(name))
            .setValue(serialized)
            .build(),
        SetStateEntry.INSTANCE,
        callback);
  }

  @Override
  public void sleep(Duration duration, SyscallCallback<DeferredResult<Void>> callback) {
    LOG.trace("sleep {}", duration);
    this.stateMachine.processCompletableJournalEntry(
        Protocol.SleepEntryMessage.getDefaultInstance(), SleepEntry.INSTANCE, callback);
  }

  @Override
  public <T extends MessageLite, R extends MessageLite> void call(
      MethodDescriptor<T, R> methodDescriptor,
      T parameter,
      SyscallCallback<DeferredResult<R>> callback) {
    String serviceName = methodDescriptor.getServiceName();
    String methodName = methodDescriptor.getBareMethodName();
    LOG.trace("call {}/{}", serviceName, methodName);

    this.stateMachine.processCompletableJournalEntry(
        Protocol.InvokeEntryMessage.newBuilder()
            .setServiceName(serviceName)
            .setMethodName(methodName)
            .setParameter(parameter.toByteString())
            .build(),
        new InvokeEntry<>(protoDeserializer(i -> methodDescriptor.parseResponse(i.newInput()))),
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

    this.stateMachine.processJournalEntryWithoutWaitingAck(
        Protocol.BackgroundInvokeEntryMessage.newBuilder()
            .setServiceName(serviceName)
            .setMethodName(methodName)
            .setParameter(parameter.toByteString())
            .build(),
        BackgroundInvokeEntry.INSTANCE,
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
    this.stateMachine.exitSideEffectBlock(
        Java.SideEffectEntryMessage.newBuilder().setValue(serialize(typeTag, toWrite)).build(),
        span -> span.addEvent("Exit SideEffect"),
        sideEffectEntryHandler(typeTag, callback),
        callback::onCancel);
  }

  private <T> Consumer<Java.SideEffectEntryMessage> sideEffectEntryHandler(
      TypeTag<T> typeTag, ExitSideEffectSyscallCallback<T> callback) {
    return sideEffectEntry -> {
      if (sideEffectEntry.hasFailure()) {
        callback.onFailure(Util.toGrpcStatus(sideEffectEntry.getFailure()).asRuntimeException());
        return;
      }

      callback.onResult(deserialize(typeTag, sideEffectEntry.getValue()));
    };
  }

  @Override
  public void exitSideEffectBlockWithException(
      Throwable toWrite, ExitSideEffectSyscallCallback<?> callback) {
    LOG.trace("exitSideEffectBlock with failure");

    // If it's a protocol exception, don't write it
    Optional<ProtocolException> protocolException = Util.findProtocolException(toWrite);
    if (protocolException.isPresent()) {
      throw protocolException.get();
    }

    this.stateMachine.exitSideEffectBlock(
        Java.SideEffectEntryMessage.newBuilder().setFailure(toProtocolFailure(toWrite)).build(),
        span -> span.addEvent("Exit SideEffect"),
        sideEffectEntry ->
            callback.onFailure(
                Util.toGrpcStatus(sideEffectEntry.getFailure()).asRuntimeException()),
        callback::onCancel);
  }

  @Override
  public <T> void awakeable(
      TypeTag<T> typeTag,
      SyscallCallback<Map.Entry<AwakeableIdentifier, DeferredResult<T>>> callback) {
    LOG.trace("callback");
    this.stateMachine.processCompletableJournalEntry(
        Protocol.AwakeableEntryMessage.getDefaultInstance(),
        new AwakeableEntry<>(serdeDeserializer(typeTag)),
        SyscallCallback.mappingTo(
            deferredResult ->
                new AbstractMap.SimpleImmutableEntry<>(
                    AwakeableIdentifier.newBuilder()
                        .setServiceName(stateMachine.getServiceName())
                        .setInstanceKey(stateMachine.getInstanceKey())
                        .setInvocationId(stateMachine.getInvocationId())
                        .setEntryIndex(
                            ((SingleDeferredResultInternal<T>) deferredResult).entryIndex())
                        .build(),
                    deferredResult),
            callback));
  }

  @Override
  public <T> void completeAwakeable(
      AwakeableIdentifier id, TypeTag<T> ty, @Nonnull T payload, SyscallCallback<Void> callback) {
    LOG.trace("completeAwakeable");
    Objects.requireNonNull(payload);
    ByteString serialized = serialize(ty, payload);

    Protocol.CompleteAwakeableEntryMessage expectedEntry =
        Protocol.CompleteAwakeableEntryMessage.newBuilder()
            .setServiceName(id.getServiceName())
            .setInstanceKey(id.getInstanceKey())
            .setInvocationId(id.getInvocationId())
            .setEntryIndex(id.getEntryIndex())
            .setPayload(serialized)
            .build();
    this.stateMachine.processJournalEntryWithoutWaitingAck(
        expectedEntry, CompleteAwakeableEntry.INSTANCE, callback);
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
  public void fail(Throwable cause) {
    this.stateMachine.fail(cause);
  }

  // --- Serde utils

  private <T extends MessageLite> Function<ByteString, ReadyResultInternal<T>> protoDeserializer(
      Function<ByteString, T> mapper) {
    return value -> ReadyResults.success(mapper.apply(value));
  }

  private <T> Function<ByteString, ReadyResultInternal<T>> serdeDeserializer(TypeTag<T> ty) {
    return value -> ReadyResults.success(deserialize(ty, value));
  }

  @SuppressWarnings("unchecked")
  private ByteString serialize(TypeTag<?> ty, Object obj) {
    if (ty instanceof CustomSerdeFunctionsTypeTag) {
      return ByteString.copyFrom(
          ((CustomSerdeFunctionsTypeTag<? super Object>) ty).getSerializer().apply(obj));
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
}
