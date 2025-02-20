// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.client;

import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public record ClientResponse<R>(int statusCode, Headers headers, R response) {
  public interface Headers {
    @Nullable String get(String key);

    Set<String> keys();

    /**
     * @return headers to lowercase keys map
     */
    Map<String, String> toLowercaseMap();
  }
}
