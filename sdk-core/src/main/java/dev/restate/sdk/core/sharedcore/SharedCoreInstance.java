// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.sharedcore;

import com.dylibso.chicory.annotations.HostModule;
import com.dylibso.chicory.annotations.WasmExport;
import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Memory;
import com.dylibso.chicory.wasm.WasmModule;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import dev.restate.sdk.core.ProtocolException;
import dev.restate.sdk.core.sharedcore.generated.SharedCoreWasmMachine;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Function;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class SharedCoreInstance {

  private static final Logger LOG = LogManager.getLogger(SharedCoreInstance.class);
  private static final CBORMapper CBOR =
      CBORMapper.builder()
          .defaultPropertyInclusion(
              JsonInclude.Value.construct(
                  JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
          .build();
  private static final WasmModule WASM_MODULE =
      dev.restate.sdk.core.sharedcore.generated.SharedCoreWasm.load();
  private static final ThreadLocal<SharedCoreInstance> THREAD_LOCAL =
      ThreadLocal.withInitial(SharedCoreInstance::create);

  private final Memory memory;
  private final SharedCoreWasm_ModuleExports exports;
  private final HostBufferRegistry registry;

  private SharedCoreInstance(
      Memory memory, SharedCoreWasm_ModuleExports exports, HostBufferRegistry registry) {
    this.memory = memory;
    this.exports = exports;
    this.registry = registry;
  }

  static SharedCoreInstance get() {
    return THREAD_LOCAL.get();
  }

  HostBufferRegistry registry() {
    return registry;
  }

  private static SharedCoreInstance create() {
    HostBufferRegistry registry = new HostBufferRegistry();
    ImportValues importValues =
        ImportValues.builder()
            .addFunction(new SharedCoreImports(registry).toHostFunctions())
            .build();

    Instance instance =
        Instance.builder(WASM_MODULE)
            .withMachineFactory(SharedCoreWasmMachine::new)
            .withImportValues(importValues)
            .build();

    Memory mem = instance.memory();
    SharedCoreWasm_ModuleExports exp = new SharedCoreWasm_ModuleExports(instance);

    exp.init(toWasmLevel(LOG.getLevel()));
    return new SharedCoreInstance(mem, exp, registry);
  }

  public SharedCoreWasm_ModuleExports getExports() {
    return exports;
  }

  public byte[] readAndFree(long packed) {
    int ptr = (int) (packed >>> 32);
    int len = (int) (packed & 0xFFFFFFFFL);
    byte[] bytes = memory.readBytes(ptr, len);
    exports.deallocate(ptr, len);
    return bytes;
  }

  public <T> T readCborAndFree(long packed, Class<T> outputClazz) {
    byte[] retBytes = readAndFree(packed);
    try {
      return CBOR.readValue(retBytes, outputClazz);
    } catch (IOException e) {
      throw new ProtocolException("Failed to decode CBOR", ProtocolException.INTERNAL_CODE, e);
    }
  }

  public int write(byte[] bytes) {
    int hPtr = exports.allocate(bytes.length);
    memory.write(hPtr, bytes);
    return hPtr;
  }

  public record BufferPointer(int ptr, int len) {}

  public BufferPointer writeCbor(Object input) {
    byte[] cbor;
    try {
      cbor = CBOR.writeValueAsBytes(input);
    } catch (JsonProcessingException e) {
      throw new ProtocolException("Failed to encode CBOR", ProtocolException.INTERNAL_CODE, e);
    }
    int ptr = write(cbor);
    return new BufferPointer(ptr, cbor.length);
  }

  public <T> T callCborVmFunction(
      TriFunction<SharedCoreWasm_ModuleExports, Integer, Integer, Long> func,
      Object input,
      Class<T> outputClazz) {
    var inputBufferPtr = writeCbor(input);
    long packed = func.apply(exports, inputBufferPtr.ptr, inputBufferPtr.len);
    return readCborAndFree(packed, outputClazz);
  }

  public void callCborVmFunction(
      TriConsumer<SharedCoreWasm_ModuleExports, Integer, Integer> func, Object input) {
    var inputBufferPtr = writeCbor(input);
    func.accept(exports, inputBufferPtr.ptr, inputBufferPtr.len);
  }

  public <T> T callCborVmFunction(
      Function<SharedCoreWasm_ModuleExports, Long> func, Class<T> outputClazz) {
    long packed = func.apply(exports);
    return readCborAndFree(packed, outputClazz);
  }

  @FunctionalInterface
  public interface TriFunction<X, Y, Z, R> {
    R apply(X x, Y y, Z z);
  }

  @FunctionalInterface
  public interface QuadFunction<W, X, Y, Z, R> {
    R apply(W w, X x, Y y, Z z);
  }

  @FunctionalInterface
  public interface TriConsumer<X, Y, Z> {
    void accept(X x, Y y, Z z);
  }

  static Level toLog4jLevel(int level) {
    return switch (level) {
      case 0 -> Level.TRACE;
      case 1 -> Level.DEBUG;
      case 2 -> Level.INFO;
      case 3 -> Level.WARN;
      default -> Level.ERROR;
    };
  }

  static int toWasmLevel(Level level) {
    if (level == Level.TRACE) {
      return 0;
    } else if (level == Level.DEBUG) {
      return 1;
    } else if (level == Level.INFO) {
      return 2;
    } else if (level == Level.WARN) {
      return 3;
    } else {
      return 4;
    }
  }

  /**
   * WASM host imports bound to one {@link SharedCoreInstance}'s {@link HostBufferRegistry}.
   *
   * <p>Single-threaded: this object is touched only from the thread that owns the surrounding
   * {@link SharedCoreInstance}.
   */
  @HostModule("env")
  static final class SharedCoreImports {

    /** Max size we'll keep around in {@link #directScratch}; anything bigger goes one-shot. */
    private static final int DIRECT_SCRATCH_MAX = 64 * 1024;

    private final HostBufferRegistry registry;
    private byte[] directScratch = new byte[0];

    SharedCoreImports(HostBufferRegistry registry) {
      this.registry = registry;
    }

    @WasmExport
    public void log(Memory memory, int level, int ptr, int len) {
      if (len <= 0) {
        return;
      }
      String message = memory.readString(ptr, len);
      LOG.atLevel(toLog4jLevel(level)).log(message);
    }

    @WasmExport
    public void host_buffer_retain(int id) {
      registry.retain(id);
    }

    @WasmExport
    public void host_buffer_release(int id) {
      registry.release(id);
    }

    @WasmExport
    public void host_buffer_read_into(Memory memory, int id, int offset, int len, int dstPtr) {
      if (len <= 0) {
        return;
      }
      ByteBuffer bb = registry.slice(id, offset, len).asReadOnlyByteBuffer();
      if (bb.hasArray()) {
        memory.write(dstPtr, bb.array(), bb.arrayOffset() + bb.position(), bb.remaining());
      } else {
        // Direct buffer: no backing array. Use a cached scratch byte[] to
        // avoid allocating per call; oversize requests go one-shot so a
        // single spike doesn't pin a huge buffer forever.
        byte[] tmp;
        if (len > DIRECT_SCRATCH_MAX) {
          tmp = new byte[len];
        } else {
          if (directScratch.length < len) {
            directScratch = new byte[len];
          }
          tmp = directScratch;
        }
        bb.get(tmp, 0, len);
        memory.write(dstPtr, tmp, 0, len);
      }
    }

    @WasmExport
    public int host_buffer_eq(int aId, int aOff, int aLen, int bId, int bOff, int bLen) {
      return registry.eq(aId, aOff, aLen, bId, bOff, bLen) ? 1 : 0;
    }

    public HostFunction[] toHostFunctions() {
      return SharedCoreImports_ModuleFactory.toHostFunctions(this);
    }
  }
}
