package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.Util.isTerminalException;
import static dev.restate.sdk.core.impl.Util.toProtocolFailure;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import com.google.rpc.Code;
import dev.restate.generated.sdk.java.Java;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.generated.service.protocol.Protocol.PollInputStreamEntryMessage;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.impl.DeferredResults.SingleDeferredResultInternal;
import dev.restate.sdk.core.impl.Entries.*;
import dev.restate.sdk.core.impl.ReadyResults.ReadyResultInternal;
import dev.restate.sdk.core.serde.CustomSerdeFunctionsTypeTag;
import dev.restate.sdk.core.serde.Serde;
import dev.restate.sdk.core.syscalls.DeferredResult;
import dev.restate.sdk.core.syscalls.EnterSideEffectSyscallCallback;
import dev.restate.sdk.core.syscalls.ExitSideEffectSyscallCallback;
import dev.restate.sdk.core.syscalls.SyscallCallback;
import io.grpc.MethodDescriptor;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class SyscallsImpl extends SyscallsInternal {

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
    this.stateMachine.processJournalEntry(entry, OutputStreamEntry.INSTANCE, callback);
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
    this.stateMachine.processJournalEntry(
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
    this.stateMachine.processJournalEntry(
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
        Protocol.SleepEntryMessage.newBuilder()
            .setWakeUpTime(Instant.now().toEpochMilli() + duration.toMillis())
            .build(),
        SleepEntry.INSTANCE,
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
      @Nullable Duration delay,
      SyscallCallback<Void> callback) {
    String serviceName = methodDescriptor.getServiceName();
    String methodName = methodDescriptor.getBareMethodName();
    LOG.trace("backgroundCall {}/{}", serviceName, methodName);

    var builder =
        Protocol.BackgroundInvokeEntryMessage.newBuilder()
            .setServiceName(serviceName)
            .setMethodName(methodName)
            .setParameter(parameter.toByteString());

    if (delay != null) {
      builder.setInvokeTime(Instant.now().toEpochMilli() + delay.toMillis());
    }

    this.stateMachine.processJournalEntry(
        builder.build(), BackgroundInvokeEntry.INSTANCE, callback);
  }

  @Override
  public <T> void enterSideEffectBlock(
      TypeTag<T> typeTag, EnterSideEffectSyscallCallback<T> callback) {
    LOG.trace("enterSideEffectBlock");
    this.stateMachine.enterSideEffectBlock(
        sideEffectEntryHandler(typeTag, callback), callback::onNotExecuted, callback::onCancel);
  }

  @Override
  public <T> void exitSideEffectBlock(
      TypeTag<T> typeTag, T toWrite, ExitSideEffectSyscallCallback<T> callback) {
    LOG.trace("exitSideEffectBlock with success");
    this.stateMachine.exitSideEffectBlock(
        Java.SideEffectEntryMessage.newBuilder().setValue(serialize(typeTag, toWrite)).build(),
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

    // If it's a non-terminal exception (such as a protocol exception),
    // we don't write it but simply throw it
    if (!isTerminalException(toWrite)) {
      // For safety wrt Syscalls API we do this check and wrapping,
      // but with the current APIs the exception should always be RuntimeException
      // because that's what can be thrown inside a lambda
      if (toWrite instanceof RuntimeException) {
        throw (RuntimeException) toWrite;
      } else {
        throw new RuntimeException(toWrite);
      }
    }

    this.stateMachine.exitSideEffectBlock(
        Java.SideEffectEntryMessage.newBuilder().setFailure(toProtocolFailure(toWrite)).build(),
        sideEffectEntry ->
            callback.onFailure(
                Util.toGrpcStatus(sideEffectEntry.getFailure()).asRuntimeException()),
        callback::onCancel);
  }

  @Override
  public <T> void awakeable(
      TypeTag<T> typeTag, SyscallCallback<Map.Entry<String, DeferredResult<T>>> callback) {
    LOG.trace("callback");
    this.stateMachine.processCompletableJournalEntry(
        Protocol.AwakeableEntryMessage.getDefaultInstance(),
        new AwakeableEntry<>(serdeDeserializer(typeTag)),
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
                                      ((SingleDeferredResultInternal<T>) deferredResult)
                                          .entryIndex())
                                  .rewind()));

              return new AbstractMap.SimpleImmutableEntry<>(
                  Base64.getUrlEncoder().encodeToString(awakeableId.toByteArray()), deferredResult);
            },
            callback));
  }

  @Override
  public <T> void resolveAwakeable(
      String serializedId, TypeTag<T> ty, @Nonnull T payload, SyscallCallback<Void> callback) {
    LOG.trace("resolveAwakeable");

    Objects.requireNonNull(payload);
    ByteString serialized = serialize(ty, payload);

    completeAwakeable(
        serializedId,
        Protocol.CompleteAwakeableEntryMessage.newBuilder().setValue(serialized),
        callback);
  }

  @Override
  public void rejectAwakeable(String serializedId, String reason, SyscallCallback<Void> callback) {
    LOG.trace("rejectAwakeable");

    completeAwakeable(
        serializedId,
        Protocol.CompleteAwakeableEntryMessage.newBuilder()
            .setFailure(
                Protocol.Failure.newBuilder().setCode(Code.UNKNOWN_VALUE).setMessage(reason)),
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
    this.stateMachine.resolveDeferred(deferredToResolve, callback);
  }

  @Override
  void startCompensating() {
    this.stateMachine.startCompensating();
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
    } else if (Void.class.equals(typeTag) || Void.TYPE.equals(typeTag)) {
      // Amazing JVM foot-gun here: Void.TYPE is the primitive type, Void.class is the boxed type.
      // For us, they're the same but for the equality they aren't, so we check both
      return null;
    }
    return serde.deserialize(typeTag, bytes.toByteArray());
  }
}
