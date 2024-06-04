// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import com.google.protobuf.ByteString;
import dev.restate.generated.service.protocol.Protocol;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Set;

final class UserStateStore {

  interface State {}

  static final class Unknown implements State {
    private static final Unknown INSTANCE = new Unknown();

    private Unknown() {}
  }

  static final class Empty implements State {
    private static final Empty INSTANCE = new Empty();

    private Empty() {}
  }

  static final class Value implements State {
    private final ByteBuffer value;

    private Value(ByteBuffer value) {
      this.value = value;
    }

    public ByteBuffer getValue() {
      return value;
    }
  }

  private boolean isPartial;
  private final HashMap<ByteString, State> map;

  UserStateStore(Protocol.StartMessage startMessage) {
    this.isPartial = startMessage.getPartialState();
    this.map = new HashMap<>(startMessage.getStateMapCount());
    for (int i = 0; i < startMessage.getStateMapCount(); i++) {
      Protocol.StartMessage.StateEntry entry = startMessage.getStateMap(i);
      this.map.put(entry.getKey(), new Value(entry.getValue().asReadOnlyByteBuffer()));
    }
  }

  public State get(ByteString key) {
    return this.map.getOrDefault(key, isPartial ? Unknown.INSTANCE : Empty.INSTANCE);
  }

  public void set(ByteString key, ByteBuffer value) {
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
