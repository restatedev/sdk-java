// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.sharedcore;

import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Memory;
import com.dylibso.chicory.wasm.WasmModule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import dev.restate.sdk.core.sharedcore.generated.SharedCoreWasmMachine;
import java.io.IOException;
import java.util.function.Function;

class SharedCoreInstance {

  static final CBORMapper CBOR = CBORMapper.builder()
          .build();
  static final int LOG_LEVEL_INFO = 2;

  private static final WasmModule WASM_MODULE;

  static {
    try {
      WASM_MODULE = dev.restate.sdk.core.sharedcore.generated.SharedCoreWasm.load();
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private final Memory memory;
  private final SharedCoreWasm_ModuleExports exports;

  private SharedCoreInstance(Memory memory, SharedCoreWasm_ModuleExports exports) {
    this.memory = memory;
    this.exports = exports;
  }

  public static SharedCoreInstance create() {
    ImportValues importValues =
        ImportValues.builder().addFunction(SharedCoreImports.INSTANCE.toHostFunctions()).build();

    Instance instance =
        Instance.builder(WASM_MODULE)
            .withMachineFactory(SharedCoreWasmMachine::new)
                .withMemoryFactory()
            .withImportValues(importValues)
            .build();

    Memory mem = instance.memory();
    SharedCoreWasm_ModuleExports exp = new SharedCoreWasm_ModuleExports(instance);

    // TODO make this configurable
    exp.init(LOG_LEVEL_INFO);
    return new SharedCoreInstance(mem, exp);
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
      throw new RuntimeException("Failed to decode CBOR", e);
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
      throw new RuntimeException("Failed to encode CBOR", e);
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
}
