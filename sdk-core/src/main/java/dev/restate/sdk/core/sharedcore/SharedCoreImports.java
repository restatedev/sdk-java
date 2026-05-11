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
import com.dylibso.chicory.runtime.Memory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Host module that satisfies the {@code env} imports required by the Rust WASM crate.
 *
 * <p>The Rust side imports a single {@code log} function from the {@code env} module and routes all
 * {@code tracing} output through it. This class bridges those calls to Log4j.
 */
@HostModule("env")
public final class SharedCoreImports {

  private SharedCoreImports() {}

  private static final Logger LOG = LogManager.getLogger(SharedCoreImports.class);

  public static final SharedCoreImports INSTANCE = new SharedCoreImports();

  @WasmExport
  public void log(Memory memory, int level, int ptr, int len) {
    if (len <= 0) {
      return;
    }
    String message = memory.readString(ptr, len);
    switch (level) {
      case 0 -> LOG.trace(message); // TRACE
      case 1 -> LOG.debug(message); // DEBUG
      case 2 -> LOG.info(message); // INFO
      case 3 -> LOG.warn(message); // WARN
      default -> LOG.error(message); // ERROR (level 4+)
    }
  }

  public HostFunction[] toHostFunctions() {
    return SharedCoreImports_ModuleFactory.toHostFunctions(this);
  }
}
