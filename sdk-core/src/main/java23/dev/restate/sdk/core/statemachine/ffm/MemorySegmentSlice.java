// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine.ffm;

import dev.restate.common.Slice;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;

/**
 * A {@link Slice} backed directly by a native {@link MemorySegment} owned by the {@code
 * restate-sdk-shared-core} library, read zero-copy (no copy-out into a heap {@code byte[]}).
 *
 * <p>The backing buffer's lifetime is GC-tied: it is wrapped via {@link
 * java.lang.foreign.Arena#ofAuto()} with a cleanup action that calls {@code free_buffer}, so the
 * native memory is released once this slice (and any {@link ByteBuffer} views derived from it)
 * become unreachable. This makes it safe to read the slice later and from another thread — e.g.
 * deserialization happening on a different dispatcher, or an asynchronous socket write that keeps
 * the wrapping buffer alive until flush.
 *
 * <p>Instances are produced by {@link FfmEncoding#wrapOwnedSlice(MemorySegment)}.
 */
final class MemorySegmentSlice implements Slice {

  private final MemorySegment segment;
  private final int length;

  MemorySegmentSlice(MemorySegment segment) {
    this.segment = segment;
    this.length = Math.toIntExact(segment.byteSize());
  }

  @Override
  public int readableBytes() {
    return length;
  }

  @Override
  public ByteBuffer asReadOnlyByteBuffer() {
    return segment.asByteBuffer().asReadOnlyBuffer();
  }

  @Override
  public void copyTo(ByteBuffer target) {
    target.put(segment.asByteBuffer());
  }

  @Override
  public void copyTo(byte[] target) {
    copyTo(target, 0);
  }

  @Override
  public void copyTo(byte[] target, int targetOffset) {
    MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, 0, target, targetOffset, length);
  }

  @Override
  public byte byteAt(int position) {
    return segment.get(ValueLayout.JAVA_BYTE, position);
  }

  @Override
  public byte[] toByteArray() {
    return segment.toArray(ValueLayout.JAVA_BYTE);
  }
}
