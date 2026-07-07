// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.sdk.core.legacy.LegacyStateMachine;
import dev.restate.sdk.core.legacy.ServiceProtocol;
import dev.restate.sdk.endpoint.HeadersAccessor;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Creates the {@link StateMachine} for the current runtime: the Panama/FFM implementation (calling
 * the native shared-core library) on JDK 23+ when that library is available, otherwise the legacy
 * pure-Java implementation — a deprecated fallback that will be removed in a future release.
 *
 * <p>Set {@code -Ddev.restate.sdk.statemachine.disableNewCore=true} to force the pure-Java
 * implementation.
 */
@FunctionalInterface
interface StateMachineFactory {

  StateMachine create(
      HeadersAccessor headersAccessor,
      EndpointRequestHandler.LoggingContextSetter loggingContextSetter);

  static StateMachineFactory get() {
    return Loader.FACTORY;
  }

  static long maxSupportedProtocolVersion() {
    return Loader.FFM_FACTORY != null
        ? Loader.FFM_MAX_PROTOCOL_VERSION
        : ServiceProtocol.MAX_SERVICE_PROTOCOL_VERSION.getNumber();
  }

  final class Loader {

    private static final Logger LOG = LogManager.getLogger(StateMachineFactory.Loader.class);

    static final StateMachineFactory LEGACY_FACTORY = LegacyStateMachine::new;
    static final @Nullable StateMachineFactory FFM_FACTORY = resolveFfmFactory();

    static final StateMachineFactory FACTORY =
        (headersAccessor, loggingContextSetter) ->
            FFM_FACTORY != null
                ? FFM_FACTORY.create(headersAccessor, loggingContextSetter)
                : LEGACY_FACTORY.create(headersAccessor, loggingContextSetter);

    /** First JDK whose stable FFM API matches the jextract-generated bindings. */
    private static final int FFM_MIN_JAVA_FEATURE = 23;

    /** Max service-protocol version supported by the native shared-core (FFM) implementation. */
    private static final long FFM_MAX_PROTOCOL_VERSION = 7L;

    /**
     * Resolves the FFM state-machine constructor reflectively, or returns {@code null} to fall back
     * to the pure-Java implementation. The decision is made once: a runtime fallback never switches
     * implementations mid-fleet. Only linkage/availability failures trigger the fallback — a
     * protocol error from constructing a VM is propagated.
     */
    private static @Nullable StateMachineFactory resolveFfmFactory() {
      if (Boolean.getBoolean("dev.restate.sdk.statemachine.disableNewCore")) {
        LOG.warn(
            "The native Restate state machine is explicitly disabled; using the Java-only state"
                + " machine, which does not support the latest Restate features.");
        return null;
      }
      if (Runtime.version().feature() < FFM_MIN_JAVA_FEATURE) {
        LOG.warn(
            "Using the Java-only Restate state machine. This does not support the latest Restate"
                + " features; upgrade to Java "
                + FFM_MIN_JAVA_FEATURE
                + "+ to enable them.");
        return null;
      }
      try {
        // Load the native library first; a linkage failure here means this platform isn't
        // supported.
        Class.forName("dev.restate.sdk.core.statemachine.ffm.NativeLibraryLoader")
            .getMethod("ensureLoaded")
            .invoke(null);
        Constructor<?> ctor =
            Class.forName("dev.restate.sdk.core.statemachine.ffm.FfmStateMachine")
                .getConstructor(HeadersAccessor.class);
        return (headersAccessor, ignored) -> {
          try {
            return (StateMachine) ctor.newInstance(headersAccessor);
          } catch (InvocationTargetException e) {
            // Unwrap so VM/protocol errors thrown by the constructor propagate unchanged.
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof Error er) throw er;
            throw new RuntimeException(cause);
          } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
          }
        };
      } catch (Throwable t) {
        LOG.warn(
            "Native shared-core library unavailable on this platform ({} {}); using the Java-only"
                + " state machine, which does not support the latest Restate features. If you expected"
                + " native support on this platform, please contact the Restate developers for more"
                + " info.",
            System.getProperty("os.name"),
            System.getProperty("os.arch"),
            t);
        return null;
      }
    }
  }
}
