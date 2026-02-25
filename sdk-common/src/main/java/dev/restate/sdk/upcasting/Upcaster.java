// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.upcasting;

import dev.restate.common.Slice;
import java.util.Map;

/**
 * A component capable of transforming input payloads from an older representation to a newer one
 * ("upcasting"). Upcasters can be plugged per service via {@code UpcasterFactory} and are invoked
 * by the state machine when handling incoming protocol messages that carry value payloads.
 *
 * <p>Metadata may be provided to upcasters via headers, including the core message name and, where
 * applicable, the state key. See {@link #CORE_MESSAGE_NAME_METADATA_KEY} and {@link
 * #STATE_KEY_METADATA_KEY}.
 *
 * @author Milan Savic
 */
public interface Upcaster {

  /** Header key containing the simple name of the core protocol message being processed. */
  String CORE_MESSAGE_NAME_METADATA_KEY = "_CORE_MESSAGE_NAME";

  /** Header key containing the state key when upcasting state entries (if applicable). */
  String STATE_KEY_METADATA_KEY = "_STATE_KEY";

  /**
   * Returns whether this upcaster is able to upcast the given payload given the provided headers.
   * Implementations should be side‑effect free.
   */
  boolean canUpcast(Slice body, Map<String, String> headers);

  /**
   * Performs the upcast transformation. Implementations may assume {@link #canUpcast(Slice, Map)}
   * has been called and returned {@code true}.
   *
   * @param body the original payload to upcast
   * @param headers metadata associated with the payload
   * @return the transformed payload; must not be {@code null}
   */
  Slice upcast(Slice body, Map<String, String> headers);

  /** Returns a no‑op upcaster that never upcasts and always returns the original payload. */
  static Upcaster noop() {
    return NoopUpcaster.INSTANCE;
  }

  /** No‑op implementation of {@link Upcaster}. */
  final class NoopUpcaster implements Upcaster {

    static final NoopUpcaster INSTANCE = new NoopUpcaster();

    private NoopUpcaster() {}

    @Override
    public boolean canUpcast(Slice body, Map<String, String> headers) {
      return false;
    }

    @Override
    public Slice upcast(Slice body, Map<String, String> headers) {
      return body;
    }
  }
}
