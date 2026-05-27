// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.common;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Wrapper type for byte slices. */
public interface Slice {

  int readableBytes();

  void copyTo(ByteBuffer target);

  void copyTo(byte[] target);

  void copyTo(byte[] target, int targetOffset);

  byte byteAt(int position);

  ByteBuffer asReadOnlyByteBuffer();

  byte[] toByteArray();

  /**
   * Returns a Slice over the sub-range {@code [offset, offset + length)} of this Slice. Shares
   * storage with this Slice — no copy.
   */
  default Slice slice(int offset, int length) {
    ByteBuffer view = asReadOnlyByteBuffer();
    view.position(offset).limit(offset + length);
    return Slice.wrap(view.slice());
  }

  /** Wrap a {@link ByteBuffer}. This will not copy the buffer. */
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
        ByteBuffer view = byteBuffer.slice();
        view.get(target, targetOffset, view.remaining());
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
      public Slice slice(int offset, int length) {
        return wrap(byteBuffer.slice(offset, length));
      }

      @Override
      public byte[] toByteArray() {
        // Only the no-offset / no-limit case can return the backing array
        // directly — otherwise array() would expose bytes outside the view.
        if (byteBuffer.hasArray()
            && byteBuffer.arrayOffset() == 0
            && byteBuffer.position() == 0
            && byteBuffer.remaining() == byteBuffer.array().length) {
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
