// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine;

import com.google.protobuf.ByteString;
import dev.restate.sdk.core.ProtocolException;
import dev.restate.sdk.core.generated.protocol.Protocol;
import java.util.*;

final class AsyncResultsState {
  public static final int CANCEL_NOTIFICATION_HANDLE = 1;

  private final Deque<Map.Entry<NotificationId, NotificationValue>> toProcess;
  private final Map<NotificationId, NotificationValue> ready;
  private final Map<Integer, NotificationId> handleMapping;

  private int nextNotificationHandle;

  public AsyncResultsState() {
    this.toProcess = new ArrayDeque<>();
    this.ready = new HashMap<>();

    this.handleMapping = new HashMap<>();
    // Prepare built in signal handles here
    this.handleMapping.put(CANCEL_NOTIFICATION_HANDLE, new NotificationId.SignalId(1));

    // First 15 are reserved for built-in signals!
    nextNotificationHandle = 17;
  }

  public void enqueue(Protocol.NotificationTemplate notification) {
    var notificationId =
        switch (notification.getIdCase()) {
          case COMPLETION_ID -> new NotificationId.CompletionId(notification.getCompletionId());
          case SIGNAL_ID -> new NotificationId.SignalId(notification.getSignalId());
          case SIGNAL_NAME -> new NotificationId.SignalName(notification.getSignalName());
          case ID_NOT_SET -> throw ProtocolException.badNotificationMessage("id");
        };

    var notificationValue =
        switch (notification.getResultCase()) {
          case VOID -> NotificationValue.Empty.INSTANCE;
          case VALUE ->
              new NotificationValue.Success(
                  Util.byteStringToSlice(notification.getValue().getContent()));
          case FAILURE ->
              new NotificationValue.Failure(Util.toRestateException(notification.getFailure()));
          case INVOCATION_ID -> new NotificationValue.InvocationId(notification.getInvocationId());
          case STATE_KEYS ->
              new NotificationValue.StateKeys(
                  notification.getStateKeys().getKeysList().stream()
                      .map(ByteString::toStringUtf8)
                      .toList());
          case RESULT_NOT_SET -> throw ProtocolException.badNotificationMessage("result");
        };

    toProcess.addLast(Map.entry(notificationId, notificationValue));
  }

  public void insertReady(NotificationId id, NotificationValue value) {
    ready.put(id, value);
  }

  public int createHandleMapping(NotificationId notificationId) {
    int assignedHandle = nextNotificationHandle;
    nextNotificationHandle++;
    handleMapping.put(assignedHandle, notificationId);
    return assignedHandle;
  }

  /**
   * Try to resolve the given future against available notifications.
   *
   * <p>Operates on a deep-mutable copy of {@code unresolved} so the caller's object is unchanged.
   *
   * @return {@link ResolveFutureResult.AnyCompleted} if the future can be resolved, or {@link
   *     ResolveFutureResult.WaitExternalInput} with the remaining (reduced) unresolved future if
   *     not.
   */
  public ResolveFutureResult tryResolveFuture(UnresolvedFuture unresolved) {
    // Work on a mutable copy so we can prune completed children in place.
    unresolved = deepMutableCopy(unresolved);

    while (true) {
      TryResolveResult result = tryResolveFutureInternal(unresolved);

      if (result == TryResolveResult.SHORT_CIRCUITED || result.handleState().isCompleted()) {
        return ResolveFutureResult.ANY_COMPLETED;
      }

      // Not completed yet — try popping the next queued notification and retry
      if (!popNotificationQueue()) {
        return new ResolveFutureResult.WaitExternalInput(unresolved);
      }
    }
  }

  /** Create a deep copy of a future tree with all children stored in mutable {@link ArrayList}s. */
  private static UnresolvedFuture deepMutableCopy(UnresolvedFuture fut) {
    if (fut instanceof UnresolvedFuture.Single) {
      return fut;
    } else if (fut instanceof UnresolvedFuture.FirstCompleted fc) {
      var copy = new ArrayList<UnresolvedFuture>(fc.children().size());
      for (var c : fc.children()) copy.add(deepMutableCopy(c));
      return new UnresolvedFuture.FirstCompleted(copy);
    } else if (fut instanceof UnresolvedFuture.AllCompleted ac) {
      var copy = new ArrayList<UnresolvedFuture>(ac.children().size());
      for (var c : ac.children()) copy.add(deepMutableCopy(c));
      return new UnresolvedFuture.AllCompleted(copy);
    } else if (fut instanceof UnresolvedFuture.FirstSucceededOrAllFailed fsaf) {
      var copy = new ArrayList<UnresolvedFuture>(fsaf.children().size());
      for (var c : fsaf.children()) copy.add(deepMutableCopy(c));
      return new UnresolvedFuture.FirstSucceededOrAllFailed(copy);
    } else if (fut instanceof UnresolvedFuture.AllSucceededOrFirstFailed asff) {
      var copy = new ArrayList<UnresolvedFuture>(asff.children().size());
      for (var c : asff.children()) copy.add(deepMutableCopy(c));
      return new UnresolvedFuture.AllSucceededOrFirstFailed(copy);
    } else if (fut instanceof UnresolvedFuture.Unknown u) {
      var copy = new ArrayList<UnresolvedFuture>(u.children().size());
      for (var c : u.children()) copy.add(deepMutableCopy(c));
      return new UnresolvedFuture.Unknown(copy);
    }
    throw new IllegalStateException("Unknown UnresolvedFuture type: " + fut);
  }

  /** Returns false if there's nothing left in toProcess. */
  private boolean popNotificationQueue() {
    Map.Entry<NotificationId, NotificationValue> notif = toProcess.pollFirst();
    if (notif == null) {
      return false;
    }
    ready.put(notif.getKey(), notif.getValue());
    return true;
  }

  /**
   * Internal recursive resolution. Returns {@link TryResolveResult#SHORT_CIRCUITED} to signal early
   * exit (a combinator completed and wants to propagate up).
   *
   * <p>This method mutates {@code unresolved} in place when children are removed (e.g. completed
   * children are removed from AllCompleted lists).
   */
  private TryResolveResult tryResolveFutureInternal(UnresolvedFuture unresolved) {
    if (unresolved instanceof UnresolvedFuture.Single s) {
      return new TryResolveResult(resolveHandleState(s.handle()));

    } else if (unresolved instanceof UnresolvedFuture.FirstCompleted fc) {
      return resolveFirstCompleted(fc.children());

    } else if (unresolved instanceof UnresolvedFuture.Unknown u) {
      return resolveFirstCompleted(u.children());

    } else if (unresolved instanceof UnresolvedFuture.AllCompleted ac) {
      return resolveAllCompleted(ac.children());

    } else if (unresolved instanceof UnresolvedFuture.FirstSucceededOrAllFailed fsaf) {
      return resolveFirstSucceededOrAllFailed(fsaf.children());

    } else if (unresolved instanceof UnresolvedFuture.AllSucceededOrFirstFailed asff) {
      return resolveAllSucceededOrFirstFailed(asff.children());
    }

    throw new IllegalStateException("Unknown UnresolvedFuture type: " + unresolved);
  }

  /** FirstCompleted / Unknown: resolve as soon as any child completes (success or failure). */
  private TryResolveResult resolveFirstCompleted(List<UnresolvedFuture> children) {
    for (UnresolvedFuture child : children) {
      TryResolveResult childResult = tryResolveFutureInternal(child);
      if (childResult == TryResolveResult.SHORT_CIRCUITED
          || childResult.handleState().isCompleted()) {
        children.clear();
        return TryResolveResult.SHORT_CIRCUITED;
      }
    }
    return TryResolveResult.PENDING;
  }

  /** AllCompleted: wait for every child to complete (success or failure). */
  private TryResolveResult resolveAllCompleted(List<UnresolvedFuture> children) {
    var it = children.listIterator();
    while (it.hasNext()) {
      UnresolvedFuture child = it.next();
      TryResolveResult childResult = tryResolveFutureInternal(child);
      if (childResult == TryResolveResult.SHORT_CIRCUITED) {
        // A nested combinator short-circuited — propagate immediately
        return TryResolveResult.SHORT_CIRCUITED;
      } else if (childResult.handleState().isCompleted()) {
        it.remove();
      }
    }
    if (children.isEmpty()) {
      return new TryResolveResult(HandleState.SUCCEEDED);
    }
    return TryResolveResult.PENDING;
  }

  /** FirstSucceededOrAllFailed: first success wins; fail only if all fail. */
  private TryResolveResult resolveFirstSucceededOrAllFailed(List<UnresolvedFuture> children) {
    var it = children.listIterator();
    while (it.hasNext()) {
      UnresolvedFuture child = it.next();
      TryResolveResult childResult = tryResolveFutureInternal(child);
      if (childResult == TryResolveResult.SHORT_CIRCUITED) {
        // A nested combinator short-circuited — treat as succeeded, propagate
        children.clear();
        return TryResolveResult.SHORT_CIRCUITED;
      }
      HandleState state = childResult.handleState();
      if (state == HandleState.SUCCEEDED) {
        children.clear();
        return TryResolveResult.SHORT_CIRCUITED;
      } else if (state == HandleState.FAILED) {
        it.remove();
      }
    }
    if (children.isEmpty()) {
      return new TryResolveResult(HandleState.FAILED);
    }
    return TryResolveResult.PENDING;
  }

  /** AllSucceededOrFirstFailed: all must succeed; first failure short-circuits. */
  private TryResolveResult resolveAllSucceededOrFirstFailed(List<UnresolvedFuture> children) {
    var it = children.listIterator();
    while (it.hasNext()) {
      UnresolvedFuture child = it.next();
      TryResolveResult childResult = tryResolveFutureInternal(child);
      if (childResult == TryResolveResult.SHORT_CIRCUITED) {
        // A nested combinator short-circuited — propagate immediately
        return TryResolveResult.SHORT_CIRCUITED;
      }
      HandleState state = childResult.handleState();
      if (state == HandleState.FAILED) {
        children.clear();
        return TryResolveResult.SHORT_CIRCUITED;
      } else if (state == HandleState.SUCCEEDED) {
        it.remove();
      }
    }
    if (children.isEmpty()) {
      return new TryResolveResult(HandleState.SUCCEEDED);
    }
    return TryResolveResult.PENDING;
  }

  private HandleState resolveHandleState(int handle) {
    NotificationId id = handleMapping.get(handle);
    if (id == null) {
      return HandleState.PENDING;
    }
    NotificationValue val = ready.get(id);
    if (val == null) {
      return HandleState.PENDING;
    }
    return (val instanceof NotificationValue.Failure) ? HandleState.FAILED : HandleState.SUCCEEDED;
  }

  /** After {@code take_handle} the mapping is gone, so unknown handles are treated as completed. */
  public boolean isHandleCompleted(int handle) {
    NotificationId id = handleMapping.get(handle);
    return id == null || ready.containsKey(id);
  }

  public boolean nonDeterministicFindId(NotificationId id) {
    if (ready.containsKey(id)) {
      return true;
    }
    return toProcess.stream().anyMatch(notif -> notif.getKey().equals(id));
  }

  public Set<NotificationId> resolveNotificationHandles(List<Integer> handles) {
    Set<NotificationId> result = new LinkedHashSet<>();
    for (int handle : handles) {
      NotificationId id = handleMapping.get(handle);
      if (id != null) {
        result.add(id);
      }
    }
    return result;
  }

  public NotificationId mustResolveNotificationHandle(int handle) {
    NotificationId id = handleMapping.get(handle);
    if (id == null) {
      throw new IllegalStateException("If there is a handle, there must be a corresponding id");
    }
    return id;
  }

  public Optional<NotificationValue> takeHandle(int handle) {
    NotificationId id = handleMapping.get(handle);
    if (id != null) {
      NotificationValue result = ready.remove(id);
      if (result != null) {
        handleMapping.remove(handle);
        return Optional.of(result);
      }
    }
    return Optional.empty();
  }

  public Optional<NotificationValue> copyHandle(int handle) {
    NotificationId id = handleMapping.get(handle);
    if (id == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(ready.get(id));
  }

  /**
   * Convert an {@link UnresolvedFuture} tree to the wire-format {@link Protocol.Future} message.
   * Single children are inlined into the parent's waiting_* fields; all other children become
   * nested Future messages.
   */
  public Protocol.Future resolveUnresolvedFuture(UnresolvedFuture unresolved) {
    var builder = Protocol.Future.newBuilder();

    if (unresolved instanceof UnresolvedFuture.Single s) {
      builder.setCombinatorType(Protocol.CombinatorType.FIRST_COMPLETED);
      pushHandle(builder, s.handle());
      return builder.build();
    }

    List<UnresolvedFuture> children;
    if (unresolved instanceof UnresolvedFuture.Unknown u) {
      builder.setCombinatorType(Protocol.CombinatorType.COMBINATOR_UNKNOWN);
      children = u.children();
    } else if (unresolved instanceof UnresolvedFuture.FirstCompleted fc) {
      builder.setCombinatorType(Protocol.CombinatorType.FIRST_COMPLETED);
      children = fc.children();
    } else if (unresolved instanceof UnresolvedFuture.AllCompleted ac) {
      builder.setCombinatorType(Protocol.CombinatorType.ALL_COMPLETED);
      children = ac.children();
    } else if (unresolved instanceof UnresolvedFuture.FirstSucceededOrAllFailed fsaf) {
      builder.setCombinatorType(Protocol.CombinatorType.FIRST_SUCCEEDED_OR_ALL_FAILED);
      children = fsaf.children();
    } else if (unresolved instanceof UnresolvedFuture.AllSucceededOrFirstFailed asff) {
      builder.setCombinatorType(Protocol.CombinatorType.ALL_SUCCEEDED_OR_FIRST_FAILED);
      children = asff.children();
    } else {
      throw new IllegalStateException("Unknown UnresolvedFuture type: " + unresolved);
    }

    for (UnresolvedFuture child : children) {
      if (child instanceof UnresolvedFuture.Single s) {
        pushHandle(builder, s.handle());
      } else {
        builder.addNestedFutures(resolveUnresolvedFuture(child));
      }
    }

    return builder.build();
  }

  private void pushHandle(Protocol.Future.Builder builder, int handle) {
    NotificationId id = handleMapping.get(handle);
    if (id == null) {
      return;
    }
    if (id instanceof NotificationId.CompletionId cid) {
      builder.addWaitingCompletions(cid.id());
    } else if (id instanceof NotificationId.SignalId sid) {
      builder.addWaitingSignals(sid.id());
    } else if (id instanceof NotificationId.SignalName sn) {
      builder.addWaitingNamedSignals(sn.name());
    }
  }

  // --- Inner types ---

  sealed interface ResolveFutureResult
      permits ResolveFutureResult.AnyCompleted, ResolveFutureResult.WaitExternalInput {

    ResolveFutureResult ANY_COMPLETED = new AnyCompleted();

    record AnyCompleted() implements ResolveFutureResult {}

    record WaitExternalInput(UnresolvedFuture remaining) implements ResolveFutureResult {}
  }

  private enum HandleState {
    SUCCEEDED,
    FAILED,
    PENDING;

    boolean isCompleted() {
      return this == SUCCEEDED || this == FAILED;
    }
  }

  /**
   * Wrapper for the internal resolution result. A sentinel {@link #SHORT_CIRCUITED} value signals
   * that a nested combinator completed and the loop should stop.
   */
  private static final class TryResolveResult {
    static final TryResolveResult SHORT_CIRCUITED = new TryResolveResult(null);
    static final TryResolveResult PENDING = new TryResolveResult(HandleState.PENDING);

    private final HandleState state;

    private TryResolveResult(HandleState state) {
      this.state = state;
    }

    HandleState handleState() {
      if (state == null) {
        throw new IllegalStateException("SHORT_CIRCUITED has no HandleState");
      }
      return state;
    }
  }
}
