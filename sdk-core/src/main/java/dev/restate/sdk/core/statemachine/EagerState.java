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
import dev.restate.sdk.types.Slice;

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

  record Value(Slice value) implements State { }

  private boolean isPartial;
  private final HashMap<ByteString, State> map;

  EagerState(Protocol.StartMessage startMessage) {
    this.isPartial = startMessage.getPartialState();
    this.map = new HashMap<>(startMessage.getStateMapCount());
    for (int i = 0; i < startMessage.getStateMapCount(); i++) {
      Protocol.StartMessage.StateEntry entry = startMessage.getStateMap(i);
      this.map.put(entry.getKey(), new Value(
              Slice.wrap(
              entry.getValue().asReadOnlyByteBuffer())));
    }
  }

  public State get(ByteString key) {
    return this.map.getOrDefault(key, isPartial ? Unknown.INSTANCE : Empty.INSTANCE);
  }

  public void set(ByteString key, Slice value) {
    this.map.put(key, new Value(value));
  }

  public void clear(ByteString key) {
    this.map.put(key, Empty.INSTANCE);
  }

  public void clearAll() {
    this.map.clear();
    this.isPartial = false;
  }

  public boolean isComplete() {
    return !isPartial;
  }

  public Set<ByteString> keys() {
    return this.map.keySet();
  }
}
