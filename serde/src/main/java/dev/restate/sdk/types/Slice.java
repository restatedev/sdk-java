package dev.restate.sdk.types;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public interface Slice {

  int readableBytes();

  void copyTo(ByteBuffer target);

  void copyTo(byte[] target);

  void copyTo(byte[] target, int targetOffset);

  byte byteAt(int position);

  ByteBuffer asReadOnlyByteBuffer();

  byte[] toByteArray();

  static Slice wrap(ByteBuffer byteBuffer) {
    return new Slice() {
      @Override
      public ByteBuffer asReadOnlyByteBuffer() {
        return byteBuffer.slice();
      }

      @Override
      public int readableBytes() {
        return byteBuffer.remaining();
      }

      @Override
      public void copyTo(byte[] target) {
        copyTo(target, 0);
      }

      @Override
      public void copyTo(byte[] target, int targetOffset) {
        byteBuffer.slice().get(target, targetOffset, target.length);
      }

      @Override
      public byte byteAt(int position) {
        return byteBuffer.slice().get(position);
      }

      @Override
      public void copyTo(ByteBuffer buffer) {
        buffer.put(byteBuffer.slice());
      }

      @Override
      public byte[] toByteArray() {
        if (byteBuffer.hasArray()) {
        return byteBuffer.array();
        }

        byte[] dst = new byte[byteBuffer.remaining()];
        byteBuffer.slice().get(dst);
        return dst;
      }
    };
  }

  static Slice wrap(byte[] bytes) {
    return wrap(ByteBuffer.wrap(bytes));
  }

  static Slice wrap(String str) {
    return wrap(str.getBytes(StandardCharsets.UTF_8));
  }

  Slice EMPTY = Slice.wrap(new byte[0]);
}