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

  }

  static Slice wrap(byte[] bytes) {

  }

  static Slice copy(byte[] bytes) {

  }

  static Slice wrap(String str) {

  }
}