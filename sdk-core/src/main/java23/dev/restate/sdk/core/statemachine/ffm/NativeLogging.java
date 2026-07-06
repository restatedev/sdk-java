// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine.ffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.spi.StandardLevel;

/**
 * Bridges the native {@code restate-sdk-shared-core} tracing output into Log4j2.
 *
 * <p>At startup the FFM state machine installs a {@code tracing} subscriber in the native core (see
 * {@code init} in {@code rust/src/lib.rs}) parameterized with:
 *
 * <ul>
 *   <li>a <b>max level</b> ({@link #abiLevel()}) derived from this class's Log4j2 logger, so the
 *       core filters disabled events on its side — critically, its {@code #[instrument(level =
 *       "trace")]} spans short-circuit before doing any work when trace is off;
 *   <li>a <b>log callback</b> ({@link #callbackStub()}): an FFM upcall stub the core invokes for
 *       every event that passes the filter.
 * </ul>
 *
 * <p>The callback is invoked synchronously on the same thread that made the downcall into the core,
 * so a native log line naturally inherits that thread's logging context (the {@code
 * restateInvocationId} / {@code restateInvocationTarget} MDC set by the endpoint handler) — a
 * Log4j2 pattern using {@code %X{restateInvocationId}} works unchanged.
 *
 * <p><b>Copy semantics.</b> The core hands the callback a borrowed {@code (ptr, len)} view over a
 * buffer that is valid only for the duration of the call. We therefore eagerly copy the bytes into
 * a JVM-heap {@code String} <b>during the call</b>: an async Log4j2 appender that retains the
 * message then holds a self-contained Java object, never a view over native memory that has since
 * been reused or freed.
 */
final class NativeLogging {

  private NativeLogging() {}

  /** Dedicated logger all native (shared-core) events are routed through. */
  static final String LOGGER_NAME = "dev.restate.sdk.core.StateMachine";

  private static final Logger LOG = LogManager.getLogger(LOGGER_NAME);

  /**
   * Optional override for the native max level, bypassing the Log4j2-derived value. Accepts {@code
   * TRACE|DEBUG|INFO|WARN|ERROR}.
   */
  private static final String LEVEL_OVERRIDE_PROPERTY =
      "dev.restate.sdk.core.statemachine.ffm.FfmStateMachine.loglevel";

  // ABI ordinal -> Log4j2 level. Mirrors AbiLogLevel in rust/src/lib.rs.
  private static final Level[] LEVELS = {
    Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR
  };

  private static final FunctionDescriptor CALLBACK_DESC =
      FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);

  /**
   * Upcall entry point invoked by the native core for each tracing event. Must never throw across
   * the FFI boundary, so everything is wrapped in a catch-all.
   *
   * @param level the {@link #LEVELS} ordinal
   * @param msgPtr native pointer to UTF-8 bytes, valid only for this call
   * @param msgLen length in bytes of the message at {@code msgPtr}
   */
  static void log(int level, MemorySegment msgPtr, long msgLen) {
    try {
      Level l = level >= 0 && level < LEVELS.length ? LEVELS[level] : Level.INFO;
      // The native side already filtered by max level; this guards against per-logger config that
      // is stricter than the max, and avoids the decode when the event would be dropped anyway.
      if (!LOG.isEnabled(l)) {
        return;
      }
      // Eagerly copy the native bytes into a JVM-heap String *now*, before returning. This is the
      // single copy that makes the whole scheme safe: the native buffer is valid only for this
      // call, so an async Log4j2 appender that retains the message must be handed a self-contained
      // Java object, never a view over native memory.
      byte[] bytes = msgPtr.reinterpret(msgLen).toArray(ValueLayout.JAVA_BYTE);
      LOG.log(l, new String(bytes, StandardCharsets.UTF_8));
    } catch (Throwable ignored) {
      // Swallow: an exception unwinding into the native caller across `extern "C"` is UB.
    }
  }

  /**
   * Builds the upcall stub for {@link #log}. Uses {@link Arena#global()} (never freed): the native
   * core keeps only the raw function pointer, which the GC cannot see, so the stub must outlive the
   * process — an {@link Arena#ofAuto()} stub would be reclaimed as soon as the returned segment
   * becomes unreachable (right after {@code init} returns), and the next event would call freed
   * code.
   */
  static MemorySegment callbackStub() {
    try {
      MethodHandle handle =
          MethodHandles.lookup()
              .findStatic(
                  NativeLogging.class,
                  "log",
                  MethodType.methodType(void.class, int.class, MemorySegment.class, long.class));
      return Linker.nativeLinker().upcallStub(handle, CALLBACK_DESC, Arena.global());
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  /**
   * The max native tracing level as an {@code AbiLogLevel} ordinal (0 trace .. 4 error), taken from
   * the {@link #LEVEL_OVERRIDE_PROPERTY} system property when set, otherwise from the effective
   * Log4j2 level of {@link #LOGGER_NAME}. Passed to native {@code init} so the core does not even
   * produce events below it.
   */
  static int abiLevel() {
    String override = System.getProperty(LEVEL_OVERRIDE_PROPERTY);
    if (override != null) {
      return switch (override.trim().toUpperCase()) {
        case "TRACE" -> 0;
        case "DEBUG" -> 1;
        case "WARN" -> 3;
        case "ERROR" -> 4;
        default -> 2;
      };
    }
    return abiLevel(LOG.getLevel());
  }

  private static int abiLevel(Level level) {
    if (level == null) {
      return 2; // INFO
    }
    // Log4j2 intLevel decreases with severity (TRACE=600 .. ERROR=200); a logger set to level L
    // permits L and everything more severe. Map that to the coarser native max level.
    int i = level.intLevel();
    if (i >= StandardLevel.TRACE.intLevel()) {
      return 0;
    } else if (i >= StandardLevel.DEBUG.intLevel()) {
      return 1;
    } else if (i >= StandardLevel.INFO.intLevel()) {
      return 2;
    } else if (i >= StandardLevel.WARN.intLevel()) {
      return 3;
    }
    return 4;
  }
}
