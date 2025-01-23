package dev.restate.sdk.types;

import java.nio.ByteBuffer;

public interface Slice {

  int readableBytes();

  void copyTo(ByteBuffer target);

  void copyTo(byte[] target);

  void copyTo(byte[] target, int targetOffset);

  byte byteAt(int position);

  ByteBuffer asReadOnlyByteBuffer();

  byte[] toByteArray();

  static Slice wrap(ByteBuffer byteBuffer) {
    // TODO
throw new UnsupportedOperationException();
  }

  static Slice wrap(byte[] bytes) {
    // TODO
    throw new UnsupportedOperationException();
  }

  static Slice copy(byte[] bytes) {
    // TODO
    throw new UnsupportedOperationException();
  }

  static Slice wrap(String str) {
    // TODO
    throw new UnsupportedOperationException();
  }

  Slice EMPTY = Slice.wrap(new byte[0]);
}