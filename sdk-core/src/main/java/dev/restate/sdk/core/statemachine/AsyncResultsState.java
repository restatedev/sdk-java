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

  public boolean processNextUntilAnyFound(Set<NotificationId> ids) {
    while (!toProcess.isEmpty()) {
      Map.Entry<NotificationId, NotificationValue> notif = toProcess.removeFirst();
      boolean anyFound = ids.contains(notif.getKey());
      ready.put(notif.getKey(), notif.getValue());
      if (anyFound) {
        return true;
      }
    }
    return false;
  }

  public boolean isHandleCompleted(int handle) {
    NotificationId id = handleMapping.get(handle);
    return id != null && ready.containsKey(id);
  }

  public boolean nonDeterministicFindId(NotificationId id) {
    if (ready.containsKey(id)) {
      return true;
    }
    return toProcess.stream().anyMatch(notif -> notif.getKey().equals(id));
  }

  public Set<NotificationId> resolveNotificationHandles(List<Integer> handles) {
    Set<NotificationId> result = new HashSet<>();
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
}
