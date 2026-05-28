// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.sharedcore;

import dev.restate.common.Slice;
import java.util.HashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Java-side mirror of the shared-core {@code HostBufferRegistry} trait.
 *
 * <p>Stores host-resident payload buffers indexed by a u32 id. Each entry carries its own refcount:
 * {@link #register} starts at 1, {@link #retain} increments, {@link #release} decrements and drops
 * the entry when it hits zero.
 *
 * <p>Buffers are stored as {@link Slice} references — the registry does not copy on register.
 * Callers are responsible for handing in a Slice whose bytes remain content-immutable for as long
 * as any refcount share is outstanding.
 *
 * <p>Not thread-safe. One registry is owned by each {@link SharedCoreInstance} and is only ever
 * touched from the single thread that drives that instance.
 */
final class HostBufferRegistry {

  private static final Logger LOG = LogManager.getLogger(HostBufferRegistry.class);

  // Package-private so tests in the same package can inspect refcount.
  static final class Entry {
    final Slice slice;
    int refcount;

    Entry(Slice slice) {
      this.slice = slice;
      this.refcount = 1;
    }
  }

  // Package-private so tests in the same package can poke at it directly.
  final HashMap<Integer, Entry> entries = new HashMap<>();
  private int nextId = 1;

  int register(Slice slice) {
    int id = nextId++;
    entries.put(id, new Entry(slice));
    LOG.trace("register(len={}) -> id={}", slice.readableBytes(), id);
    return id;
  }

  void retain(int id) {
    Entry e = entries.get(id);
    if (e == null) {
      throw new IllegalStateException("retain on unknown host buffer id " + id);
    }
    e.refcount++;
    LOG.trace("retain(id={}) -> refcount={}", id, e.refcount);
  }

  void release(int id) {
    Entry e = entries.get(id);
    if (e == null) {
      // Tolerate double-release; shared-core's drop semantics guarantee
      // exactly-once but be defensive across the WASM boundary.
      LOG.trace("release(id={}) -> entry already gone", id);
      return;
    }
    int newCount = --e.refcount;
    if (newCount == 0) {
      entries.remove(id);
      LOG.trace("release(id={}) -> refcount=0, dropped", id);
    } else {
      LOG.trace("release(id={}) -> refcount={}", id, newCount);
    }
  }

  /**
   * Return a {@link Slice} view over {@code entries[id][offset..offset+len)}. Java-only — not
   * exposed across the WASM boundary. Shares storage with the registered Slice; no copy.
   *
   * <p>The returned Slice keeps the underlying bytes reachable even after the caller releases the
   * registry entry — the {@link java.nio.ByteBuffer} pins the backing storage.
   */
  Slice slice(int id, int offset, int len) {
    Entry e = entries.get(id);
    if (e == null) {
      throw new IllegalStateException("slice on unknown host buffer id " + id);
    }
    LOG.trace(
        "slice(id={}, offset={}, len={}) over entry of len={}",
        id,
        offset,
        len,
        e.slice.readableBytes());
    return e.slice.slice(offset, len);
  }

  /** Copy {@code len} bytes from {@code entries[id][offset..offset+len]} into {@code dst}. */
  void readInto(int id, int offset, int len, byte[] dst, int dstOffset) {
    Entry e = entries.get(id);
    if (e == null) {
      throw new IllegalStateException("read_into on unknown host buffer id " + id);
    }
    LOG.trace(
        "readInto(id={}, offset={}, len={}) over entry of len={}",
        id,
        offset,
        len,
        e.slice.readableBytes());
    e.slice.slice(offset, len).copyTo(dst, dstOffset);
  }

  boolean eq(int aId, int aOff, int aLen, int bId, int bOff, int bLen) {
    LOG.trace(
        "eq(aId={}, aOff={}, aLen={}, bId={}, bOff={}, bLen={})", aId, aOff, aLen, bId, bOff, bLen);
    if (aLen != bLen) return false;
    Entry a = entries.get(aId);
    Entry b = entries.get(bId);
    if (a == null || b == null) return false;
    return a.slice
        .slice(aOff, aLen)
        .asReadOnlyByteBuffer()
        .equals(b.slice.slice(bOff, bLen).asReadOnlyByteBuffer());
  }
}
