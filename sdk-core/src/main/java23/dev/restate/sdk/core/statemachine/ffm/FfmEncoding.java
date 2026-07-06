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
import dev.restate.sdk.core.UnresolvedFuture;
import dev.restate.sdk.core.statemachine.ffm.generated.CallArguments;
import dev.restate.sdk.core.statemachine.ffm.generated.ForeignSlice;
import dev.restate.sdk.core.statemachine.ffm.generated.SharedCoreNative;
import dev.restate.sdk.core.statemachine.ffm.generated.Slice;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
   * as a zero-copy {@link dev.restate.common.Slice} over the native memory — no copy-out into a
   * heap {@code byte[]}. The native buffer is freed when the returned slice (and any {@link
   * ByteBuffer} views of it) become unreachable (GC-tied via {@link Arena#ofAuto()}): {@code
   * asByteBuffer} keeps the segment's session reachable, so it is safe to read later and from
   * another thread (deserialization on a different dispatcher, or an async socket write). Returns
   * {@link dev.restate.common.Slice#EMPTY} for the null/empty slice.
   */
  static dev.restate.common.Slice wrapOwnedSlice(MemorySegment sliceStruct) {
    MemorySegment ptr = Slice.ptr(sliceStruct);
    long len = Slice.len(sliceStruct);
    if (ptr.address() == 0 || len == 0) {
      return dev.restate.common.Slice.EMPTY;
    }
    MemorySegment seg =
        ptr.reinterpret(len, Arena.ofAuto(), s -> SharedCoreNative.free_buffer(s, len));
    return dev.restate.common.Slice.wrap(seg.asByteBuffer());
  }

  private static final byte[] EMPTY_BYTES = new byte[0];

  // -------------------------------------------------------------------------
  // TargetAbi (passed by value as a MemorySegment)
  // -------------------------------------------------------------------------

  /**
   * Builds a {@code TargetAbi} struct in {@code arena}. String fields are borrowed {@code
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
    setForeign(CallArguments.headers(t), allocateHeaderList(alloc, headers));
    // input is a Slice: ownership of the alloc_buffer-backed payload transfers into the core.
    setOwned(CallArguments.input(t), transferSlice(payload));
    return t;
  }

  // -------------------------------------------------------------------------
  // Blob encoders (little-endian, matching the Rust decode_* functions). Each precomputes its exact
  // byte size, allocates the native segment once, and writes straight into the segment's
  // little-endian ByteBuffer view — no intermediate heap buffer, no copy.
  // -------------------------------------------------------------------------

  /** A little-endian {@link ByteBuffer} view over the whole native segment. */
  private static ByteBuffer leView(MemorySegment seg) {
    return seg.asByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
  }

  /** Wraps an already-written data segment as a by-value ForeignSlice. */
  private static MemorySegment foreignOf(SegmentAllocator alloc, MemorySegment data) {
    MemorySegment fs = ForeignSlice.allocate(alloc);
    setForeign(fs, data);
    return fs;
  }

  /** A header list ({@code u32 count, count*(str,str)}) as a native segment; NULL when empty. */
  private static MemorySegment allocateHeaderList(
      SegmentAllocator alloc, @Nullable Collection<Map.Entry<String, String>> headers) {
    if (headers == null || headers.isEmpty()) {
      return MemorySegment.NULL;
    }
    byte[][] parts = new byte[headers.size() * 2][];
    int size = 4;
    int i = 0;
    for (Map.Entry<String, String> e : headers) {
      byte[] k = e.getKey().getBytes(StandardCharsets.UTF_8);
      byte[] v = e.getValue().getBytes(StandardCharsets.UTF_8);
      parts[i++] = k;
      parts[i++] = v;
      size += 8 + k.length + v.length;
    }
    MemorySegment seg = alloc.allocate(size);
    ByteBuffer bb = leView(seg);
    bb.putInt(headers.size());
    for (byte[] p : parts) {
      bb.putInt(p.length);
      bb.put(p);
    }
    return seg;
  }

  /**
   * A header list from {@code key/value} pairs as a by-value ForeignSlice ({@code u32 count,
   * count*(str,str)}); null ptr when empty.
   */
  static MemorySegment foreignHeaderPairs(
      SegmentAllocator alloc, @Nullable List<String[]> headers) {
    if (headers == null || headers.isEmpty()) {
      return foreignOf(alloc, MemorySegment.NULL);
    }
    byte[][] parts = new byte[headers.size() * 2][];
    int size = 4;
    int i = 0;
    for (String[] e : headers) {
      byte[] k = e[0].getBytes(StandardCharsets.UTF_8);
      byte[] v = e[1].getBytes(StandardCharsets.UTF_8);
      parts[i++] = k;
      parts[i++] = v;
      size += 8 + k.length + v.length;
    }
    MemorySegment seg = alloc.allocate(size);
    ByteBuffer bb = leView(seg);
    bb.putInt(headers.size());
    for (byte[] p : parts) {
      bb.putInt(p.length);
      bb.put(p);
    }
    return foreignOf(alloc, seg);
  }

  /**
   * A {@code TerminalFailure} as a by-value ForeignSlice (see {@code decode_failure}): {@code u16
   * code, str message, u32 meta_count, meta_count*(str,str)}.
   */
  static MemorySegment foreignFailure(SegmentAllocator alloc, TerminalException failure) {
    byte[] message =
        (failure.getMessage() != null ? failure.getMessage() : "").getBytes(StandardCharsets.UTF_8);
    Map<String, String> metadata = failure.getMetadata();
    int size = 2 + 4 + message.length + 4;
    byte[][] meta = null;
    if (metadata != null && !metadata.isEmpty()) {
      meta = new byte[metadata.size() * 2][];
      int i = 0;
      for (Map.Entry<String, String> e : metadata.entrySet()) {
        byte[] k = e.getKey().getBytes(StandardCharsets.UTF_8);
        byte[] v = e.getValue().getBytes(StandardCharsets.UTF_8);
        meta[i++] = k;
        meta[i++] = v;
        size += 8 + k.length + v.length;
      }
    }
    MemorySegment seg = alloc.allocate(size);
    ByteBuffer bb = leView(seg);
    bb.putShort((short) failure.getCode());
    bb.putInt(message.length);
    bb.put(message);
    if (meta == null) {
      bb.putInt(0);
    } else {
      bb.putInt(meta.length / 2);
      for (byte[] p : meta) {
        bb.putInt(p.length);
        bb.put(p);
      }
    }
    return foreignOf(alloc, seg);
  }

  /**
   * The retry policy as a by-value ForeignSlice, or a null ptr when there is no policy (the core's
   * default). Layout (see {@code decode_retry_policy}): {@code u64 initial, f32 factor, opt(u64
   * max_interval), opt(u32 max_attempts), opt(u64 max_duration)} — presence is the null-ness of the
   * slice, so there is no {@code has_policy} flag.
   */
  static MemorySegment foreignRetryPolicy(
      SegmentAllocator alloc, @Nullable RetryPolicy retryPolicy) {
    if (retryPolicy == null) {
      return foreignOf(alloc, MemorySegment.NULL);
    }
    int size = 8 + 4; // initial + factor
    size += 1 + (retryPolicy.getMaxDelay() != null ? 8 : 0);
    size += 1 + (retryPolicy.getMaxAttempts() != null ? 4 : 0);
    size += 1 + (retryPolicy.getMaxDuration() != null ? 8 : 0);
    MemorySegment seg = alloc.allocate(size);
    ByteBuffer bb = leView(seg);
    bb.putLong(retryPolicy.getInitialDelay().toMillis());
    bb.putFloat(retryPolicy.getExponentiationFactor());
    putOptU64(bb, retryPolicy.getMaxDelay() != null ? retryPolicy.getMaxDelay().toMillis() : null);
    putOptU32(bb, retryPolicy.getMaxAttempts());
    putOptU64(
        bb, retryPolicy.getMaxDuration() != null ? retryPolicy.getMaxDuration().toMillis() : null);
    return foreignOf(alloc, seg);
  }

  private static void putOptU32(ByteBuffer bb, @Nullable Integer value) {
    if (value == null) {
      bb.put((byte) 0);
    } else {
      bb.put((byte) 1);
      bb.putInt(value);
    }
  }

  private static void putOptU64(ByteBuffer bb, @Nullable Long value) {
    if (value == null) {
      bb.put((byte) 0);
    } else {
      bb.put((byte) 1);
      bb.putLong(value);
    }
  }

  // -------------------------------------------------------------------------
  // Await future tree (see decode_future). Sized without a buffer: awaitTreeSize measures (pruning
  // resolved nodes), then a single write into the segment's ByteBuffer back-patches each
  // combinator's surviving-child count. Returns null when the whole tree resolved (nothing to
  // await), so the caller can skip the downcall.
  // -------------------------------------------------------------------------

  /**
   * The still-uncompleted await tree as a by-value ForeignSlice, or {@code null} when every node is
   * already resolved (nothing left to await).
   */
  static @Nullable MemorySegment foreignAwaitTree(SegmentAllocator alloc, UnresolvedFuture future) {
    int size = awaitTreeSize(future);
    if (size == 0) {
      return null;
    }
    MemorySegment seg = alloc.allocate(size);
    writeAwaitTree(future, leView(seg));
    return foreignOf(alloc, seg);
  }

  /**
   * Encoded size of the still-uncompleted await tree in bytes, pruning resolved nodes: a leaf is
   * {@code u8 0, u32 handle} = 5B; a combinator is {@code u8 tag, u32 count} + children = 5B +
   * children; a combinator whose children all prune contributes 0. Must match {@link
   * #writeAwaitTree} byte-for-byte.
   */
  private static int awaitTreeSize(UnresolvedFuture node) {
    if (node.isDone()) {
      return 0;
    }
    if (node.kind() == UnresolvedFuture.Kind.SINGLE) {
      return 5; // u8 tag + u32 handle
    }
    int childrenSize = 0;
    int children = 0;
    for (UnresolvedFuture child : node.combinatorChildren()) {
      int cs = awaitTreeSize(child);
      if (cs > 0) {
        childrenSize += cs;
        children++;
      }
    }
    return children == 0 ? 0 : 5 + childrenSize; // u8 tag + u32 count + children
  }

  /**
   * Writes the await tree into {@code bb}: a leaf is {@code u8 0, u32 handle}; a combinator is
   * {@code u8 tag, u32 count, count*node}. Resolved nodes are pruned (a combinator whose children
   * all prune rewinds its reserved header); each combinator's surviving count is back-patched.
   * Writes exactly {@link #awaitTreeSize} bytes.
   */
  private static boolean writeAwaitTree(UnresolvedFuture node, ByteBuffer bb) {
    if (node.isDone()) {
      return false;
    }
    if (node.kind() == UnresolvedFuture.Kind.SINGLE) {
      bb.put((byte) 0);
      bb.putInt(node.singleHandle());
      return true;
    }
    int mark = bb.position();
    bb.put((byte) awaitTag(node.kind()));
    int countPos = bb.position();
    bb.putInt(0);
    int written = 0;
    for (UnresolvedFuture child : node.combinatorChildren()) {
      if (writeAwaitTree(child, bb)) {
        written++;
      }
    }
    if (written == 0) {
      bb.position(mark);
      return false;
    }
    bb.putInt(countPos, written);
    return true;
  }

  private static int awaitTag(UnresolvedFuture.Kind kind) {
    return switch (kind) {
      case SINGLE -> 0;
      case FIRST_COMPLETED -> 1;
      case ALL_COMPLETED -> 2;
      case FIRST_SUCCEEDED_OR_ALL_FAILED -> 3;
      case ALL_SUCCEEDED_OR_FIRST_FAILED -> 4;
      case UNKNOWN -> 5;
    };
  }

  static String readString(ByteBuffer buf) {
    int len = buf.getInt();
    if (len == 0) {
      return "";
    }
    byte[] data = new byte[len];
    buf.get(data);
    return new String(data, StandardCharsets.UTF_8);
  }
}
