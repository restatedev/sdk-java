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
import dev.restate.sdk.core.generated.protocol.Protocol;
import dev.restate.common.Slice;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Set;

final class EagerState {

  sealed interface State {}

  record Unknown() implements State {
    private static final Unknown INSTANCE = new Unknown();
  }

  record Empty() implements State {
    private static final Empty INSTANCE = new Empty();
  }

  record Value(Slice value) implements State {}

  private boolean isPartial;
  private final HashMap<ByteString, NotificationValue> map;

  EagerState(Protocol.StartMessage startMessage) {
    this.isPartial = startMessage.getPartialState();
    this.map = new HashMap<>(startMessage.getStateMapCount());
    for (int i = 0; i < startMessage.getStateMapCount(); i++) {
      Protocol.StartMessage.StateEntry entry = startMessage.getStateMap(i);
      this.map.put(entry.getKey(), new NotificationValue.Success(Slice.wrap(entry.getValue().asReadOnlyByteBuffer())));
    }
  }

  public @Nullable NotificationValue get(ByteString key) {
    return this.map.getOrDefault(key, isComplete() ? NotificationValue.Empty.INSTANCE : null);
  }

  public void set(ByteString key, Slice value) {
    this.map.put(key, new NotificationValue.Success(value));
  }

  public void clear(ByteString key) {
    this.map.put(key, NotificationValue.Empty.INSTANCE);
  }

  public void clearAll() {
    this.map.clear();
    this.isPartial = false;
  }

  public boolean isComplete() {
    return !isPartial;
  }

  public @Nullable Set<ByteString> keys() {
    if (isComplete()) {
      return this.map.keySet();
    }
    return null;
  }
}
