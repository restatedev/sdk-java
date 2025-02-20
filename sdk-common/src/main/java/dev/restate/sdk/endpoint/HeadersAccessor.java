// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.endpoint;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Abstraction for headers map. */
public interface HeadersAccessor {
  Iterable<String> keys();

  @Nullable String get(String key);

  static HeadersAccessor wrap(Map<String, String> input) {
    return new HeadersAccessor() {
      @Override
      public Iterable<String> keys() {
        return input.keySet();
      }

      @Override
      public String get(String key) {
        for (var k : input.keySet()) {
          if (k.equalsIgnoreCase(key)) {
            return input.get(k);
          }
        }
        return null;
      }
    };
  }
}
