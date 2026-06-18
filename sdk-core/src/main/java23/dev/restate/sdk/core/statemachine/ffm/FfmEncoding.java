// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine.ffm;

import dev.restate.common.Target;
import dev.restate.sdk.common.RetryPolicy;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.core.StateMachine;
import dev.restate.sdk.core.statemachine.ffm.generated.CallArguments;
import dev.restate.sdk.core.statemachine.ffm.generated.ForeignSlice;
import dev.restate.sdk.core.statemachine.ffm.generated.SharedCoreNative;
import dev.restate.sdk.core.statemachine.ffm.generated.Slice;
import java.io.ByteArrayOutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Encoding/marshalling helpers between the canonical Java state-machine types and the native C ABI
 * exposed by {@code restate-sdk-shared-core} (see {@code sdk-core/src/main/rust/src/lib.rs}).
 *
 * <p>The "blob" encodings (header list, await future tree, failure, retry policy) are compact
 * little-endian byte sequences decoded by the matching {@code decode_*} functions in the Rust
 * crate. Multi-byte integers are little-endian; strings are {@code u32 len + utf8 bytes};
 * collections are {@code u32 count, count*element}.
 */
final class FfmEncoding {

  private FfmEncoding() {}

  // -------------------------------------------------------------------------
  // Native segment allocation (inputs: copy-for-now into the call scratch)
  // -------------------------------------------------------------------------

  /**
   * Allocate a native segment holding the given bytes, or {@link MemorySegment#NULL} when {@code
   * bytes} is null/empty. The Rust side treats a null ptr / zero len as the empty slice.
   */
  static MemorySegment allocateBytes(SegmentAllocator alloc, byte @Nullable [] bytes) {
    if (bytes == null || bytes.length == 0) {
      return MemorySegment.NULL;
    }
    MemorySegment seg = alloc.allocate(bytes.length);
    MemorySegment.copy(bytes, 0, seg, ValueLayout.JAVA_BYTE, 0, bytes.length);
    return seg;
  }

  /**
   * Allocate a native segment holding the UTF-8 bytes of {@code s}, or {@link MemorySegment#NULL}
   * when {@code s} is null. An empty (non-null) string yields a zero-length, non-null segment.
   */
  static MemorySegment allocateUtf8(SegmentAllocator alloc, @Nullable String s) {
    if (s == null) {
      return MemorySegment.NULL;
    }
    return allocateBytes(alloc, s.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Allocate a Rust-owned native buffer (via {@code alloc_buffer}) and copy {@code slice} into it,
   * transferring ownership to the native callee. The returned segment is tied to no {@link Arena},
   * so Java never frees it: the native side takes ownership on the call and frees it when the core
   * is done with it (see the ownership docs in {@code rust/src/lib.rs}). Used for opaque payloads
   * only. Returns {@link MemorySegment#NULL} for an empty slice.
   */
  static MemorySegment transferSlice(dev.restate.common.Slice slice) {
    int len = slice.readableBytes();
    if (len == 0) {
      return MemorySegment.NULL;
    }
    MemorySegment buf = SharedCoreNative.alloc_buffer(len).reinterpret(len);
    slice.copyTo(buf.asByteBuffer());
    return buf;
  }

  /**
   * Like {@link #transferSlice} but for the UTF-8 bytes of {@code s}: allocates a Rust-owned buffer
   * via {@code alloc_buffer}, copies the encoded bytes in, and transfers ownership to the native
   * callee (which re-owns it as a {@code String} zero-copy, see {@code take_string}). Used for the
   * {@code notify_error} message/stacktrace. Returns {@link MemorySegment#NULL} for a null/empty
   * string (the Rust side maps that to the empty string / absent stacktrace).
   */
  static MemorySegment transferUtf8(@Nullable String s) {
    if (s == null) {
      return MemorySegment.NULL;
    }
    byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
    if (bytes.length == 0) {
      return MemorySegment.NULL;
    }
    MemorySegment buf = SharedCoreNative.alloc_buffer(bytes.length).reinterpret(bytes.length);
    MemorySegment.copy(bytes, 0, buf, ValueLayout.JAVA_BYTE, 0, bytes.length);
    return buf;
  }

  /** Allocates a result {@link Slice} struct out-param (for calls that write a bare Slice). */
  static MemorySegment allocateSliceStruct(SegmentAllocator alloc) {
    return Slice.allocate(alloc);
  }

  // -------------------------------------------------------------------------
  // By-value boundary slices. A ForeignSlice borrows Java-arena memory (Rust reads it in-call and
  // never frees it); a Slice is alloc_buffer-backed memory whose ownership transfers into the
  // native callee. Both cross by value as a {ptr,len} struct; we keep the construction here so the
  // state machine never touches the (name-colliding) generated Slice type directly.
  // -------------------------------------------------------------------------

  /** Writes {@code buf}'s ptr+len into an already-allocated ForeignSlice field segment. */
  private static void setForeign(MemorySegment foreignSliceField, MemorySegment buf) {
    ForeignSlice.ptr(foreignSliceField, buf);
    ForeignSlice.len(foreignSliceField, buf.byteSize());
  }

  /** Writes {@code buf}'s ptr+len into an already-allocated (ownership-transfer) Slice field. */
  private static void setOwned(MemorySegment sliceField, MemorySegment buf) {
    Slice.ptr(sliceField, buf);
    Slice.len(sliceField, buf.byteSize());
  }

  /** A by-value ForeignSlice borrowing a fresh UTF-8 copy of {@code s} (null/empty → null ptr). */
  static MemorySegment foreignUtf8(SegmentAllocator alloc, @Nullable String s) {
    MemorySegment fs = ForeignSlice.allocate(alloc);
    setForeign(fs, allocateUtf8(alloc, s));
    return fs;
  }

  /** A by-value ForeignSlice borrowing a fresh copy of {@code bytes} (null/empty → null ptr). */
  static MemorySegment foreignBytes(SegmentAllocator alloc, byte @Nullable [] bytes) {
    MemorySegment fs = ForeignSlice.allocate(alloc);
    setForeign(fs, allocateBytes(alloc, bytes));
    return fs;
  }

  /**
   * A by-value Slice transferring {@code slice}'s bytes (alloc_buffer buffer the callee re-owns).
   */
  static MemorySegment transferToSlice(SegmentAllocator alloc, dev.restate.common.Slice slice) {
    MemorySegment buf = transferSlice(slice);
    MemorySegment s = Slice.allocate(alloc);
    Slice.ptr(s, buf);
    Slice.len(s, buf.byteSize());
    return s;
  }

  /** A by-value Slice transferring the UTF-8 bytes of {@code s} (callee re-owns it as a String). */
  static MemorySegment transferUtf8ToSlice(SegmentAllocator alloc, @Nullable String s) {
    MemorySegment buf = transferUtf8(s);
    MemorySegment slice = Slice.allocate(alloc);
    Slice.ptr(slice, buf);
    Slice.len(slice, buf.byteSize());
    return slice;
  }

  // -------------------------------------------------------------------------
  // Result Slice reading (outputs: owned by Rust, must be freed)
  // -------------------------------------------------------------------------

  /**
   * Reads an owned result {@link Slice} (a {@code Slice} embedded in a result struct) into a Java
   * byte array, then frees the underlying native buffer via {@code free_buffer}. Returns an empty
   * array when the slice is null/empty.
   */
  static byte[] takeSliceBytes(MemorySegment sliceStruct) {
    MemorySegment ptr = Slice.ptr(sliceStruct);
    long len = Slice.len(sliceStruct);
    if (ptr.address() == 0 || len == 0) {
      return EMPTY_BYTES;
    }
    MemorySegment data = ptr.reinterpret(len);
    byte[] out = new byte[(int) len];
    MemorySegment.copy(data, ValueLayout.JAVA_BYTE, 0, out, 0, (int) len);
    SharedCoreNative.free_buffer(ptr, len);
    return out;
  }

  /** Reads an owned result {@link Slice} into a UTF-8 string, freeing the native buffer. */
  static String takeSliceString(MemorySegment sliceStruct) {
    byte[] bytes = takeSliceBytes(sliceStruct);
    if (bytes.length == 0) {
      return "";
    }
    return new String(bytes, StandardCharsets.UTF_8);
  }

  /**
   * Wraps an owned result {@link Slice} (Rust-allocated; must be released via {@code free_buffer})
   * in a {@link MemorySegmentSlice} read zero-copy — no copy-out into a heap {@code byte[]}. The
   * native buffer is freed when the returned slice becomes unreachable (GC-tied via {@link
   * Arena#ofAuto()}), so it is safe to read later and from another thread (deserialization on a
   * different dispatcher, or an async socket write). Returns {@link dev.restate.common.Slice#EMPTY}
   * for the null/empty slice.
   */
  static dev.restate.common.Slice wrapOwnedSlice(MemorySegment sliceStruct) {
    MemorySegment ptr = Slice.ptr(sliceStruct);
    long len = Slice.len(sliceStruct);
    if (ptr.address() == 0 || len == 0) {
      return dev.restate.common.Slice.EMPTY;
    }
    MemorySegment seg =
        ptr.reinterpret(len, Arena.ofAuto(), s -> SharedCoreNative.free_buffer(s, len));
    return new MemorySegmentSlice(seg);
  }

  private static final byte[] EMPTY_BYTES = new byte[0];

  // -------------------------------------------------------------------------
  // TargetAbi (passed by value as a MemorySegment)
  // -------------------------------------------------------------------------

  /**
   * Builds a {@link TargetAbi} struct in {@code arena}. String fields are borrowed {@code
   * (ptr,len)}; a null ptr means the optional field is absent. {@code headers} is the encoded
   * header-list blob.
   */
  static MemorySegment buildCallArguments(
      SegmentAllocator alloc,
      Target target,
      dev.restate.common.Slice payload,
      @Nullable String idempotencyKey,
      @Nullable String scope,
      @Nullable String limitKey,
      @Nullable Collection<Map.Entry<String, String>> headers) {
    MemorySegment t = CallArguments.allocate(alloc);
    // Target fields are nested ForeignSlices borrowing a fresh copy in `alloc`; a null/empty buffer
    // maps to `None` (V7 optionals) / the empty slice on the Rust side.
    setForeign(CallArguments.service(t), allocateUtf8(alloc, target.getService()));
    setForeign(CallArguments.handler(t), allocateUtf8(alloc, target.getHandler()));
    setForeign(CallArguments.key(t), allocateUtf8(alloc, target.getKey()));
    setForeign(CallArguments.idempotency_key(t), allocateUtf8(alloc, idempotencyKey));
    setForeign(CallArguments.scope(t), allocateUtf8(alloc, scope));
    setForeign(CallArguments.limit_key(t), allocateUtf8(alloc, limitKey));
    setForeign(CallArguments.headers(t), allocateBytes(alloc, encodeHeaderList(headers)));
    // input is a Slice: ownership of the alloc_buffer-backed payload transfers into the core.
    setOwned(CallArguments.input(t), transferSlice(payload));
    return t;
  }

  // -------------------------------------------------------------------------
  // Blob encoders (little-endian, matching the Rust decode_* functions)
  // -------------------------------------------------------------------------

  /** Encodes a header list as {@code u32 count, count*(str key, str value)}. */
  static byte @Nullable [] encodeHeaderList(
      @Nullable Collection<Map.Entry<String, String>> headers) {
    if (headers == null || headers.isEmpty()) {
      return null;
    }
    LeBuffer buf = new LeBuffer();
    buf.putU32(headers.size());
    for (Map.Entry<String, String> e : headers) {
      buf.putStr(e.getKey());
      buf.putStr(e.getValue());
    }
    return buf.toByteArray();
  }

  /** Encodes a header list given as {@code key/value} string pairs. */
  static byte @Nullable [] encodeHeaderPairs(@Nullable List<String[]> headers) {
    if (headers == null || headers.isEmpty()) {
      return null;
    }
    LeBuffer buf = new LeBuffer();
    buf.putU32(headers.size());
    for (String[] e : headers) {
      buf.putStr(e[0]);
      buf.putStr(e[1]);
    }
    return buf.toByteArray();
  }

  /**
   * Encodes the await future tree (see {@code decode_future}): {@code u8 tag}; tag 0 (Single) →
   * {@code u32 handle}; tags 1..=5 → {@code u32 count, count*node}.
   */
  static byte[] encodeFuture(StateMachine.UnresolvedFuture future) {
    LeBuffer buf = new LeBuffer();
    encodeFutureInto(buf, future);
    return buf.toByteArray();
  }

  private static void encodeFutureInto(LeBuffer buf, StateMachine.UnresolvedFuture future) {
    switch (future) {
      case StateMachine.UnresolvedFuture.Single s -> {
        buf.putU8(0);
        buf.putU32(s.handle());
      }
      case StateMachine.UnresolvedFuture.FirstCompleted f -> encodeChildren(buf, 1, f.children());
      case StateMachine.UnresolvedFuture.AllCompleted f -> encodeChildren(buf, 2, f.children());
      case StateMachine.UnresolvedFuture.FirstSucceededOrAllFailed f ->
          encodeChildren(buf, 3, f.children());
      case StateMachine.UnresolvedFuture.AllSucceededOrFirstFailed f ->
          encodeChildren(buf, 4, f.children());
      case StateMachine.UnresolvedFuture.Unknown f -> encodeChildren(buf, 5, f.children());
    }
  }

  private static void encodeChildren(
      LeBuffer buf, int tag, List<StateMachine.UnresolvedFuture> children) {
    buf.putU8(tag);
    buf.putU32(children.size());
    for (StateMachine.UnresolvedFuture child : children) {
      encodeFutureInto(buf, child);
    }
  }

  /**
   * Encodes a {@link TerminalFailure} (see {@code decode_failure}): {@code u16 code, str message,
   * u32 meta_count, meta_count*(str key, str value)}.
   */
  static byte[] encodeFailure(TerminalException failure) {
    LeBuffer buf = new LeBuffer();
    encodeFailureInto(buf, failure);
    return buf.toByteArray();
  }

  private static void encodeFailureInto(LeBuffer buf, TerminalException failure) {
    buf.putU16(failure.getCode());
    buf.putStr(failure.getMessage() != null ? failure.getMessage() : "");
    Map<String, String> metadata = failure.getMetadata();
    if (metadata == null || metadata.isEmpty()) {
      buf.putU32(0);
    } else {
      buf.putU32(metadata.size());
      for (Map.Entry<String, String> e : metadata.entrySet()) {
        buf.putStr(e.getKey());
        buf.putStr(e.getValue());
      }
    }
  }

  /**
   * Encodes the {@code propose_run_completion_retryable_failure} params buffer: {@code u16 code,
   * str message, u8 has_stacktrace, [str stacktrace]}, then the retry-policy blob. The attempt
   * duration is passed as a direct arg.
   */
  static byte[] encodeRetryableRunParams(
      String message, @Nullable String stacktrace, @Nullable RetryPolicy retryPolicy) {
    LeBuffer buf = new LeBuffer();
    buf.putU16(500);
    buf.putStr(message != null ? message : "");
    if (stacktrace != null) {
      buf.putU8(1);
      buf.putStr(stacktrace);
    } else {
      buf.putU8(0);
    }
    encodeRetryPolicyInto(buf, retryPolicy);
    return buf.toByteArray();
  }

  /**
   * Encodes the retry policy (see {@code decode_retry_policy}): {@code u8 has_policy}; if 1: {@code
   * u64 initial, f32 factor, opt(u64 max_interval), opt(u32 max_attempts), opt(u64 max_duration)}.
   */
  private static void encodeRetryPolicyInto(LeBuffer buf, @Nullable RetryPolicy retryPolicy) {
    if (retryPolicy == null) {
      buf.putU8(0);
      return;
    }
    buf.putU8(1);
    buf.putU64(retryPolicy.getInitialDelay().toMillis());
    buf.putF32(retryPolicy.getExponentiationFactor());
    putOptU64(buf, retryPolicy.getMaxDelay() != null ? retryPolicy.getMaxDelay().toMillis() : null);
    putOptU32(buf, retryPolicy.getMaxAttempts());
    putOptU64(
        buf, retryPolicy.getMaxDuration() != null ? retryPolicy.getMaxDuration().toMillis() : null);
  }

  private static void putOptU32(LeBuffer buf, @Nullable Integer value) {
    if (value == null) {
      buf.putU8(0);
    } else {
      buf.putU8(1);
      buf.putU32(value);
    }
  }

  private static void putOptU64(LeBuffer buf, @Nullable Long value) {
    if (value == null) {
      buf.putU8(0);
    } else {
      buf.putU8(1);
      buf.putU64(value);
    }
  }

  // -------------------------------------------------------------------------
  // Little-endian growable byte writer
  // -------------------------------------------------------------------------

  private static final class LeBuffer {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream(64);

    void putU8(int v) {
      out.write(v & 0xFF);
    }

    void putU16(int v) {
      out.write(v & 0xFF);
      out.write((v >>> 8) & 0xFF);
    }

    void putU32(int v) {
      out.write(v & 0xFF);
      out.write((v >>> 8) & 0xFF);
      out.write((v >>> 16) & 0xFF);
      out.write((v >>> 24) & 0xFF);
    }

    void putU64(long v) {
      for (int i = 0; i < 8; i++) {
        out.write((int) ((v >>> (8 * i)) & 0xFF));
      }
    }

    void putF32(float v) {
      putU32(Float.floatToRawIntBits(v));
    }

    void putStr(String s) {
      byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
      putU32(bytes.length);
      out.write(bytes, 0, bytes.length);
    }

    byte[] toByteArray() {
      return out.toByteArray();
    }
  }
}
