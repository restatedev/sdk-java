// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import com.google.protobuf.ByteString;
import dev.restate.sdk.common.*;
import dev.restate.sdk.common.function.ThrowingSupplier;
import dev.restate.sdk.common.syscalls.Deferred;
import dev.restate.sdk.common.syscalls.EnterSideEffectSyscallCallback;
import dev.restate.sdk.common.syscalls.ExitSideEffectSyscallCallback;
import dev.restate.sdk.common.syscalls.Syscalls;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

class ContextImpl implements ObjectContext {

  private final Syscalls syscalls;

  ContextImpl(Syscalls syscalls) {
    this.syscalls = syscalls;
  }

  @Override
  public String key() {
    return syscalls.objectKey();
  }

  @Override
  public InvocationId invocationId() {
    return syscalls.invocationId();
  }

  @Override
  public <T> Optional<T> get(StateKey<T> key) {
    Deferred<ByteString> deferred = Util.blockOnSyscall(cb -> syscalls.get(key.name(), cb));

    if (!deferred.isCompleted()) {
      Util.<Void>blockOnSyscall(cb -> syscalls.resolveDeferred(deferred, cb));
    }

    return Util.unwrapOptionalReadyResult(deferred.toResult())
        .map(bs -> Util.deserializeWrappingException(syscalls, key.serde(), bs));
  }

  @Override
  public Collection<String> stateKeys() {
    Deferred<Collection<String>> deferred = Util.blockOnSyscall(syscalls::getKeys);

    if (!deferred.isCompleted()) {
      Util.<Void>blockOnSyscall(cb -> syscalls.resolveDeferred(deferred, cb));
    }

    return Util.unwrapResult(deferred.toResult());
  }

  @Override
  public void clear(StateKey<?> key) {
    Util.<Void>blockOnSyscall(cb -> syscalls.clear(key.name(), cb));
  }

  @Override
  public void clearAll() {
    Util.<Void>blockOnSyscall(syscalls::clearAll);
  }

  @Override
  public <T> void set(StateKey<T> key, @NonNull T value) {
    Util.<Void>blockOnSyscall(
        cb ->
            syscalls.set(
                key.name(), Util.serializeWrappingException(syscalls, key.serde(), value), cb));
  }

  @Override
  public Awaitable<Void> timer(Duration duration) {
    Deferred<Void> result = Util.blockOnSyscall(cb -> syscalls.sleep(duration, cb));
    return Awaitable.single(syscalls, result);
  }

  @Override
  public <T, R> Awaitable<R> call(
      Target target, Serde<T> inputSerde, Serde<R> outputSerde, T parameter) {
    ByteString input = Util.serializeWrappingException(syscalls, inputSerde, parameter);
    Deferred<ByteString> result = Util.blockOnSyscall(cb -> syscalls.call(target, input, cb));
    return Awaitable.single(syscalls, result)
        .map(bs -> Util.deserializeWrappingException(syscalls, outputSerde, bs));
  }

  @Override
  public <T> void send(Target target, Serde<T> inputSerde, T parameter) {
    ByteString input = Util.serializeWrappingException(syscalls, inputSerde, parameter);
    Util.<Void>blockOnSyscall(cb -> syscalls.send(target, input, null, cb));
  }

  @Override
  public <T> void sendDelayed(Target target, Serde<T> inputSerde, T parameter, Duration delay) {
    ByteString input = Util.serializeWrappingException(syscalls, inputSerde, parameter);
    Util.<Void>blockOnSyscall(cb -> syscalls.send(target, input, delay, cb));
  }

  @Override
  public <T> T sideEffect(Serde<T> serde, ThrowingSupplier<T> action) {
    CompletableFuture<CompletableFuture<ByteString>> enterFut = new CompletableFuture<>();
    syscalls.enterSideEffectBlock(
        new EnterSideEffectSyscallCallback() {
          @Override
          public void onNotExecuted() {
            enterFut.complete(new CompletableFuture<>());
          }

          @Override
          public void onSuccess(ByteString result) {
            enterFut.complete(CompletableFuture.completedFuture(result));
          }

          @Override
          public void onFailure(TerminalException t) {
            enterFut.complete(CompletableFuture.failedFuture(t));
          }

          @Override
          public void onCancel(Throwable t) {
            enterFut.cancel(true);
          }
        });

    // If a failure was stored, it's simply thrown here
    CompletableFuture<ByteString> exitFut = Util.awaitCompletableFuture(enterFut);
    if (exitFut.isDone()) {
      // We already have a result, we don't need to execute the action
      return Util.deserializeWrappingException(
          syscalls, serde, Util.awaitCompletableFuture(exitFut));
    }

    ExitSideEffectSyscallCallback exitCallback =
        new ExitSideEffectSyscallCallback() {
          @Override
          public void onSuccess(ByteString result) {
            exitFut.complete(result);
          }

          @Override
          public void onFailure(TerminalException t) {
            exitFut.completeExceptionally(t);
          }

          @Override
          public void onCancel(@Nullable Throwable t) {
            exitFut.cancel(true);
          }
        };

    T res = null;
    TerminalException failure = null;
    try {
      res = action.get();
    } catch (TerminalException e) {
      failure = e;
    } catch (Error e) {
      throw e;
    } catch (Throwable e) {
      syscalls.fail(e);
      AbortedExecutionException.sneakyThrow();
    }

    if (failure != null) {
      syscalls.exitSideEffectBlockWithTerminalException(failure, exitCallback);
    } else {
      syscalls.exitSideEffectBlock(
          Util.serializeWrappingException(syscalls, serde, res), exitCallback);
    }

    return Util.deserializeWrappingException(syscalls, serde, Util.awaitCompletableFuture(exitFut));
  }

  @Override
  public <T> Awakeable<T> awakeable(Serde<T> serde) throws TerminalException {
    // Retrieve the awakeable
    Map.Entry<String, Deferred<ByteString>> awakeable = Util.blockOnSyscall(syscalls::awakeable);

    return new Awakeable<>(syscalls, awakeable.getValue(), serde, awakeable.getKey());
  }

  @Override
  public AwakeableHandle awakeableHandle(String id) {
    return new AwakeableHandle() {
      @Override
      public <T> void resolve(Serde<T> serde, @NonNull T payload) {
        Util.<Void>blockOnSyscall(
            cb ->
                syscalls.resolveAwakeable(
                    id, Util.serializeWrappingException(syscalls, serde, payload), cb));
      }

      @Override
      public void reject(String reason) {
        Util.<Void>blockOnSyscall(cb -> syscalls.rejectAwakeable(id, reason, cb));
      }
    };
  }

  @Override
  public RestateRandom random() {
    return new RestateRandom(this.invocationId().toRandomSeed(), this.syscalls);
  }
}
